package com.tanman.chattranslator.client.event;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.OutgoingTranslationDecision;
import com.tanman.chattranslator.client.translation.OutgoingTranslationDecision.Outcome;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.Translator;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Translates the player's outgoing chat from English into the current target
 * language before the packet leaves the client.
 *
 * <h2>Why this one is allowed to block</h2>
 *
 * <p>Unlike the incoming path, the outgoing decision <em>is</em> the return value:
 * {@code ClientSendMessageEvents.MODIFY_CHAT} hands over the typed string and takes
 * back the string that will actually be sent, so there is no way to finish the work
 * later — whatever this method returns is what other players see. The design spec
 * therefore accepts a short blocking wait here. "Short" is load-bearing: this callback
 * runs on the client (render) thread, so every second spent waiting is a second of
 * frozen game. Hence a single {@link #BUDGET_MILLIS} deadline for the whole message,
 * shared between the download wait and inference rather than granted to each, so the
 * worst case is that budget and not the sum of two.
 *
 * <p>A consequence worth knowing: any local system message queued before a blocking
 * wait cannot actually be painted until the wait ends, because the thread that would
 * render it is the thread that is waiting. The "downloading…" notice is still queued
 * up-front (it is the honest ordering, and it renders on the very next frame), but it
 * should not be relied on as a progress indicator <em>during</em> a slow download.
 *
 * <h2>The decision itself is not made here</h2>
 *
 * <p>All branching over the outcome lives in {@link OutgoingTranslationDecision}, which
 * is pure and unit-tested. This class only performs IO — cache check, download,
 * inference — and feeds the results into a single
 * {@link OutgoingTranslationDecision#decide} call, then acts on the {@link Outcome}.
 * The guards around the IO steps ("don't download when the target is English") are not
 * a second copy of that decision: they only avoid pointless work whose result
 * {@code decide} would ignore anyway.
 */
public final class OutgoingChatHandler {

    /** Outgoing chat is always translated from the local player's language. */
    private static final String SOURCE_LANGUAGE = "en";

    /**
     * Total time one outgoing message may freeze the client thread.
     *
     * <p>This is a single budget shared by <em>both</em> blocking steps, not a per-step
     * allowance: whatever the download wait consumes is taken out of what inference may
     * then use. Two independent timeouts would stack, and a first-time language switch
     * would freeze for their sum — long enough that a player reasonably concludes the
     * game has hung. The design spec asks for "a short blocking wait with a reasonable
     * timeout", singular, and this is it.
     *
     * <p>A ~100 MB download will not finish inside this, and that is fine: the message
     * goes out in English, the download continues in the background, and the next
     * message the player types is usually translated.
     *
     * <p>It is also the only bound on the download at all — {@link ModelDownloader} sets
     * no connect or request timeout of its own, so without this a black-holed socket
     * would hang the game permanently.
     */
    private static final long BUDGET_MILLIS = 6_000;

    /**
     * Least time worth handing to inference.
     *
     * <p>If the download wait has eaten all but a sliver of {@link #BUDGET_MILLIS},
     * starting an inference that cannot possibly finish only adds another stall and
     * occupies the worker (see {@link #INFERENCE_RUNNING}) for the messages after this
     * one. Below this threshold the message goes out untranslated immediately.
     */
    private static final long MIN_INFERENCE_MILLIS = 500;

    /**
     * Worker used to give {@link Translator#translate} a timeout it does not have.
     *
     * <p>{@code translate} is a plain blocking call, so the only way to stop waiting on
     * it is to run it somewhere else and abandon the wait. Abandoning is safe:
     * {@code Translator} is internally thread-safe, and a late result is simply
     * discarded. Single-threaded so a burst of messages cannot stack up concurrent ONNX
     * sessions, and daemon so an abandoned inference never holds the JVM open at exit.
     */
    private static final ExecutorService INFERENCE_WORKER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "chat-translator-outgoing");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Whether {@link #INFERENCE_WORKER} is currently occupied.
     *
     * <p>Abandoning a wait does not abandon the task: an overrunning cold model load
     * keeps the single worker thread to itself. Without this flag the next message's
     * inference would queue up behind it — already past its own deadline before it even
     * started — and time out for certain, as would the one after that. One slow load
     * would turn into a self-sustaining backlog of doomed work for as long as the player
     * kept typing.
     *
     * <p>So a message that finds the worker busy skips inference entirely and goes out
     * untranslated. One slow load degrades one message, not the rest of the session.
     */
    private static final AtomicBoolean INFERENCE_RUNNING = new AtomicBoolean();

    /**
     * When a failed pair may next be retried, keyed by {@link ModelManager#pairKey}.
     *
     * <p>{@link ModelDownloader#download} answers only {@code Boolean}: a confirmed 404
     * for a pair that was never published and a {@code ConnectException} from a player
     * whose wifi dropped for five seconds are both plain {@code false}. Treating that as
     * proof the model does not exist would let one network blip disable a language for
     * the rest of the session, with a "couldn't translate" notice on every message sent
     * afterwards and no way back short of restarting the game.
     *
     * <p>So failure is temporary rather than permanent, and backs off: the first retry
     * comes {@link #BASE_RETRY_MILLIS} later, which costs a genuinely offline player
     * almost nothing and recovers a transient failure quickly, while a pair that really
     * has no {@code opus-mt-en-X} repository doubles its way up to
     * {@link #MAX_RETRY_MILLIS} within a few attempts and stops mattering. While a pair
     * is in backoff it is reported to {@code decide} as {@code reverseModelKnownMissing},
     * so those messages skip the network and the freeze entirely.
     *
     * <p>Distinguishing the two cases properly needs a failure reason from
     * {@code ModelDownloader}, which is deliberately out of scope here.
     */
    private static final Map<String, Backoff> DOWNLOAD_BACKOFF = new ConcurrentHashMap<>();

    /** Delay before a pair's first retry; doubles per consecutive failure. */
    private static final long BASE_RETRY_MILLIS = 30_000;

    /** Ceiling for the doubling in {@link #DOWNLOAD_BACKOFF}. */
    private static final long MAX_RETRY_MILLIS = 15 * 60_000;

    /**
     * Consecutive download failures for a pair, and the {@link System#nanoTime} after
     * which it may be attempted again.
     */
    private record Backoff(int failures, long retryAtNanos) {
    }

    /**
     * Downloads currently in flight, keyed by {@link ModelManager#pairKey}.
     *
     * <p>Timing out abandons the wait, not the request — the HTTP exchange keeps
     * running. Without this map, the next message the player types would start a
     * <em>second</em> download of the same pair, and {@link ModelDownloader} writes every
     * attempt to the same fixed {@code <dest>.part} path: two overlapping attempts can
     * interleave their writes and both {@code Files.move} the result into place, leaving
     * a truncated {@code .onnx} that {@link ModelManager#isCached} (existence-only) then
     * trusts forever. Someone typing three messages in a row while a large model
     * downloads is a completely ordinary way to hit that.
     *
     * <p>So retries join the existing future. Entries are removed once resolved, so a
     * genuinely new attempt is never blocked by a stale one. Must be concurrent: the
     * cleanup callback runs on an HTTP client thread, not the client thread.
     *
     * <p>This mirrors the guard {@link IncomingChatHandler} keeps for its own direction.
     * The two cannot collide with each other ({@code en->X} and {@code X->en} are
     * different pair keys and different directories), but the duplication is a symptom:
     * the durable fix is a unique temp path plus real timeouts inside
     * {@code ModelDownloader}, which would let both handlers drop their copy.
     */
    private static final Map<String, CompletableFuture<Boolean>> IN_FLIGHT_DOWNLOADS =
            new ConcurrentHashMap<>();

    private OutgoingChatHandler() {
    }

    public static void register(
            TranslationState state,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> {
            try {
                return translateOutgoing(message, state, modelManager, downloader, translator);
            } catch (Exception error) {
                // Chat must keep working no matter what: on any unexpected failure the
                // player's original text is sent rather than nothing at all.
                ChatTranslator.LOGGER.warn("Failed to translate outgoing message", error);
                return message;
            }
        });
    }

    private static String translateOutgoing(
            String message,
            TranslationState state,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        if (message.isBlank()) {
            // Nothing to translate, and nothing worth reporting a failure about.
            return message;
        }

        // Empty in auto mode until a non-English message has been seen, which means
        // there is no one to translate for yet — treat that as English, i.e. send as-is.
        String targetLanguage = state.getCurrentTargetLanguage().orElse(SOURCE_LANGUAGE);

        String pairKey = modelManager.pairKey(SOURCE_LANGUAGE, targetLanguage);
        boolean reverseModelKnownMissing = inBackoff(pairKey);
        // Left false until it is actually looked up: for an English target `decide`
        // ignores it, and checking would mean four pointless stat calls on the client
        // thread for every message the player sends.
        boolean cached = false;
        TranslationResult inferenceResult = null;

        boolean worthTranslating =
                !SOURCE_LANGUAGE.equals(targetLanguage) && !reverseModelKnownMissing;

        if (worthTranslating) {
            // One deadline covering both blocking steps; see BUDGET_MILLIS.
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BUDGET_MILLIS);
            cached = modelManager.isCached(SOURCE_LANGUAGE, targetLanguage);
            if (!cached) {
                sendLocalSystemMessage(
                        "Downloading the " + targetLanguage + " translation model…");
                cached = awaitDownload(
                        targetLanguage,
                        pairKey,
                        modelManager.modelDir(SOURCE_LANGUAGE, targetLanguage),
                        downloader,
                        deadlineNanos);
                // The download attempt may have put the pair into backoff.
                reverseModelKnownMissing = inBackoff(pairKey);
            }
            if (cached && !reverseModelKnownMissing) {
                inferenceResult = translate(
                        translator,
                        message,
                        modelManager.modelDir(SOURCE_LANGUAGE, targetLanguage),
                        deadlineNanos);
            }
        }

        Outcome outcome = OutgoingTranslationDecision.decide(
                targetLanguage, cached, reverseModelKnownMissing, inferenceResult);

        return switch (outcome) {
            case OutgoingTranslationDecision.SendTranslated translated ->
                    translated.translatedText();
            case OutgoingTranslationDecision.SendUnmodified ignored -> message;
            case OutgoingTranslationDecision.AwaitingDownload awaiting -> {
                sendLocalSystemMessage("The " + awaiting.targetLanguage()
                        + " model isn't ready yet — sending this message in English.");
                yield message;
            }
            case OutgoingTranslationDecision.NoReverseModel missing -> {
                sendLocalSystemMessage("Couldn't translate into " + missing.targetLanguage()
                        + " — sending this message in English.");
                yield message;
            }
        };
    }

    /**
     * Runs one translation within whatever is left of the message's budget.
     *
     * <p>Skipped outright if the worker is still busy with an earlier overrunning
     * inference, or if too little of the budget survives the download wait to be worth
     * spending — see {@link #INFERENCE_RUNNING} and {@link #MIN_INFERENCE_MILLIS}.
     *
     * @return the result, or {@code null} if it was skipped, did not finish in time, or
     *         threw — {@code decide} treats a missing result as a failure to translate
     */
    private static TranslationResult translate(
            Translator translator, String message, Path modelDir, long deadlineNanos) {
        long budgetMillis = remainingMillis(deadlineNanos);
        if (budgetMillis < MIN_INFERENCE_MILLIS) {
            ChatTranslator.LOGGER.info(
                    "No time left in this message's budget to translate it; sending as-is");
            return null;
        }
        if (!INFERENCE_RUNNING.compareAndSet(false, true)) {
            ChatTranslator.LOGGER.info(
                    "An earlier translation is still running; sending this message as-is");
            return null;
        }

        CompletableFuture<TranslationResult> inference;
        try {
            inference = CompletableFuture.supplyAsync(() -> {
                try {
                    return translator.translate(message, modelDir);
                } finally {
                    INFERENCE_RUNNING.set(false);
                }
            }, INFERENCE_WORKER);
        } catch (RuntimeException rejected) {
            // Never reached in practice (unbounded queue), but the flag must not be left
            // stuck true if the task never runs.
            INFERENCE_RUNNING.set(false);
            throw rejected;
        }

        try {
            return inference.get(budgetMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            // The task keeps running and keeps the worker; INFERENCE_RUNNING makes the
            // next message skip inference rather than queue behind it.
            ChatTranslator.LOGGER.warn(
                    "Translation did not finish within {}ms; sending the message untranslated",
                    budgetMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Outgoing translation failed", error);
        }
        return null;
    }

    /**
     * Waits for the {@code en->target} model until the message's deadline — starting a
     * download only if no attempt for this pair is already running.
     *
     * @return {@code true} if the model is now on disk
     */
    private static boolean awaitDownload(
            String targetLanguage,
            String pairKey,
            Path modelDir,
            ModelDownloader downloader,
            long deadlineNanos
    ) {
        try {
            if (Boolean.TRUE.equals(inFlightDownload(targetLanguage, pairKey, modelDir, downloader)
                    .get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS))) {
                DOWNLOAD_BACKOFF.remove(pairKey);
                return true;
            }
            // An answer, but not necessarily a permanent one — this is equally a missing
            // repository and a five-second network drop. Back off instead of blacklisting.
            noteDownloadFailure(pairKey, targetLanguage);
        } catch (TimeoutException timeout) {
            // Not a failure at all: the download is merely slower than one message's
            // budget. It keeps running on the downloader's own executor and will usually
            // have landed by the next message, so no backoff is recorded.
            ChatTranslator.LOGGER.info(
                    "Still downloading the {}->{} model; sending this message untranslated",
                    SOURCE_LANGUAGE, targetLanguage);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Failed to download the {}->{} model",
                    SOURCE_LANGUAGE, targetLanguage, error);
            noteDownloadFailure(pairKey, targetLanguage);
        }
        return false;
    }

    /** Milliseconds left before {@code deadlineNanos}, never negative. */
    private static long remainingMillis(long deadlineNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    /** Whether this pair's last download failed recently enough not to retry yet. */
    private static boolean inBackoff(String pairKey) {
        Backoff backoff = DOWNLOAD_BACKOFF.get(pairKey);
        if (backoff == null) {
            return false;
        }
        if (System.nanoTime() - backoff.retryAtNanos() >= 0) {
            // Expired. Left in the map with its failure count intact, so a pair that
            // keeps failing keeps backing off further rather than restarting at 30s.
            return false;
        }
        return true;
    }

    /** Records a failed download and schedules the next attempt, backing off each time. */
    private static void noteDownloadFailure(String pairKey, String targetLanguage) {
        Backoff backoff = DOWNLOAD_BACKOFF.compute(pairKey, (key, previous) -> {
            int failures = previous == null ? 1 : previous.failures() + 1;
            // Doubling, shift capped so it cannot overflow on a long-running session.
            long delay = Math.min(
                    MAX_RETRY_MILLIS, BASE_RETRY_MILLIS << Math.min(failures - 1, 16));
            return new Backoff(failures, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay));
        });
        ChatTranslator.LOGGER.warn(
                "Could not fetch the {}->{} model (attempt {}); messages stay in English"
                        + " until it is retried",
                SOURCE_LANGUAGE, targetLanguage, backoff.failures());
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
            String targetLanguage,
            String pairKey,
            Path modelDir,
            ModelDownloader downloader
    ) {
        boolean[] started = {false};
        CompletableFuture<Boolean> download = IN_FLIGHT_DOWNLOADS.computeIfAbsent(pairKey, key -> {
            started[0] = true;
            return downloader.download(SOURCE_LANGUAGE, targetLanguage, modelDir);
        });
        if (started[0]) {
            // Attached outside the mapping function: the future may already be complete,
            // and re-entering the map from inside computeIfAbsent is not allowed.
            download.whenComplete((ok, error) -> IN_FLIGHT_DOWNLOADS.remove(pairKey, download));
        }
        return download;
    }

    /**
     * Shows a message in the local chat HUD only — it is never sent to the server.
     *
     * <p>{@code ChatComponent#addClientSystemMessage} writes straight to the HUD,
     * bypassing the packet-handling path entirely, so nobody else sees these notices.
     */
    private static void sendLocalSystemMessage(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        // Runs inline when already on the client thread (the normal case here), and
        // hops threads correctly if that ever stops being true.
        minecraft.execute(() -> {
            if (minecraft.gui != null) {
                minecraft.gui.getChat().addClientSystemMessage(
                        Component.literal("[Chat Translator] " + text)
                                .withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
