package com.tanman.chattranslator.client.translation;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.LocalNotices;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ModelDownloader {

    private static final String BASE_URL = "https://huggingface.co/Xenova/opus-mt-%s-%s/resolve/main/";

    /**
     * Only one language-pair download at a time. Parallel pair downloads (~100 MB each)
     * saturate disk/network and make the game hitch even when they are off the render
     * thread (GC + I/O wait).
     */
    private static final Semaphore PAIR_SLOT = new Semaphore(1);

    private static final ExecutorService PAIR_QUEUE = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-translator-download");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Must follow redirects: Hugging Face {@code /resolve/} URLs return 302/307 to a
     * CDN. {@link HttpClient#newHttpClient()} defaults to {@link HttpClient.Redirect#NEVER}.
     */
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public CompletableFuture<DownloadOutcome> download(String sourceLang, String targetLang, Path destDir) {
        String pair = sourceLang + "->" + targetLang;
        return CompletableFuture.supplyAsync(() -> {
            PAIR_SLOT.acquireUninterruptibly();
            try {
                return downloadPair(sourceLang, targetLang, destDir, pair);
            } finally {
                PAIR_SLOT.release();
            }
        }, PAIR_QUEUE);
    }

    private DownloadOutcome downloadPair(String sourceLang, String targetLang, Path destDir, String pair) {
        String repoBase = String.format(BASE_URL, sourceLang, targetLang);
        LocalNotices.show("Downloading " + pair + " (4 files)…");

        record FileJob(String label, String url, Path dest) {
        }

        FileJob[] jobs = {
                new FileJob("encoder", repoBase + "onnx/encoder_model_quantized.onnx",
                        destDir.resolve(ModelFiles.ENCODER)),
                new FileJob("decoder", repoBase + "onnx/decoder_model_quantized.onnx",
                        destDir.resolve(ModelFiles.DECODER)),
                new FileJob("tokenizer", repoBase + ModelFiles.TOKENIZER,
                        destDir.resolve(ModelFiles.TOKENIZER)),
                new FileJob("config", repoBase + ModelFiles.GENERATION_CONFIG,
                        destDir.resolve(ModelFiles.GENERATION_CONFIG)),
        };

        // Sequential within a pair: clearer chat progress and less disk thrash than
        // four parallel ~50 MB writes.
        for (int i = 0; i < jobs.length; i++) {
            FileJob job = jobs[i];
            LocalNotices.show(pair + ": " + job.label() + " (" + (i + 1) + "/4)…");
            FetchResult result = fetchToFile(job.url(), job.dest(), pair, job.label());
            if (result != FetchResult.OK) {
                if (result == FetchResult.NOT_AVAILABLE) {
                    ChatTranslator.LOGGER.warn(
                            "No on-device OPUS-MT model for {} (Hugging Face has no Xenova/opus-mt-{}-{} repo)",
                            pair, sourceLang, targetLang);
                    LocalNotices.show(pair + ": no on-device model published for this language.");
                } else {
                    LocalNotices.show(pair + ": failed on " + job.label() + ".");
                }
                return result == FetchResult.NOT_AVAILABLE
                        ? DownloadOutcome.NOT_AVAILABLE
                        : DownloadOutcome.FAILED;
            }
            LocalNotices.show(pair + ": " + job.label() + " done (" + (i + 1) + "/4).");
        }
        return DownloadOutcome.SUCCESS;
    }

    private FetchResult fetchToFile(String url, Path dest, String pair, String label) {
        Path tempFile = dest.resolveSibling(dest.getFileName() + ".part");

        try {
            Files.createDirectories(dest.getParent());
        } catch (IOException e) {
            ChatTranslator.LOGGER.warn("Could not create model directory {}: {}", dest.getParent(), e.toString());
            return FetchResult.FAILED;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                DownloadOutcome classified = classifyHttpStatus(status);
                if (classified == DownloadOutcome.NOT_AVAILABLE) {
                    ChatTranslator.LOGGER.warn(
                            "On-device model not available (HTTP {}): {}", status, url);
                    deleteQuietly(tempFile);
                    return FetchResult.NOT_AVAILABLE;
                }
                ChatTranslator.LOGGER.warn(
                        "Model file download failed (HTTP {}): {}", status, url);
                deleteQuietly(tempFile);
                return FetchResult.FAILED;
            }

            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            long lastNoticeNanos = 0L;
            int lastPct = -1;

            try (InputStream in = response.body();
                    OutputStream out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[64 * 1024];
                long read = 0;
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                    read += n;
                    if (contentLength > 0) {
                        int pct = (int) Math.min(100, (read * 100) / contentLength);
                        long now = System.nanoTime();
                        // Chat spam guard: every ~10% or at least every 2s.
                        if (pct >= lastPct + 10 || now - lastNoticeNanos > 2_000_000_000L) {
                            LocalNotices.show(pair + " " + label + ": " + pct + "%");
                            lastPct = pct;
                            lastNoticeNanos = now;
                        }
                    }
                }
            }

            Files.move(tempFile, dest, StandardCopyOption.REPLACE_EXISTING);
            if (ModelFiles.TOKENIZER.equals(dest.getFileName().toString())) {
                TokenizerSanitizer.sanitize(dest);
            }
            return FetchResult.OK;
        } catch (Exception e) {
            ChatTranslator.LOGGER.warn("Model file download error for {}: {}", url, e.toString());
            deleteQuietly(tempFile);
            return FetchResult.FAILED;
        }
    }

    /**
     * Hugging Face often returns 401/403 for missing private-or-absent model repos
     * instead of a clean 404 — treat those as "not published", not a bad password.
     */
    static DownloadOutcome classifyHttpStatus(int statusCode) {
        return switch (statusCode) {
            case 401, 403, 404 -> DownloadOutcome.NOT_AVAILABLE;
            default -> DownloadOutcome.FAILED;
        };
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private enum FetchResult {
        OK,
        NOT_AVAILABLE,
        FAILED
    }
}
