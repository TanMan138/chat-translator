package com.tanman.chattranslator.client.translation;

public record TranslationResult(String translatedText, String originalText, boolean success) {

    public static TranslationResult success(String translatedText, String originalText) {
        return new TranslationResult(translatedText, originalText, true);
    }

    public static TranslationResult failure(String originalText) {
        return new TranslationResult(originalText, originalText, false);
    }
}
