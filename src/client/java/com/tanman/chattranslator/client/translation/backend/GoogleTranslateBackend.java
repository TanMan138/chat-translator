package com.tanman.chattranslator.client.translation.backend;

import com.google.gson.JsonObject;
import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.TranslationResult;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class GoogleTranslateBackend implements TranslationBackend {

    private static final String ENDPOINT =
            "https://translation.googleapis.com/language/translate/v2";

    private final TranslatorConfig config;

    public GoogleTranslateBackend(TranslatorConfig config) {
        this.config = config;
    }

    @Override
    public boolean requiresModelDownload() {
        return false;
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
        String apiKey = config.googleApiKey == null ? "" : config.googleApiKey.trim();
        if (apiKey.isEmpty()) {
            return TranslationResult.failure(text);
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("q", text);
            payload.addProperty("target", toGoogleCode(targetLang));
            payload.addProperty("format", "text");
            if (sourceLang != null && !sourceLang.isBlank() && !"auto".equalsIgnoreCase(sourceLang)) {
                payload.addProperty("source", toGoogleCode(sourceLang));
            }

            String url = ENDPOINT + "?key=" + apiKey;
            String responseBody = JsonHttp.postJson(url, payload.toString(), Map.of());
            String translated = JsonHttp.parseGoogleTranslateResponse(responseBody);
            return TranslationResult.success(translated, text);
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Google Translate failed", error);
            return TranslationResult.failure(text);
        }
    }

    static String toGoogleCode(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en";
        }
        return lang.trim().toLowerCase(Locale.ROOT);
    }
}
