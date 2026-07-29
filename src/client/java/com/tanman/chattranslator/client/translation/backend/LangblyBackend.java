package com.tanman.chattranslator.client.translation.backend;

import com.google.gson.JsonObject;
import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.TranslationResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Langbly speaks the Google Translate v2 request/response format, but authenticates with a
 * bearer token instead of a {@code ?key=} query parameter, so it cannot reuse
 * {@link GoogleTranslateBackend} directly.
 */
public final class LangblyBackend implements TranslationBackend {

    static final String GLOBAL_ENDPOINT = "https://api.langbly.com/language/translate/v2";
    static final String EU_ENDPOINT = "https://eu.langbly.com/language/translate/v2";

    private final TranslatorConfig config;

    public LangblyBackend(TranslatorConfig config) {
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
        String apiKey = config.langblyApiKey == null ? "" : config.langblyApiKey.trim();
        if (apiKey.isEmpty()) {
            return TranslationResult.failure(text);
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("q", text);
            payload.addProperty("target", GoogleTranslateBackend.toGoogleCode(targetLang));
            payload.addProperty("format", "text");
            if (sourceLang != null && !sourceLang.isBlank() && !"auto".equalsIgnoreCase(sourceLang)) {
                payload.addProperty("source", GoogleTranslateBackend.toGoogleCode(sourceLang));
            }

            String responseBody = JsonHttp.postJson(
                    endpoint(config.langblyUseEuEndpoint),
                    payload.toString(),
                    Map.of("Authorization", "Bearer " + apiKey));
            String translated = JsonHttp.parseGoogleTranslateResponse(responseBody);
            return TranslationResult.success(translated, text);
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Langbly translation failed", error);
            return TranslationResult.failure(text);
        }
    }

    static String endpoint(boolean useEuEndpoint) {
        return useEuEndpoint ? EU_ENDPOINT : GLOBAL_ENDPOINT;
    }
}
