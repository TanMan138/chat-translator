package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.LocalNotices;
import com.tanman.chattranslator.client.translation.DownloadOutcome;
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

    /**
     * A pair is ~100 MB over four files. 60s was shorter than the download itself on
     * ordinary connections, so the first translation reported failure — and three of
     * those blacklisted the pair for the session even though the fetch was fine.
     * Callers already apply their own (shorter) timeout for what the player waits on;
     * this one only bounds a genuinely stuck transfer.
     */
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 15 * 60;

    private static final Set<String> FAILED_PAIRS = ConcurrentHashMap.newKeySet();
    private static final Map<String, CompletableFuture<DownloadOutcome>> IN_FLIGHT_DOWNLOADS =
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

    /** True when the pair is already on disk, so translating needs no network. */
    public boolean isCached(String sourceLang, String targetLang) {
        return !FAILED_PAIRS.contains(modelManager.pairKey(sourceLang, targetLang))
                && modelManager.isCached(sourceLang, targetLang);
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
        Download download = inFlightDownload(sourceLang, targetLang, modelDir, pairKey);
        if (!download.started()) {
            // Someone is already fetching this pair and streaming progress to chat.
            // Waiting here too would just park another thread for the whole download.
            return false;
        }
        try {
            DownloadOutcome outcome = download.future()
                    .get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (outcome != null && outcome.ok()) {
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

    /** A pair download plus whether this caller is the one that kicked it off. */
    private record Download(CompletableFuture<DownloadOutcome> future, boolean started) {
    }

    /** Starts the model download for a pair, or joins the one already running. */
    public void prewarm(String sourceLang, String targetLang) {
        String pairKey = modelManager.pairKey(sourceLang, targetLang);
        FAILED_PAIRS.remove(pairKey);
        inFlightDownload(sourceLang, targetLang,
                modelManager.modelDir(sourceLang, targetLang), pairKey);
    }

    private Download inFlightDownload(
            String sourceLang,
            String targetLang,
            Path modelDir,
            String pairKey
    ) {
        boolean[] started = {false};
        CompletableFuture<DownloadOutcome> download = IN_FLIGHT_DOWNLOADS.computeIfAbsent(pairKey, key -> {
            started[0] = true;
            LocalNotices.show("Downloading the " + sourceLang + "->" + targetLang
                    + " translation model…");
            return downloader.download(sourceLang, targetLang, modelDir);
        });
        if (started[0]) {
            download.whenComplete((outcome, error) -> {
                IN_FLIGHT_DOWNLOADS.remove(pairKey, download);
                if (error != null) {
                    return;
                }
                if (outcome != null && outcome.ok()) {
                    LocalNotices.show(sourceLang + "->" + targetLang + " model ready.");
                } else if (outcome == DownloadOutcome.NOT_AVAILABLE) {
                    LocalNotices.show("No on-device model for " + sourceLang + "->"
                            + targetLang + " — that language isn't supported offline. "
                            + "Try Online service (DeepL / Langbly) in Config.");
                } else {
                    LocalNotices.show("Couldn't download the " + sourceLang + "->"
                            + targetLang + " model — check your internet and try again.");
                }
            });
        }
        return new Download(download, started[0]);
    }
}
