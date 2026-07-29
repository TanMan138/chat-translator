package com.tanman.chattranslator.client.translation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class ModelDownloader {

    private static final String BASE_URL = "https://huggingface.co/Xenova/opus-mt-%s-%s/resolve/main/";

    private final HttpClient client = HttpClient.newHttpClient();

    public CompletableFuture<Boolean> download(String sourceLang, String targetLang, Path destDir) {
        String repoBase = String.format(BASE_URL, sourceLang, targetLang);

        // The Xenova/opus-mt-* repos have no fused "model.onnx"; the export is split
        // into separate encoder and decoder graphs under onnx/. We take the quantized
        // variants (~50 MB each) and the non-KV-cache decoder, which is the graph the
        // greedy loop in MarianMtModel drives.
        CompletableFuture<Boolean> encoderFuture = fetchToFile(
                repoBase + "onnx/encoder_model_quantized.onnx", destDir.resolve(ModelFiles.ENCODER));
        CompletableFuture<Boolean> decoderFuture = fetchToFile(
                repoBase + "onnx/decoder_model_quantized.onnx", destDir.resolve(ModelFiles.DECODER));
        CompletableFuture<Boolean> tokenizerFuture = fetchToFile(
                repoBase + ModelFiles.TOKENIZER, destDir.resolve(ModelFiles.TOKENIZER));
        // Carries decoder_start_token_id / eos_token_id, which differ per language pair.
        CompletableFuture<Boolean> generationConfigFuture = fetchToFile(
                repoBase + ModelFiles.GENERATION_CONFIG, destDir.resolve(ModelFiles.GENERATION_CONFIG));

        return CompletableFuture.allOf(
                        encoderFuture, decoderFuture, tokenizerFuture, generationConfigFuture)
                .thenApply(ignored ->
                        encoderFuture.join()
                                && decoderFuture.join()
                                && tokenizerFuture.join()
                                && generationConfigFuture.join());
    }

    private CompletableFuture<Boolean> fetchToFile(String url, Path dest) {
        Path tempFile = dest.resolveSibling(dest.getFileName() + ".part");

        // BodyHandlers.ofFile opens the destination file eagerly, before any bytes
        // arrive, so the parent directory must exist before the request is sent
        // (not merely before the final move) or the request fails immediately.
        try {
            Files.createDirectories(dest.getParent());
        } catch (IOException e) {
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(tempFile))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        deleteQuietly(tempFile);
                        return false;
                    }
                    try {
                        Files.move(tempFile, dest, StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    } catch (IOException e) {
                        deleteQuietly(tempFile);
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    deleteQuietly(tempFile);
                    return false;
                });
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
