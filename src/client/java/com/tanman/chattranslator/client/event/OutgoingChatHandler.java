package com.tanman.chattranslator.client.event;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.LocalNotices;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.OutgoingTranslationDecision;
import com.tanman.chattranslator.client.translation.OutgoingTranslationDecision.Outcome;
import com.tanman.chattranslator.client.translation.ProtectedSpans;
import com.tanman.chattranslator.client.translation.TextRomanizer;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.Translator;
import com.tanman.chattranslator.client.translation.backend.TranslationService;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

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
 */
public final class OutgoingChatHandler {

    private static final String SOURCE_LANGUAGE = "en";

    private static final long WARM_INFERENCE_MILLIS = 750;
    private static final long REMOTE_INFERENCE_MILLIS = 4_000;

    private static final ExecutorService INFERENCE_WORKER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "chat-translator-outgoing");
                thread.setDaemon(true);
                return thread;
            });

    private static final AtomicBoolean INFERENCE_RUNNING = new AtomicBoolean();

    private static final Map<String, Backoff> DOWNLOAD_BACKOFF = new ConcurrentHashMap<>();
    private static final long BASE_RETRY_MILLIS = 30_000;
    private static final long MAX_RETRY_MILLIS = 15 * 60_000;

    private static final Map<String, CompletableFuture<Boolean>> IN_FLIGHT_DOWNLOADS =
            new ConcurrentHashMap<>();

    private static volatile TranslationService translationService;
    private static volatile TranslatorConfig config;

    private record Backoff(int failures, long retryAtNanos) {
    }

    private OutgoingChatHandler() {
    }

    public static void register(
            TranslationState state,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator,
            TranslationService service,
            TranslatorConfig translatorConfig
    ) {
        translationService = service;
        config = translatorConfig;

        ClientSendMessageEvents.MODIFY_CHAT.register(message -> {
            try {
                if (config.isRemoteBackend()) {
                    return translateOutgoingRemote(message, state);
                }
                return translateOutgoingOnDevice(message, state, modelManager, downloader, translator);
            } catch (Exception error) {
                ChatTranslator.LOGGER.warn("Failed to translate outgoing message", error);
                return ProtectedSpans.unwrap(message);
            }
        });
    }

    private static String translateOutgoingRemote(String message, TranslationState state) {
        if (message.isBlank()) {
            return message;
        }

        ProtectedSpans.Masked masked = ProtectedSpans.mask(message);
        String targetLanguage = state.getCurrentTargetLanguage().orElse(SOURCE_LANGUAGE);
        if (SOURCE_LANGUAGE.equals(targetLanguage)) {
            return ProtectedSpans.unwrap(message);
        }

        LocalNotices.show("Translating outgoing message…");

        CompletableFuture<TranslationResult> inference = translationService.translate(
                masked.text(), SOURCE_LANGUAGE, targetLanguage);

        TranslationResult result;
        try {
            result = inference.get(REMOTE_INFERENCE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            ChatTranslator.LOGGER.warn(
                    "Remote outgoing translation did not finish within {}ms; sending untranslated",
                    REMOTE_INFERENCE_MILLIS);
            return ProtectedSpans.unwrap(message);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ProtectedSpans.unwrap(message);
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Remote outgoing translation failed", error);
            return ProtectedSpans.unwrap(message);
        }

        if (!result.success()) {
            return ProtectedSpans.unwrap(message);
        }

        return finalizeOutgoing(masked, result.translatedText(), state);
    }

    private static String translateOutgoingOnDevice(
            String message,
            TranslationState state,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        if (message.isBlank()) {
            return message;
        }

        ProtectedSpans.Masked masked = ProtectedSpans.mask(message);
        String targetLanguage = state.getCurrentTargetLanguage().orElse(SOURCE_LANGUAGE);

        String pairKey = modelManager.pairKey(SOURCE_LANGUAGE, targetLanguage);
        boolean reverseModelKnownMissing = inBackoff(pairKey);
        boolean cached = false;
        TranslationResult inferenceResult = null;

        boolean worthTranslating =
                !SOURCE_LANGUAGE.equals(targetLanguage) && !reverseModelKnownMissing;

        if (worthTranslating) {
            Path modelDir = modelManager.modelDir(SOURCE_LANGUAGE, targetLanguage);
            cached = modelManager.isCached(SOURCE_LANGUAGE, targetLanguage);
            if (!cached) {
                boolean started = startDownload(
                        targetLanguage, pairKey, modelDir, downloader, translator);
                if (started) {
                    LocalNotices.show(
                            "Downloading the en->" + targetLanguage
                                    + " translation model… (sending in English until ready)");
                } else {
                    LocalNotices.show("The en->" + targetLanguage
                            + " model isn't ready yet — sending this message in English.");
                }
                return ProtectedSpans.unwrap(message);
            }

            if (!translator.isLoaded(modelDir)) {
                warmInBackground(translator, modelDir, targetLanguage);
                LocalNotices.show("Loading en->" + targetLanguage
                        + " into memory… sending this message in English.");
                return ProtectedSpans.unwrap(message);
            }

            inferenceResult = translateWarm(translator, masked.text(), modelDir);
        }

        Outcome outcome = OutgoingTranslationDecision.decide(
                targetLanguage, cached, reverseModelKnownMissing, inferenceResult);

        return switch (outcome) {
            case OutgoingTranslationDecision.SendTranslated translated ->
                    finalizeOutgoing(masked, translated.translatedText(), state);
            case OutgoingTranslationDecision.SendUnmodified ignored ->
                    ProtectedSpans.unwrap(message);
            case OutgoingTranslationDecision.AwaitingDownload awaiting -> {
                LocalNotices.show("The en->" + awaiting.targetLanguage()
                        + " model isn't ready yet — sending this message in English.");
                yield ProtectedSpans.unwrap(message);
            }
            case OutgoingTranslationDecision.NoReverseModel missing -> {
                if (cached && inferenceResult == null) {
                    yield ProtectedSpans.unwrap(message);
                }
                LocalNotices.show("Couldn't translate into " + missing.targetLanguage()
                        + " — sending this message in English.");
                yield ProtectedSpans.unwrap(message);
            }
        };
    }

    private static String finalizeOutgoing(
            ProtectedSpans.Masked masked,
            String translatedText,
            TranslationState state
    ) {
        String text = masked.restore(translatedText);
        if (state.isLatinOutgoing() && containsNonAscii(text)) {
            String latin = TextRomanizer.toLatinAscii(text);
            LocalNotices.show("Sending romanized: \"" + latin + "\" (was \"" + text
                    + "\") — /translate native for real script.");
            return latin;
        }
        if (containsNonAscii(text)) {
            LocalNotices.show("Sending: \"" + text
                    + "\" — if rejected, /translate latin (AntiSpam-safe).");
        }
        return text;
    }

    private static boolean containsNonAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static TranslationResult translateWarm(
            Translator translator, String message, Path modelDir) {
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
            INFERENCE_RUNNING.set(false);
            throw rejected;
        }

        try {
            return inference.get(WARM_INFERENCE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            ChatTranslator.LOGGER.warn(
                    "Warm translation did not finish within {}ms; sending untranslated",
                    WARM_INFERENCE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Outgoing translation failed", error);
        }
        return null;
    }

    private static void warmInBackground(
            Translator translator, Path modelDir, String targetLanguage) {
        if (!INFERENCE_RUNNING.compareAndSet(false, true)) {
            return;
        }
        INFERENCE_WORKER.execute(() -> {
            try {
                translator.preload(modelDir);
                if (translator.isLoaded(modelDir)) {
                    LocalNotices.show("en->" + targetLanguage + " loaded — next messages translate.");
                }
            } finally {
                INFERENCE_RUNNING.set(false);
            }
        });
    }

    private static boolean startDownload(
            String targetLanguage,
            String pairKey,
            Path modelDir,
            ModelDownloader downloader,
            Translator translator
    ) {
        boolean[] started = {false};
        CompletableFuture<Boolean> download = IN_FLIGHT_DOWNLOADS.computeIfAbsent(pairKey, key -> {
            started[0] = true;
            return downloader.download(SOURCE_LANGUAGE, targetLanguage, modelDir);
        });
        if (started[0]) {
            download.whenComplete((ok, error) -> {
                IN_FLIGHT_DOWNLOADS.remove(pairKey, download);
                if (Boolean.TRUE.equals(ok)) {
                    DOWNLOAD_BACKOFF.remove(pairKey);
                    LocalNotices.show("en->" + targetLanguage + " model ready — loading…");
                    warmInBackground(translator, modelDir, targetLanguage);
                } else if (error == null) {
                    noteDownloadFailure(pairKey, targetLanguage);
                    LocalNotices.show("Couldn't download the en->" + targetLanguage + " model.");
                }
            });
        }
        return started[0];
    }

    private static boolean inBackoff(String pairKey) {
        Backoff backoff = DOWNLOAD_BACKOFF.get(pairKey);
        if (backoff == null) {
            return false;
        }
        return System.nanoTime() - backoff.retryAtNanos() < 0;
    }

    private static void noteDownloadFailure(String pairKey, String targetLanguage) {
        Backoff backoff = DOWNLOAD_BACKOFF.compute(pairKey, (key, previous) -> {
            int failures = previous == null ? 1 : previous.failures() + 1;
            long delay = Math.min(
                    MAX_RETRY_MILLIS, BASE_RETRY_MILLIS << Math.min(failures - 1, 16));
            return new Backoff(failures, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay));
        });
        ChatTranslator.LOGGER.warn(
                "Could not fetch the {}->{} model (attempt {}); messages stay in English"
                        + " until it is retried",
                SOURCE_LANGUAGE, targetLanguage, backoff.failures());
    }
}
