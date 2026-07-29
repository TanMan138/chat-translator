package com.tanman.chattranslator.client.translation.backend;

import com.google.gson.JsonObject;
import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.TranslationResult;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

public final class OllamaBackend implements TranslationBackend {

    private static final String SYSTEM_PROMPT =
            "You are a Minecraft chat translator. Translate the following text to %s. "
                    + "You must maintain gamer slang and Minecraft terminology like 'bedwars', "
                    + "'aggro', 'gank', and 'griefing' naturally without translating them literally. "
                    + "Return ONLY the translated text.";

    private final TranslatorConfig config;

    public OllamaBackend(TranslatorConfig config) {
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
        String endpoint = JsonHttp.normalizeEndpoint(config.customEndpointUrl);
        if (endpoint.isEmpty()) {
            return TranslationResult.failure(text);
        }

        try {
            String model = config.ollamaModel == null || config.ollamaModel.isBlank()
                    ? "qwen2.5:1.5b"
                    : config.ollamaModel.trim();
            String targetName = languageName(targetLang);

            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            payload.addProperty("prompt", text);
            payload.addProperty("system", String.format(SYSTEM_PROMPT, targetName));
            payload.addProperty("stream", false);

            String responseBody = JsonHttp.postJson(
                    endpoint + "/api/generate", payload.toString(), Map.of());
            String translated = JsonHttp.parseOllamaResponse(responseBody);
            return TranslationResult.success(translated, text);
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("Ollama translation failed", error);
            return TranslationResult.failure(text);
        }
    }

    static String languageName(String code) {
        if (code == null || code.isBlank()) {
            return "English";
        }
        return switch (code.trim().toLowerCase()) {
            case "en" -> "English";
            case "fr" -> "French";
            case "de" -> "German";
            case "es" -> "Spanish";
            case "ru" -> "Russian";
            case "ja" -> "Japanese";
            case "zh" -> "Chinese";
            case "ko" -> "Korean";
            case "pt" -> "Portuguese";
            case "it" -> "Italian";
            case "nl" -> "Dutch";
            case "pl" -> "Polish";
            case "uk" -> "Ukrainian";
            case "ar" -> "Arabic";
            case "tr" -> "Turkish";
            default -> code;
        };
    }
}
