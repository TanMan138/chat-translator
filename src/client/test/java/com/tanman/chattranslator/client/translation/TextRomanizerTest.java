package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextRomanizerTest {

    @Test
    void usesChatStyleRussianMappings() {
        assertEquals(
                "Privet! Schastye, yula, khorosho.",
                TextRomanizer.toLatinAscii("Привет! Счастье, юла, хорошо.", "ru"));
    }

    @Test
    void usesChatStyleUkrainianMappings() {
        assertEquals(
                "Pryvit! Gvara, yizhak, heroy.",
                TextRomanizer.toLatinAscii("Привіт! Ґвара, їжак, герой.", "uk"));
    }

    @Test
    void preservesCaseAndFoldsPunctuationToAscii() {
        assertEquals(
                "ZhUK, SchUKA - <<da>>...",
                TextRomanizer.toLatinAscii("ЖУК, ЩУКА — «да»…", "ru"));
    }

    @Test
    void fallsBackToIcuForOtherLanguages() {
        String out = TextRomanizer.toLatinAscii("こんにちは", "ja");
        assertFalse(out.isBlank());
        assertTrue(out.chars().allMatch(c -> c <= 0x7F), "expected ASCII only, got: " + out);
        assertFalse(out.contains("こ"));
    }

    @Test
    void leavesAsciiUnchanged() {
        assertEquals("hello", TextRomanizer.toLatinAscii("hello", "ru"));
    }

    @Test
    void preservesUnicodeSpacingAsAsciiSpaces() {
        assertEquals("10 000 rub.", TextRomanizer.toLatinAscii("10 000 руб.", "ru"));
    }
}
