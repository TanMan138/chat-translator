package com.tanman.chattranslator.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tanman.chattranslator.client.state.TranslationState;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted user preferences under {@code config/chat-translator.json}.
 */
public final class TranslatorConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("chat-translator");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** When true, outgoing text is romanized to Latin ASCII. Default on. */
    public boolean latinOutgoing = true;

    /** When true, outgoing target follows last click-detected language. */
    public boolean auto = true;

    /** Manual target language code when {@link #auto} is false. */
    public String targetLanguage = null;

    /**
     * When true, incoming foreign chat is translated as it arrives and the English is
     * appended to the line. Only applies when no download is needed — an uncached
     * on-device pair still waits for a hover. Default on.
     */
    public boolean autoTranslateIncoming = true;

    /** Translation backend tier. */
    public TranslationBackendType backend = TranslationBackendType.ON_DEVICE;

    /** Cloud provider when {@link #backend} is {@link TranslationBackendType#MANAGED_CLOUD}. */
    public CloudProvider cloudProvider = CloudProvider.DEEPL;

    public String deeplApiKey = "";

    /**
     * Only consulted for keys that carry no plan suffix — a {@code :fx} key picks the
     * free host on its own. Defaults off because DeepL's API Free plan is closed to
     * new signups, so a fresh install is far more likely to hold a paid key.
     */
    public boolean deeplUseFreeApi = false;
    public String googleApiKey = "";
    public String langblyApiKey = "";
    public boolean langblyUseEuEndpoint = false;
    public String customEndpointUrl = "";
    public String ollamaModel = "qwen2.5:1.5b";

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("chat-translator.json");
    }

    public static TranslatorConfig load() {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            return new TranslatorConfig();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            TranslatorConfig loaded = GSON.fromJson(reader, TranslatorConfig.class);
            if (loaded == null) {
                return new TranslatorConfig();
            }
            loaded.normalize();
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read {}; using defaults", file, e);
            return new TranslatorConfig();
        }
    }

    public void save() {
        normalize();
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write {}", file, e);
        }
    }

    public void applyTo(TranslationState state) {
        state.restore(auto, targetLanguage, latinOutgoing);
    }

    public void captureFrom(TranslationState state) {
        latinOutgoing = state.isLatinOutgoing();
        auto = state.isAuto();
        // Only persist a locked manual target — auto detections are ephemeral.
        targetLanguage = auto ? null : state.getCurrentTargetLanguage().orElse(null);
    }

    public boolean isRemoteBackend() {
        return backend != TranslationBackendType.ON_DEVICE;
    }

    public void normalize() {
        if (backend == null) {
            backend = TranslationBackendType.ON_DEVICE;
        }
        if (cloudProvider == null) {
            cloudProvider = CloudProvider.DEEPL;
        }
        if (deeplApiKey == null) {
            deeplApiKey = "";
        }
        if (googleApiKey == null) {
            googleApiKey = "";
        }
        if (langblyApiKey == null) {
            langblyApiKey = "";
        }
        if (customEndpointUrl == null) {
            customEndpointUrl = "";
        }
        if (ollamaModel == null || ollamaModel.isBlank()) {
            ollamaModel = "qwen2.5:1.5b";
        }
    }
}
