package com.tanman.chattranslator.client.state;

import java.util.Optional;

public class TranslationState {

    private boolean auto = true;
    private String targetLanguage = null;

    public void onLanguageDetected(String langCode) {
        if (auto) {
            targetLanguage = langCode;
        }
    }

    public void setManualTarget(String langCode) {
        auto = false;
        targetLanguage = langCode;
    }

    public void setAuto() {
        auto = true;
        targetLanguage = null;
    }

    public boolean isAuto() {
        return auto;
    }

    public Optional<String> getCurrentTargetLanguage() {
        return Optional.ofNullable(targetLanguage);
    }
}
