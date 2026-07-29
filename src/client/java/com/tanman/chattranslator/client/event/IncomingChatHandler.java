package com.tanman.chattranslator.client.event;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.LanguageDetector;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.Translator;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.PlayerChatMessage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-translates incoming (server-sent) chat into English.
 *
 * <h2>Two events, because Fabric splits system chat from player chat</h2>
 *
 * <p>{@code ClientReceiveMessageEvents.MODIFY_GAME} only ever delivers <em>system</em>
 * messages (server broadcasts, {@code /say}, most plugin/proxy chat) — real
 * player-to-player chat on a vanilla server goes through a separate event,
 * {@code ClientReceiveMessageEvents.CHAT}, which has no {@code MODIFY} variant (it is
 * observer-only: {@code void}, no return value to alter). Both are hooked here so real
 * player chat is translated too, not just system messages. The {@code CHAT} path is
 * simpler in one respect: {@link PlayerChatMessage#signedBody()}{@code .content()}
 * gives the clean, undecorated message body directly, so there is no need for the
 * sender-prefix guesswork the {@code MODIFY_GAME} path relies on.
 *
 * <h2>Nothing runs on the client thread</h2>
 *
 * <p>Fabric's {@code ClientReceiveMessageEvents.MODIFY_GAME} callback runs on the
 * client (render) thread while the incoming-chat packet is being handled. Every
 * expensive step of this pipeline is therefore pushed onto a background worker:
 * MarianMT ONNX inference and model downloads obviously, but also language
 * <em>detection</em> — Lingua is built from all ~75 languages and loads those models
 * lazily on its first call, which is seconds of I/O and hundreds of megabytes, and
 * even warm it is real work to run against every chat line.
 *
 * <p>This is possible because the callback's return value never depends on any of
 * that: the handler always returns the message unchanged and appends the translation
 * as a separate chat line later. The only piece that hops back to the client thread
 * is the {@link TranslationState} write, preserving the invariant that state is
 * only ever mutated from the client thread.
 *
 * <h2>Why the translation is appended rather than replacing the original</h2>
 *
 * <p>No replace-in-place mechanism exists in 26.1.2 for either event. The chat HUD
 * ({@link ChatComponent}) keeps its {@code allMessages}/{@code trimmedMessages} lists
 * private with no accessor, and its only removal API is
 * {@code deleteMessage(MessageSignature)} — keyed on the cryptographic signature of a
 * signed message. System messages (the {@code MODIFY_GAME} path) carry no signature at
 * all. Player messages (the {@code CHAT} path) do carry one via
 * {@link PlayerChatMessage#signature()}, but {@code deleteMessage} is paired with a
 * {@code DELETED_CHAT_MESSAGE} marker/placeholder mechanism rather than a clean removal,
 * which is a worse user experience than simply appending — so both paths append. The
 * cost is a minor UX compromise: the translation appears below the original.
 *
 * <p>The appended line is added via {@link ChatComponent#addClientSystemMessage},
 * which writes straight to the HUD rather than going through the packet-handling
 * path that {@code MODIFY_GAME} is mixed into, so appended translations cannot
 * re-enter this handler.
 */
public final class IncomingChatHandler {

    /** Incoming chat is always translated into the local player's language. */
    private static final String TARGET_LANGUAGE = "en";

    /**
     * Best-effort match for a leading sender-name prefix on a rendered chat line.
     *
     * <p>{@code MODIFY_GAME} hands over the fully rendered line (e.g.
     * {@code "<Steve> bonjour"}), not the raw message body — the clean
     * {@code PlayerChatMessage.signedBody().content()} is only reachable through the
     * {@code CHAT} event, which has no modify variant. Feeding the sender's name into
     * the detector skews detection and pads the minimum-length check, and feeding it
     * into the translator garbles output, so it is stripped first.
     *
     * <p>This is inherently approximate: it covers the vanilla {@code <name>} form and
     * the common {@code [name]} plugin form, but will not catch servers with custom
     * chat formats (e.g. {@code Name » message} or coloured rank prefixes). When it
     * does not match, the line is used as-is — the same behaviour as before, never
     * worse.
     */
    private static final Pattern SENDER_PREFIX =
            Pattern.compile("^\\s*(<[^<>]{1,32}>|\\[[^\\[\\]]{1,32}])\\s*");

    /**
     * How long to wait for a model download before giving up on this attempt.
     *
     * <p>{@link ModelDownloader} sets no request or connect timeout, so without a
     * bound here a black-holed socket would park this handler's single worker thread
     * forever — silently killing detection, translation and auto-target tracking for
     * the rest of the session. Generous enough for a ~100 MB model on an ordinary
     * connection.
     */
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 60;

    /**
     * Consecutive translation failures tolerated for a pair before it is given up on.
     *
     * <p>More than one, because a single failure may be specific to one odd input
     * rather than to the model; a genuinely corrupt model fails every time and trips
     * the limit almost immediately.
     */
    private static final int MAX_TRANSLATION_FAILURES = 3;

    /**
     * Background worker for detection, downloads and inference.
     *
     * <p>Single-threaded so a burst of chat cannot spawn unbounded concurrent ONNX
     * sessions, and daemon so it never keeps the JVM alive at shutdown.
     */
    private static final Executor WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-translator-incoming");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Language pairs whose model download has already been attempted and failed.
     *
     * <p>Detection runs over all ~75 Lingua languages against noisy real-world chat,
     * so it will sometimes name a language that has no {@code Xenova/opus-mt-X-en}
     * repository at all. Without this guard every subsequent qualifying message would
     * re-attempt the download — several HTTP requests per message, all 404 — turning
     * one chatty speaker into a sustained request loop against Hugging Face.
     *
     * <p>Keyed by {@link ModelManager#pairKey}. Deliberately kept here rather than in
     * {@link ModelManager}: this is about not repeating this handler's own wasted
     * dispatch, not about {@code ModelManager}'s on-disk cache-completeness contract.
     * It is intentionally process-lifetime only, so restarting the game retries a pair
     * that may have failed for transient reasons (offline, rate limit).
     */
    private static final Set<String> FAILED_PAIRS = ConcurrentHashMap.newKeySet();

    /**
     * Consecutive {@link Translator#translate} failures per pair key.
     *
     * <p>A model directory can satisfy {@code isCached} (all four files present) and
     * still be unusable — a truncated or corrupt ONNX graph, say. {@code Translator}
     * deliberately does not cache load failures, so without this guard every
     * qualifying message would trigger another full ~100 MB load attempt on the
     * worker. Reaching {@link #MAX_TRANSLATION_FAILURES} moves the pair into
     * {@link #FAILED_PAIRS}; any success resets the count.
     */
    private static final Map<String, Integer> TRANSLATION_FAILURES = new ConcurrentHashMap<>();

    /**
     * Downloads currently in flight, keyed by pair key.
     *
     * <p>Giving up on {@link #DOWNLOAD_TIMEOUT_SECONDS} abandons the <em>wait</em>, not
     * the request — the {@code HttpClient} exchange carries on in the background. And a
     * timeout deliberately does not blacklist the pair, so the next qualifying message
     * tries again. Without this map that retry would start a <em>second</em> download of
     * the same files, and {@link ModelDownloader} writes every attempt to the same fixed
     * {@code <dest>.part} path — so two overlapping attempts can interleave their writes
     * and both {@code Files.move} into the destination, leaving a truncated {@code .onnx}
     * that {@link ModelManager#isCached} (existence-only) then treats as a valid cache
     * entry across restarts. On a black-holed socket the leaked attempts would also pile
     * up without bound, one per message.
     *
     * <p>So retries join the existing future instead of starting a new request. Entries
     * are removed once resolved, so a genuinely new attempt is never blocked by a stale
     * one. Must be concurrent: the removal callback runs on an HTTP client thread, not
     * on {@link #WORKER}.
     */
    private static final Map<String, CompletableFuture<Boolean>> IN_FLIGHT_DOWNLOADS =
            new ConcurrentHashMap<>();

    private IncomingChatHandler() {
    }

    public static void register(
            TranslationState state,
            LanguageDetector detector,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            // Overlay messages are the action bar, not chat — there is no chat line
            // to append a translation under, so leave them alone entirely.
            if (overlay) {
                return message;
            }

            ChatLine line = splitSenderPrefix(message.getString());

            // Everything from detection onward is off-thread; see the class javadoc.
            WORKER.execute(() -> process(line, state, detector, modelManager, downloader, translator));

            // Always show the original immediately; the translation follows as its
            // own line when the background work completes.
            return message;
        });

        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, chatType, receptionTimestamp) -> {
                    // This is an observer event (void, no return value) fired directly
                    // from the packet-handling mixin — an uncaught exception here would
                    // propagate into that dispatch, not just fail this translation, so
                    // extraction is guarded the same way process() is.
                    try {
                        // Unlike MODIFY_GAME, the raw body is already available with no
                        // sender-name guesswork required.
                        String body = signedMessage.signedBody().content();
                        String prefix = sender != null && sender.name() != null
                                ? "<" + sender.name() + "> "
                                : "";
                        ChatLine line = new ChatLine(prefix, body);

                        WORKER.execute(() -> process(line, state, detector, modelManager, downloader, translator));
                    } catch (Exception error) {
                        ChatTranslator.LOGGER.warn("Failed to queue incoming chat message for translation", error);
                    }
                });
    }

    /** Runs entirely on {@link #WORKER}. Must never throw into the executor. */
    private static void process(
            ChatLine line,
            TranslationState state,
            LanguageDetector detector,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        try {
            String body = line.body();

            Optional<String> detected = detector.detect(body);
            if (detected.isEmpty()) {
                // English, too short, or not confident enough: nothing to do, and
                // no state mutation.
                return;
            }
            String sourceLanguage = detected.get();

            // TranslationState is only ever written from the client thread.
            runOnClientThread(() -> state.onLanguageDetected(sourceLanguage));

            String pairKey = modelManager.pairKey(sourceLanguage, TARGET_LANGUAGE);
            if (FAILED_PAIRS.contains(pairKey)) {
                return;
            }

            Path modelDir = modelManager.modelDir(sourceLanguage, TARGET_LANGUAGE);
            if (!modelManager.isCached(sourceLanguage, TARGET_LANGUAGE)
                    && !awaitDownload(sourceLanguage, modelDir, pairKey, downloader)) {
                return;
            }

            TranslationResult result = translator.translate(body, modelDir);
            if (!result.success()) {
                noteTranslationFailure(pairKey, sourceLanguage);
                return;
            }
            TRANSLATION_FAILURES.remove(pairKey);

            if (result.translatedText().isBlank()
                    || result.translatedText().equals(result.originalText())) {
                // Nothing useful to add — don't duplicate the line.
                return;
            }

            appendToChat(buildTranslatedComponent(
                    line.prefix(), result.translatedText(), result.originalText()));
        } catch (Exception error) {
            // Nothing here may escape: a failure to translate must never disturb
            // chat handling or kill the worker thread.
            ChatTranslator.LOGGER.warn("Failed to translate incoming message", error);
        }
    }

    /**
     * Waits, with a bound, for a model download to finish — starting one only if no
     * attempt for this pair is already running.
     *
     * @return {@code true} if the model is now on disk
     */
    private static boolean awaitDownload(
            String sourceLanguage,
            Path modelDir,
            String pairKey,
            ModelDownloader downloader
    ) throws Exception {
        try {
            Boolean downloaded = inFlightDownload(sourceLanguage, modelDir, pairKey, downloader)
                    .get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(downloaded)) {
                return true;
            }
            // A definitive failure: offline, or no such model pair on Hugging Face.
            // Stop retrying it, or every later message repeats the 404s.
            FAILED_PAIRS.add(pairKey);
            ChatTranslator.LOGGER.warn(
                    "No model available for {}->{}; leaving these messages untranslated",
                    sourceLanguage, TARGET_LANGUAGE);
        } catch (TimeoutException timeout) {
            // Deliberately NOT added to FAILED_PAIRS: a timeout is transient (slow
            // link, stalled socket), not proof the model is missing, so the next
            // qualifying message should try again. The download itself keeps running
            // on the downloader's own executor and may still populate the cache.
            ChatTranslator.LOGGER.warn(
                    "Timed out after {}s waiting for the {}->{} model; will retry",
                    DOWNLOAD_TIMEOUT_SECONDS, sourceLanguage, TARGET_LANGUAGE);
        }
        return false;
    }

    /**
     * Returns the in-flight download for this pair, starting one if there is none.
     *
     * <p>See {@link #IN_FLIGHT_DOWNLOADS} for why sharing the future matters. The
     * cleanup callback is attached only by the caller that actually started the
     * download, and uses the two-argument {@code remove} so it can never evict a newer
     * attempt that replaced this one.
     */
    private static CompletableFuture<Boolean> inFlightDownload(
            String sourceLanguage,
            Path modelDir,
            String pairKey,
            ModelDownloader downloader
    ) {
        boolean[] started = {false};
        CompletableFuture<Boolean> download = IN_FLIGHT_DOWNLOADS.computeIfAbsent(pairKey, key -> {
            started[0] = true;
            return downloader.download(sourceLanguage, TARGET_LANGUAGE, modelDir);
        });
        if (started[0]) {
            // Attached outside the mapping function: the future may already be complete,
            // and re-entering the map from inside computeIfAbsent is not allowed.
            download.whenComplete((ok, error) -> IN_FLIGHT_DOWNLOADS.remove(pairKey, download));
        }
        return download;
    }

    private static void noteTranslationFailure(String pairKey, String sourceLanguage) {
        int failures = TRANSLATION_FAILURES.merge(pairKey, 1, Integer::sum);
        if (failures >= MAX_TRANSLATION_FAILURES) {
            FAILED_PAIRS.add(pairKey);
            ChatTranslator.LOGGER.warn(
                    "Giving up on the {}->{} model after {} consecutive translation failures;"
                            + " the cached model may be corrupt",
                    sourceLanguage, TARGET_LANGUAGE, failures);
        }
    }

    /** A chat line split into its sender prefix (possibly empty) and message body. */
    private record ChatLine(String prefix, String body) {
    }

    /**
     * Splits off a leading {@code <name>} / {@code [name]} sender prefix, if present.
     *
     * <p>See {@link #SENDER_PREFIX} for why this is necessary and why it is only
     * best-effort. The prefix is kept rather than discarded so it can be re-attached
     * to the translated line — since the translation is appended as its own message,
     * dropping it would leave a bare sentence with no indication of who said it.
     * Falls back to treating the whole string as the body if stripping would leave
     * nothing behind.
     */
    private static ChatLine splitSenderPrefix(String rendered) {
        Matcher matcher = SENDER_PREFIX.matcher(rendered);
        if (matcher.find()) {
            String body = rendered.substring(matcher.end());
            if (!body.isBlank()) {
                return new ChatLine(matcher.group(1) + " ", body);
            }
        }
        return new ChatLine("", rendered);
    }

    /**
     * Builds the translated chat line, with the untranslated original as its hover
     * tooltip.
     *
     * <p>In 26.1.2 {@code HoverEvent} is an interface and {@code HoverEvent.ShowText}
     * is a record implementing it (verified against the resolved client jar) — there
     * is no {@code new HoverEvent(Action.SHOW_TEXT, ...)} constructor as in older
     * versions.
     */
    private static Component buildTranslatedComponent(
            String senderPrefix, String translated, String original) {
        HoverEvent hover = new HoverEvent.ShowText(Component.literal(original));
        return Component.literal(senderPrefix + translated)
                .withStyle(style -> style.withHoverEvent(hover));
    }

    private static void appendToChat(Component component) {
        // The chat HUD is not thread-safe.
        runOnClientThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui != null) {
                minecraft.gui.getChat().addClientSystemMessage(component);
            }
        });
    }

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(action);
        }
    }
}
