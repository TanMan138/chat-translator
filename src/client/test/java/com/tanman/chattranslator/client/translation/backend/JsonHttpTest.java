package com.tanman.chattranslator.client.translation.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonHttpTest {

    @Test
    void parseOllamaResponse() {
        String json = """
                {"model":"qwen2.5:1.5b","response":"Hello world","done":true}
                """;
        assertEquals("Hello world", JsonHttp.parseOllamaResponse(json));
    }

    @Test
    void parseDeepLResponse() {
        String json = """
                {"translations":[{"detected_source_language":"FR","text":"Hello"}]}
                """;
        assertEquals("Hello", JsonHttp.parseDeepLResponse(json));
    }

    @Test
    void parseGoogleTranslateResponse() {
        String json = """
                {"data":{"translations":[{"translatedText":"Bonjour","detectedSourceLanguage":"en"}]}}
                """;
        assertEquals("Bonjour", JsonHttp.parseGoogleTranslateResponse(json));
    }

    @Test
    void normalizeEndpointStripsTrailingSlash() {
        assertEquals("http://localhost:11434", JsonHttp.normalizeEndpoint("http://localhost:11434/"));
    }

    @Test
    void parseOllamaMissingFieldThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonHttp.parseOllamaResponse("{\"done\":true}"));
    }
}
