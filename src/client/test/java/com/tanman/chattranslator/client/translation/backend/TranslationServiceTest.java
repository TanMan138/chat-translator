package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.client.config.CloudProvider;
import com.tanman.chattranslator.client.config.TranslationBackendType;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationServiceTest {

    @TempDir
    Path modelsDir;

    @Test
    void selectsOnDeviceByDefault() {
        TranslationService service = createService(new TranslatorConfig());
        assertInstanceOf(OnDeviceBackend.class, service.resolveBackend());
        assertTrue(service.requiresModelDownload());
        assertFalse(service.isRemoteBackend());
    }

    @Test
    void selectsDeepLWhenManagedCloud() {
        TranslatorConfig config = new TranslatorConfig();
        config.backend = TranslationBackendType.MANAGED_CLOUD;
        config.cloudProvider = CloudProvider.DEEPL;
        TranslationService service = createService(config);
        assertInstanceOf(DeepLBackend.class, service.resolveBackend());
        assertTrue(service.isRemoteBackend());
    }

    @Test
    void selectsGoogleWhenConfigured() {
        TranslatorConfig config = new TranslatorConfig();
        config.backend = TranslationBackendType.MANAGED_CLOUD;
        config.cloudProvider = CloudProvider.GOOGLE;
        TranslationService service = createService(config);
        assertInstanceOf(GoogleTranslateBackend.class, service.resolveBackend());
    }

    @Test
    void selectsLangblyWhenConfigured() {
        TranslatorConfig config = new TranslatorConfig();
        config.backend = TranslationBackendType.MANAGED_CLOUD;
        config.cloudProvider = CloudProvider.LANGBLY;
        TranslationService service = createService(config);
        assertInstanceOf(LangblyBackend.class, service.resolveBackend());
        assertTrue(service.isRemoteBackend());
        assertFalse(service.requiresModelDownload());
    }

    @Test
    void selectsOllamaForCustomBackend() {
        TranslatorConfig config = new TranslatorConfig();
        config.backend = TranslationBackendType.CUSTOM;
        TranslationService service = createService(config);
        assertInstanceOf(OllamaBackend.class, service.resolveBackend());
        assertFalse(service.requiresModelDownload());
    }

    private TranslationService createService(TranslatorConfig config) {
        ModelManager modelManager = new ModelManager(modelsDir);
        return new TranslationService(config, modelManager, new ModelDownloader(), new Translator());
    }
}
