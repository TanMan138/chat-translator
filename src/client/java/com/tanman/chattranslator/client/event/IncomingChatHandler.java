package com.tanman.chattranslator.client.event;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.LanguageDetector;
import com.tanman.chattranslator.client.translation.ProtectedSpans;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.backend.TranslationService;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Incoming chat stays as-is until the player opens chat and <strong>hovers</strong>
 * (or clicks) a line. That keeps the model cache from bloating on every lobby message.
 *
 * <p>Tooltips are resolved live via {@link #liveTooltip} because Minecraft bakes
 * {@link net.minecraft.network.chat.Style} into chat line buffers at add-time.
 */
public final class IncomingChatHandler {

    public static final Identifier CLICK_ID =
            Identifier.fromNamespaceAndPath(ChatTranslator.MOD_ID, "translate");

    private static final String TARGET_LANGUAGE = "en";

    private static final String HINT = "Hover to translate → English (Chat Translator)";

    private static final Pattern SENDER_PREFIX =
            Pattern.compile("^\\s*(<[^<>]{1,32}>|\\[[^\\[\\]]{1,32}])\\s*");

    private static final long ON_DEVICE_TIMEOUT_SECONDS = 90;
    private static final long REMOTE_TIMEOUT_SECONDS = 30;

    private static final int MAX_TRANSLATION_FAILURES = 3;

    /** Cap pending click targets so a long session cannot grow without bound. */
    private static final int MAX_PENDING = 200;

    private static final Executor WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-translator-incoming");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<String> FAILED_PAIRS = ConcurrentHashMap.newKeySet();

    private static final Map<String, Integer> TRANSLATION_FAILURES = new ConcurrentHashMap<>();

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private static volatile TranslationState state;
    private static volatile LanguageDetector detector;
    private static volatile TranslationService translationService;

    private enum Status {
        READY,
        RUNNING,
        DONE
    }

    private record Pending(
            MutableComponent display,
            String body,
            AtomicReference<Status> status,
            AtomicReference<String> tooltip
    ) {
    }

    private IncomingChatHandler() {
    }

    public static void register(
            TranslationState translationState,
            LanguageDetector languageDetector,
            TranslationService service
    ) {
        state = translationState;
        detector = languageDetector;
        translationService = service;

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay) {
                return message;
            }
            return prepare(message.copy(), splitSenderPrefix(message.getString()).body());
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register(
                (message, signedMessage, sender, chatType, receptionTimestamp) -> {
                    try {
                        MutableComponent display = message.copy();
                        String body = signedMessage.signedBody().content();
                        prepare(display, body);

                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.gui != null) {
                            MessageSignature signature = signedMessage.signature();
                            minecraft.gui.getChat().addPlayerMessage(display, signature, null);
                        }
                        return false;
                    } catch (Exception error) {
                        ChatTranslator.LOGGER.warn(
                                "Failed to prepare incoming chat message", error);
                        return true;
                    }
                });
    }

    /**
     * Live tooltip text for our translate click-ids. Used by {@code StyleMixin}
     * because baked chat styles never see later {@code setStyle} updates.
     */
    public static Optional<String> liveTooltip(ClickEvent event) {
        String id = payloadId(event);
        if (id == null) {
            return Optional.empty();
        }
        Pending pending = PENDING.get(id);
        if (pending == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.tooltip().get());
    }

    /**
     * Hover or click entry point from {@code ChatScreen} mixin.
     *
     * @return {@code true} if this was our click id (handled even if already running/done)
     */
    public static boolean requestTranslate(ClickEvent event) {
        String id = payloadId(event);
        if (id == null) {
            return event instanceof ClickEvent.Custom custom && CLICK_ID.equals(custom.id());
        }
        Pending pending = PENDING.get(id);
        if (pending == null) {
            return true;
        }
        if (!pending.status().compareAndSet(Status.READY, Status.RUNNING)) {
            return true;
        }
        pending.tooltip().set("Translating…");
        WORKER.execute(() -> process(pending));
        return true;
    }

    private static String payloadId(ClickEvent event) {
        if (!(event instanceof ClickEvent.Custom custom) || !CLICK_ID.equals(custom.id())) {
            return null;
        }
        Optional<Tag> payload = custom.payload();
        if (payload.isEmpty() || !(payload.get() instanceof StringTag stringTag)) {
            return null;
        }
        return stringTag.value();
    }

    private static MutableComponent prepare(MutableComponent display, String body) {
        if (body == null || body.isBlank()) {
            return display;
        }

        String id = UUID.randomUUID().toString();
        trimPendingIfNeeded();
        Pending pending = new Pending(
                display,
                body,
                new AtomicReference<>(Status.READY),
                new AtomicReference<>(HINT));
        PENDING.put(id, pending);

        display.setStyle(display.getStyle()
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(HINT)))
                .withClickEvent(new ClickEvent.Custom(CLICK_ID, Optional.of(StringTag.valueOf(id)))));
        return display;
    }

    private static void trimPendingIfNeeded() {
        if (PENDING.size() < MAX_PENDING) {
            return;
        }
        int toRemove = PENDING.size() - MAX_PENDING + 50;
        var iterator = PENDING.keySet().iterator();
        while (toRemove > 0 && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            toRemove--;
        }
    }

    /** Runs entirely on {@link #WORKER}. Must never throw into the executor. */
    private static void process(Pending pending) {
        String body = pending.body();
        try {
            ProtectedSpans.Masked masked = ProtectedSpans.mask(body);
            Optional<String> detected = detector.detect(ProtectedSpans.unwrap(body));
            if (detected.isEmpty()) {
                finish(pending, "Couldn't detect a language (English or too short).");
                return;
            }
            String sourceLanguage = detected.get();

            runOnClientThread(() -> state.onLanguageDetected(sourceLanguage));

            if (translationService.requiresModelDownload()) {
                String pairKey = pairKey(sourceLanguage, TARGET_LANGUAGE);
                if (FAILED_PAIRS.contains(pairKey)) {
                    finish(pending, "No translation model for " + sourceLanguage + ".");
                    return;
                }
            }

            pending.tooltip().set("Running translation…");

            long timeoutSeconds = translationService.isRemoteBackend()
                    ? REMOTE_TIMEOUT_SECONDS
                    : ON_DEVICE_TIMEOUT_SECONDS;

            TranslationResult result = translationService
                    .translate(masked.text(), sourceLanguage, TARGET_LANGUAGE)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            if (!result.success()) {
                if (translationService.requiresModelDownload()) {
                    noteTranslationFailure(pairKey(sourceLanguage, TARGET_LANGUAGE), sourceLanguage);
                }
                finish(pending, "Translation failed.");
                return;
            }
            if (translationService.requiresModelDownload()) {
                TRANSLATION_FAILURES.remove(pairKey(sourceLanguage, TARGET_LANGUAGE));
            }

            String english = masked.restore(result.translatedText());
            if (english.isBlank()
                    || english.equals(ProtectedSpans.unwrap(result.originalText()))) {
                finish(pending, "Nothing to translate.");
                return;
            }

            finish(pending, english);
        } catch (TimeoutException timeout) {
            finish(pending, "Translation timed out.");
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Failed to translate incoming message", error);
            finish(pending, "Translation error — see log.");
        }
    }

    private static String pairKey(String source, String target) {
        return source + "-" + target;
    }

    private static void finish(Pending pending, String tooltip) {
        pending.tooltip().set(tooltip);
        pending.status().set(Status.DONE);
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

    private record ChatLine(String prefix, String body) {
    }

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

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(action);
        }
    }
}
