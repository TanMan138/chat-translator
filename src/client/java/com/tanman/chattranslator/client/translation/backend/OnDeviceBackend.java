package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.LocalNotices;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.Translator;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps the existing ONNX {@link Translator} plus Hugging Face model download path.
 */
public final class OnDeviceBackend implements TranslationBackend {

    private static final long DOWNLOAD_TIMEOUT_SECONDS = 60;

    private static final Set<String> FAILED_PAIRS = ConcurrentHashMap.newKeySet();
    private static final Map<String, CompletableFuture<Boolean>> IN_FLIGHT_DOWNLOADS =
            new ConcurrentHashMap<>();

    private final ModelManager modelManager;
    private final ModelDownloader downloader;
    private final Translator translator;

    public OnDeviceBackend(
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        this.modelManager = modelManager;
        this.downloader = downloader;
        this.translator = translator;
    }

    @Override
    public boolean requiresModelDownload() {
        return true;
    }

    @Override
    public CompletableFuture<TranslationResult> translate(
            String text,
            String sourceLang,
            String targetLang
    ) {
        return CompletableFuture.supplyAsync(() -> translateBlocking(text, sourceLang, targetLang));
    }

    private TranslationResult translateBlocking(String text, String sourceLang, String targetLang) {
        String pairKey = modelManager.pairKey(sourceLang, targetLang);
        if (FAILED_PAIRS.contains(pairKey)) {
            return TranslationResult.failure(text);
        }

        Path modelDir = modelManager.modelDir(sourceLang, targetLang);
        if (!modelManager.isCached(sourceLang, targetLang)) {
            if (!awaitDownload(sourceLang, targetLang, modelDir, pairKey)) {
                return TranslationResult.failure(text);
            }
        }

        TranslationResult result = translator.translate(text, modelDir);
        if (!result.success()) {
            ChatTranslator.LOGGER.warn(
                    "On-device translation failed for {}->{}", sourceLang, targetLang);
        }
        return result;
    }

    private boolean awaitDownload(
            String sourceLang,
            String targetLang,
            Path modelDir,
            String pairKey
    ) {
        try {
            Boolean downloaded = inFlightDownload(sourceLang, targetLang, modelDir, pairKey)
                    .get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(downloaded)) {
                return true;
            }
            FAILED_PAIRS.add(pairKey);
        } catch (TimeoutException timeout) {
            ChatTranslator.LOGGER.warn(
                    "Timed out after {}s waiting for {}->{} model",
                    DOWNLOAD_TIMEOUT_SECONDS, sourceLang, targetLang);
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn(
                    "Failed to download {}->{} model", sourceLang, targetLang, error);
        }
        return false;
    }

    private CompletableFuture<Boolean> inFlightDownload(
            String sourceLang,
            String targetLang,
            Path modelDir,
            String pairKey
    ) {
        boolean[] started = {false};
        CompletableFuture<Boolean> download = IN_FLIGHT_DOWNLOADS.computeIfAbsent(pairKey, key -> {
            started[0] = true;
            LocalNotices.show("Downloading the " + sourceLang + "->" + targetLang
                    + " translation model…");
            return downloader.download(sourceLang, targetLang, modelDir);
        });
        if (started[0]) {
            download.whenComplete((ok, error) -> {
                IN_FLIGHT_DOWNLOADS.remove(pairKey, download);
                if (Boolean.TRUE.equals(ok)) {
                    LocalNotices.show(sourceLang + "->" + targetLang + " model ready.");
                } else if (error == null) {
                    LocalNotices.show("Couldn't download the " + sourceLang + "->"
                            + targetLang + " model.");
                }
            });
        }
        return download;
    }
}
