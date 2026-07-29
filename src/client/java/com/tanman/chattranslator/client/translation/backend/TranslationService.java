package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.client.LocalNotices;
import com.tanman.chattranslator.client.config.CloudProvider;
import com.tanman.chattranslator.client.config.TranslationBackendType;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.Translator;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes translation requests to the configured backend tier.
 */
public final class TranslationService {

    private static final Set<String> MISSING_CONFIG_NOTICES = ConcurrentHashMap.newKeySet();

    private final TranslatorConfig config;
    private final OnDeviceBackend onDevice;
    private final DeepLBackend deepL;
    private final GoogleTranslateBackend google;
    private final LangblyBackend langbly;
    private final OllamaBackend ollama;

    public TranslationService(
            TranslatorConfig config,
            ModelManager modelManager,
            ModelDownloader downloader,
            Translator translator
    ) {
        this.config = config;
        this.onDevice = new OnDeviceBackend(modelManager, downloader, translator);
        this.deepL = new DeepLBackend(config);
        this.google = new GoogleTranslateBackend(config);
        this.langbly = new LangblyBackend(config);
        this.ollama = new OllamaBackend(config);
    }

    public boolean isRemoteBackend() {
        return config.isRemoteBackend();
    }

    public boolean requiresModelDownload() {
        return resolveBackend().requiresModelDownload();
    }

    public CompletableFuture<TranslationResult> translate(
            String text,
            String sourceLang,
            String targetLang
    ) {
        TranslationBackend backend = resolveBackend();
        if (!validateConfig(backend)) {
            return CompletableFuture.completedFuture(TranslationResult.failure(text));
        }
        return backend.translate(text, sourceLang, targetLang);
    }

    TranslationBackend resolveBackend() {
        config.normalize();
        return switch (config.backend) {
            case ON_DEVICE -> onDevice;
            case MANAGED_CLOUD -> switch (config.cloudProvider) {
                case GOOGLE -> google;
                case LANGBLY -> langbly;
                case DEEPL -> deepL;
            };
            case CUSTOM -> ollama;
        };
    }

    private boolean validateConfig(TranslationBackend backend) {
        if (backend instanceof DeepLBackend) {
            if (config.deeplApiKey == null || config.deeplApiKey.isBlank()) {
                noticeOnce("deepl-key", "Set your DeepL API key in Mod Menu config.");
                return false;
            }
        } else if (backend instanceof GoogleTranslateBackend) {
            if (config.googleApiKey == null || config.googleApiKey.isBlank()) {
                noticeOnce("google-key", "Set your Google Translate API key in Mod Menu config.");
                return false;
            }
        } else if (backend instanceof LangblyBackend) {
            if (config.langblyApiKey == null || config.langblyApiKey.isBlank()) {
                noticeOnce("langbly-key", "Set your Langbly API key in Mod Menu config.");
                return false;
            }
        } else if (backend instanceof OllamaBackend) {
            if (JsonHttp.normalizeEndpoint(config.customEndpointUrl).isEmpty()) {
                noticeOnce("ollama-url", "Set your Ollama endpoint URL in Mod Menu config.");
                return false;
            }
        }
        return true;
    }

    private static void noticeOnce(String key, String message) {
        if (MISSING_CONFIG_NOTICES.add(key)) {
            LocalNotices.show(message);
        }
    }
}
