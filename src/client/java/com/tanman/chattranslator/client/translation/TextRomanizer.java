package com.tanman.chattranslator.client.translation;

import com.ibm.icu.text.Transliterator;

import java.util.Locale;

/**
 * Turns translated text into Latin letters.
 *
 * <p>This is <em>romanization</em>, not translation: {@code Привет} becomes something
 * like {@code Privet}, not {@code Hello}. Useful on servers whose AntiSpam blocks
 * Cyrillic / CJK / etc. as "forbidden symbols".
 *
 * <p>Russian and Ukrainian use compact chat-style mappings. Other languages use ICU's
 * broad script transliterator.
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

    public static String toLatinAscii(String text, String languageCode) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String language = normalizeLanguage(languageCode);
        if ("ru".equals(language) || "uk".equals(language)) {
            String chatStyle = transliterateCyrillic(text, "uk".equals(language));
            String romanized = containsNonAscii(chatStyle)
                    ? Holder.INSTANCE.transliterate(chatStyle)
                    : chatStyle;
            return foldToAscii(romanized);
        }
        return foldToAscii(Holder.INSTANCE.transliterate(text));
    }

    private static String normalizeLanguage(String languageCode) {
        if (languageCode == null) {
            return "";
        }
        String normalized = languageCode.trim().toLowerCase(Locale.ROOT);
        int separator = Math.min(
                indexOrLength(normalized, '-'),
                indexOrLength(normalized, '_'));
        return normalized.substring(0, separator);
    }

    private static int indexOrLength(String value, char needle) {
        int index = value.indexOf(needle);
        return index < 0 ? value.length() : index;
    }

    private static String transliterateCyrillic(String text, boolean ukrainian) {
        StringBuilder result = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char original = text.charAt(i);
            char lower = Character.toLowerCase(original);
            String replacement = lower == 'е'
                    && i > 0
                    && Character.toLowerCase(text.charAt(i - 1)) == 'ь'
                    ? "ye"
                    : mapping(lower, ukrainian);
            if (replacement == null) {
                result.append(original);
            } else if (Character.isUpperCase(original) && !replacement.isEmpty()) {
                result.append(Character.toUpperCase(replacement.charAt(0)))
                        .append(replacement, 1, replacement.length());
            } else {
                result.append(replacement);
            }
        }
        return result.toString();
    }

    private static String mapping(char letter, boolean ukrainian) {
        return switch (letter) {
            case 'а' -> "a";
            case 'б' -> "b";
            case 'в' -> "v";
            case 'г' -> ukrainian ? "h" : "g";
            case 'ґ' -> "g";
            case 'д' -> "d";
            case 'е' -> "e";
            case 'ё' -> "yo";
            case 'є' -> "ye";
            case 'ж' -> "zh";
            case 'з' -> "z";
            case 'и' -> ukrainian ? "y" : "i";
            case 'і' -> "i";
            case 'ї' -> "yi";
            case 'й' -> "y";
            case 'к' -> "k";
            case 'л' -> "l";
            case 'м' -> "m";
            case 'н' -> "n";
            case 'о' -> "o";
            case 'п' -> "p";
            case 'р' -> "r";
            case 'с' -> "s";
            case 'т' -> "t";
            case 'у' -> "u";
            case 'ф' -> "f";
            case 'х' -> "kh";
            case 'ц' -> "ts";
            case 'ч' -> "ch";
            case 'ш' -> "sh";
            case 'щ' -> "sch";
            case 'ъ', 'ь' -> "";
            case 'ы' -> "y";
            case 'э' -> "e";
            case 'ю' -> "yu";
            case 'я' -> "ya";
            default -> null;
        };
    }

    private static boolean containsNonAscii(String text) {
        return text.codePoints().anyMatch(codePoint -> codePoint > 0x7F);
    }

    private static String foldToAscii(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (codePoint <= 0x7F) {
                result.appendCodePoint(codePoint);
                return;
            }
            if (Character.isSpaceChar(codePoint)) {
                result.append(' ');
                return;
            }
            switch (codePoint) {
                case 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2015, 0x2212 ->
                        result.append('-');
                case 0x2018, 0x2019, 0x201A, 0x201B, 0x2032 -> result.append('\'');
                case 0x201C, 0x201D, 0x201E, 0x201F, 0x00AB, 0x00BB, 0x2033 ->
                        result.append('"');
                case 0x2026 -> result.append("...");
                case 0x2022 -> result.append('*');
                default -> {
                    // Latin mode is intentionally ASCII-safe for strict server filters.
                    // ICU handles letters; unsupported symbols such as emoji are omitted.
                }
            }
        });
        return result.toString();
    }
}
