package com.tanman.chattranslator.client.translation;

import com.ibm.icu.text.Transliterator;

/**
 * Turns translated text in any script into Latin letters (optionally ASCII-only).
 *
 * <p>This is <em>romanization</em>, not translation: {@code Привет} becomes something
 * like {@code Privet}, not {@code Hello}. Useful on servers whose AntiSpam blocks
 * Cyrillic / CJK / etc. as "forbidden symbols".
 */
public final class TextRomanizer {

    /**
     * Any script → Latin, then fold to basic ASCII (strip accents like {@code š→s}).
     * Lazy so unit tests that never romanize do not pay ICU class-init cost.
     */
    private static final class Holder {
        static final Transliterator INSTANCE =
                Transliterator.getInstance("Any-Latin; Latin-ASCII");
    }

    private TextRomanizer() {
    }

    public static String toLatinAscii(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Holder.INSTANCE.transliterate(text);
    }
}
