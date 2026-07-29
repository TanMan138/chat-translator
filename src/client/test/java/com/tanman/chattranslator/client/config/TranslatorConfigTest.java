package com.tanman.chattranslator.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslatorConfigTest {

    @Test
    void defaultsAreOnDevice() {
        TranslatorConfig config = new TranslatorConfig();
        config.normalize();
        assertEquals(TranslationBackendType.ON_DEVICE, config.backend);
        assertEquals(CloudProvider.DEEPL, config.cloudProvider);
        assertEquals("qwen2.5:1.5b", config.ollamaModel);
        assertTrue(config.deeplUseFreeApi);
        assertFalse(config.isRemoteBackend());
    }

    @Test
    void roundTripsNewFields(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("chat-translator.json");
        Files.writeString(file, """
                {
                  "latinOutgoing": false,
                  "auto": false,
                  "targetLanguage": "fr",
                  "backend": "MANAGED_CLOUD",
                  "cloudProvider": "GOOGLE",
                  "deeplApiKey": "deepl-test",
                  "deeplUseFreeApi": false,
                  "googleApiKey": "google-test",
                  "customEndpointUrl": "http://localhost:11434",
                  "ollamaModel": "llama3"
                }
                """);

        TranslatorConfig loaded = loadFrom(file);
        assertEquals(TranslationBackendType.MANAGED_CLOUD, loaded.backend);
        assertEquals(CloudProvider.GOOGLE, loaded.cloudProvider);
        assertEquals("deepl-test", loaded.deeplApiKey);
        assertFalse(loaded.deeplUseFreeApi);
        assertEquals("google-test", loaded.googleApiKey);
        assertEquals("http://localhost:11434", loaded.customEndpointUrl);
        assertEquals("llama3", loaded.ollamaModel);
        assertTrue(loaded.isRemoteBackend());
    }

    private static TranslatorConfig loadFrom(Path file) throws Exception {
        try (var reader = Files.newBufferedReader(file)) {
            var gson = new com.google.gson.Gson();
            TranslatorConfig loaded = gson.fromJson(reader, TranslatorConfig.class);
            loaded.normalize();
            return loaded;
        }
    }
}
