package com.tanman.chattranslator.client.translation.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendLanguageCodeTest {

    @Test
    void deepLUsesUppercase() {
        assertEquals("EN", DeepLBackend.toDeepLCode("en"));
        assertEquals("FR", DeepLBackend.toDeepLCode("fr"));
    }

    @Test
    void googleUsesLowercase() {
        assertEquals("en", GoogleTranslateBackend.toGoogleCode("EN"));
        assertEquals("ru", GoogleTranslateBackend.toGoogleCode("ru"));
    }

    @Test
    void langblyPicksEndpointByResidency() {
        assertEquals("https://api.langbly.com/language/translate/v2", LangblyBackend.endpoint(false));
        assertEquals("https://eu.langbly.com/language/translate/v2", LangblyBackend.endpoint(true));
    }

    @Test
    void ollamaLanguageNames() {
        assertEquals("English", OllamaBackend.languageName("en"));
        assertEquals("French", OllamaBackend.languageName("fr"));
        assertEquals("xx", OllamaBackend.languageName("xx"));
    }
}
