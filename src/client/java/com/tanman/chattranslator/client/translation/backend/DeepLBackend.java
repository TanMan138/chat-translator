package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.ChatTranslator;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.TranslationResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DeepLBackend implements TranslationBackend {

    private final TranslatorConfig config;

    public DeepLBackend(TranslatorConfig config) {
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
        String apiKey = config.deeplApiKey == null ? "" : config.deeplApiKey.trim();
        if (apiKey.isEmpty()) {
            return TranslationResult.failure(text);
        }

        try {
            String host = config.deeplUseFreeApi ? "api-free.deepl.com" : "api.deepl.com";
            Map<String, String> form = new HashMap<>();
            form.put("text", text);
            form.put("target_lang", toDeepLCode(targetLang));
            if (sourceLang != null && !sourceLang.isBlank() && !"auto".equalsIgnoreCase(sourceLang)) {
                form.put("source_lang", toDeepLCode(sourceLang));
            }

            Map<String, String> headers = Map.of("Authorization", "DeepL-Auth-Key " + apiKey);
            String responseBody = JsonHttp.postForm(
                    "https://" + host + "/v2/translate", form, headers);
            String translated = JsonHttp.parseDeepLResponse(responseBody);
            return TranslationResult.success(translated, text);
        } catch (Exception error) {
            ChatTranslator.LOGGER.warn("DeepL translation failed", error);
            return TranslationResult.failure(text);
        }
    }

    static String toDeepLCode(String lang) {
        if (lang == null || lang.isBlank()) {
            return "EN";
        }
        return lang.trim().toUpperCase(Locale.ROOT);
    }
}
