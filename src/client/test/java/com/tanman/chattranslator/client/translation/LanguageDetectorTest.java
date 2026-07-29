package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector();

    @Test
    void detectsFrenchText() {
        Optional<String> result = detector.detect("Bonjour, comment allez-vous aujourd'hui?");
        assertEquals("fr", result.orElseThrow());
    }

    @Test
    void returnsEmptyForEnglishText() {
        Optional<String> result = detector.detect("Hello, how are you doing today?");
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyForBlankText() {
        assertTrue(detector.detect("").isEmpty());
        assertTrue(detector.detect("   ").isEmpty());
    }

    @Test
    void returnsEmptyForTooShortAmbiguousText() {
        // Single common short token is not enough signal to act on.
        assertTrue(detector.detect("ok").isEmpty());
    }

    @Test
    void returnsEmptyForShortEnglishChatSlang() {
        // Regression coverage for a real false-positive failure mode: Lingua's
        // per-message confidence is normalized so the top match is ~1.0 for nearly
        // any input, including short English chat tokens confidently (but wrongly)
        // matched to an unrelated language when run without a length gate (e.g.
        // "lol" -> Tswana, "omg" -> Zulu, "wtf" -> Nynorsk, "brb" -> Welsh, all at
        // confidence 1.0, verified empirically against lingua 1.2.2). These are all
        // well under the MIN_TEXT_LENGTH gate and must come back empty rather than
        // being reported as a confident foreign-language match.
        assertTrue(detector.detect("lol").isEmpty());
        assertTrue(detector.detect("omg").isEmpty());
        assertTrue(detector.detect("wtf").isEmpty());
        assertTrue(detector.detect("brb").isEmpty());
        assertTrue(detector.detect("yes").isEmpty());
        assertTrue(detector.detect("cool").isEmpty());
        assertTrue(detector.detect("goodbye").isEmpty());
        assertTrue(detector.detect("amazing").isEmpty());
    }
}
