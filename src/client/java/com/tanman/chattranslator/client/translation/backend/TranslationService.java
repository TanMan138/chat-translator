package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.client.LocalNotices;
import com.tanman.chattranslator.client.config.CloudProvider;
import com.tanman.chattranslator.client.config.TranslationBackendType;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.TranslationCache;
import com.tanman.chattranslator.client.translation.UsageTracker;
import com.tanman.chattranslator.client.translation.TranslationResult;
import com.tanman.chattranslator.client.translation.Translator;

import java.time.YearMonth;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes translation requests to the configured backend tier.
 */
public final class TranslationService {

    private static final Set<String> MISSING_CONFIG_NOTICES = ConcurrentHashMap.newKeySet();

    /** Characters to let accumulate before rewriting the config file. */
    private static final int SAVE_EVERY_CHARACTERS = 2_000;

    private final TranslationCache cache = new TranslationCache();

    private final UsageTracker usage;

    private int unsavedCharacters;

    /**
     * Which backend produced what is currently cached. Providers disagree, so the
     * cache is dropped whenever the player switches method or cloud provider.
     */
    private volatile String cachedUnder = "";

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
        config.normalize();
        this.usage = new UsageTracker(config.usageMonth, config.usageByProvider);
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

    /**
     * True when a translation can start right now without fetching a model first.
     * Auto-translate uses this so joining a server never kicks off a ~100 MB download
     * the player did not ask for.
     */
    public boolean canTranslateWithoutDownload(String sourceLang, String targetLang) {
        TranslationBackend backend = resolveBackend();
        if (backend instanceof OnDeviceBackend onDeviceBackend) {
            return onDeviceBackend.isCached(sourceLang, targetLang);
        }
        return validateConfig(backend);
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
        backend = applyUsageBudget(backend, text);
        TranslationBackend chosen = backend;
        return cache.get(sourceLang, targetLang, text)
                .map(hit -> CompletableFuture.completedFuture(
                        TranslationResult.success(hit, text)))
                .orElseGet(() -> chosen.translate(text, sourceLang, targetLang)
                        .thenApply(result -> {
                            if (result != null && result.success()) {
                                cache.put(sourceLang, targetLang, text, result.translatedText());
                            }
                            return result;
                        }));
    }

    /**
     * Downloads a language pair ahead of time.
     *
     * @return {@code false} when the active backend needs no local model
     */
    public boolean prewarm(String sourceLang, String targetLang) {
        if (resolveBackend() instanceof OnDeviceBackend onDeviceBackend) {
            onDeviceBackend.prewarm(sourceLang, targetLang);
            return true;
        }
        return false;
    }

    /**
     * Counts what is about to be sent to a paid service and, once the player's
     * monthly budget is used up, hands the work to the on-device engine instead.
     *
     * <p>Falling back beats refusing: chat keeps being translated, for free, and the
     * player is told once rather than silently losing translations.
     */
    private TranslationBackend applyUsageBudget(TranslationBackend backend, String text) {
        String provider = providerKey(backend);
        if (provider == null || text == null || text.isEmpty()) {
            return backend;
        }

        int budget = config.monthlyCharacterBudget;
        String month = currentMonth();
        if (budget > 0 && usage.used(provider, month) >= budget) {
            noticeOnce("budget-" + provider + "-" + month,
                    "You have used this month's " + budget + "-character budget for "
                            + provider + ". Translating on your computer instead — free, "
                            + "and no surprise bill. Raise or clear the budget in Config.");
            return onDevice;
        }

        long used = usage.record(provider, month, text.length());
        persistUsage(text.length());

        switch (UsageTracker.level(used, budget)) {
            case WARN_80 -> noticeOnce("budget80-" + provider + "-" + month,
                    "Used about " + used + " of " + budget + " characters on " + provider
                            + " this month (roughly 80%).");
            case WARN_95 -> noticeOnce("budget95-" + provider + "-" + month,
                    "Used about " + used + " of " + budget + " characters on " + provider
                            + " this month (roughly 95%). Past the budget the mod switches "
                            + "to translating on your computer.");
            default -> {
            }
        }
        return backend;
    }

    /** Usage is an estimate, so it is written in batches rather than per message. */
    private void persistUsage(int addedCharacters) {
        unsavedCharacters += addedCharacters;
        if (unsavedCharacters < SAVE_EVERY_CHARACTERS) {
            return;
        }
        unsavedCharacters = 0;
        saveUsage();
    }

    /** Flushes the running estimate into the config file. */
    public void saveUsage() {
        config.usageMonth = usage.month();
        config.usageByProvider = usage.snapshot();
        config.save();
    }

    public UsageTracker usage() {
        return usage;
    }

    public static String currentMonth() {
        return YearMonth.now().toString();
    }

    /** Null for backends that cost nothing, so only paid traffic is counted. */
    static String providerKey(TranslationBackend backend) {
        if (backend instanceof DeepLBackend) {
            return "DeepL";
        }
        if (backend instanceof GoogleTranslateBackend) {
            return "Google";
        }
        if (backend instanceof LangblyBackend) {
            return "Langbly";
        }
        // On-device costs nothing, and a self-hosted Ollama is the player's own box.
        return null;
    }

    public void clearCache() {
        cache.clear();
    }

    TranslationBackend resolveBackend() {
        config.normalize();
        String key = config.backend + "/" + config.cloudProvider;
        if (!key.equals(cachedUnder)) {
            cachedUnder = key;
            cache.clear();
        }
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
