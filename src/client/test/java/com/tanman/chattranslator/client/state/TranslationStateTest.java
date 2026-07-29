package com.tanman.chattranslator.client.state;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationStateTest {

    @Test
    void startsInAutoModeWithNoLanguage() {
        TranslationState state = new TranslationState();
        assertTrue(state.isAuto());
        assertTrue(state.getCurrentTargetLanguage().isEmpty());
    }

    @Test
    void autoModeUpdatesFromDetectedLanguage() {
        TranslationState state = new TranslationState();
        state.onLanguageDetected("fr");
        assertEquals("fr", state.getCurrentTargetLanguage().orElseThrow());
        state.onLanguageDetected("de");
        assertEquals("de", state.getCurrentTargetLanguage().orElseThrow());
    }

    @Test
    void manualOverrideSuspendsAutoUpdates() {
        TranslationState state = new TranslationState();
        state.onLanguageDetected("fr");
        state.setManualTarget("es");
        assertFalse(state.isAuto());
        assertEquals("es", state.getCurrentTargetLanguage().orElseThrow());

        state.onLanguageDetected("de");
        assertEquals("es", state.getCurrentTargetLanguage().orElseThrow());
    }

    @Test
    void switchingBackToAutoResumesUpdates() {
        TranslationState state = new TranslationState();
        state.setManualTarget("es");
        state.setAuto();
        assertTrue(state.isAuto());
        assertTrue(state.getCurrentTargetLanguage().isEmpty());

        state.onLanguageDetected("ja");
        assertEquals("ja", state.getCurrentTargetLanguage().orElseThrow());
    }
}
