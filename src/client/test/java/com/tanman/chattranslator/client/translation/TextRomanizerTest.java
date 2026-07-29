package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextRomanizerTest {

    @Test
    void romanizesCyrillicToAsciiLatin() {
        String out = TextRomanizer.toLatinAscii("Привет");
        assertFalse(out.isBlank());
        assertTrue(out.chars().allMatch(c -> c <= 0x7F), "expected ASCII only, got: " + out);
        // Should still look like a greeting, not empty / unchanged Cyrillic
        assertFalse(out.contains("П"));
    }

    @Test
    void leavesAsciiUnchanged() {
        assertEquals("hello", TextRomanizer.toLatinAscii("hello"));
    }
}
