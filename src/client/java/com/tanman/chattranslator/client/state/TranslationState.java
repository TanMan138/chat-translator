package com.tanman.chattranslator.client.state;

import java.util.Optional;

public class TranslationState {

    private boolean auto = true;
    private String targetLanguage = null;

    /**
     * When true, outgoing translations are romanized to Latin ASCII (e.g. Cyrillic
     * {@code Привет} → {@code Privet}) so English-only / AntiSpam servers accept them.
     * Default on — public networks often block non-Latin scripts.
     */
    private boolean latinOutgoing = true;

    private Runnable onUserChanged = () -> {
    };

    public void setOnUserChanged(Runnable onUserChanged) {
        this.onUserChanged = onUserChanged != null ? onUserChanged : () -> {
        };
    }

    /** Apply saved preferences without firing persistence callbacks. */
    public void restore(boolean auto, String targetLanguage, boolean latinOutgoing) {
        this.auto = auto;
        this.targetLanguage = auto ? null : targetLanguage;
        this.latinOutgoing = latinOutgoing;
    }

    public void onLanguageDetected(String langCode) {
        if (auto) {
            targetLanguage = langCode;
        }
    }

    public void setManualTarget(String langCode) {
        auto = false;
        targetLanguage = langCode;
        notifyUserChanged();
    }

    public void setAuto() {
        auto = true;
        targetLanguage = null;
        notifyUserChanged();
    }

    public boolean isAuto() {
        return auto;
    }

    public Optional<String> getCurrentTargetLanguage() {
        return Optional.ofNullable(targetLanguage);
    }

    public boolean isLatinOutgoing() {
        return latinOutgoing;
    }

    public void setLatinOutgoing(boolean latinOutgoing) {
        this.latinOutgoing = latinOutgoing;
        notifyUserChanged();
    }

    private void notifyUserChanged() {
        onUserChanged.run();
    }
}
