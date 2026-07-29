package com.tanman.chattranslator.client.config;

public enum TranslationBackendType {
    ON_DEVICE("On your computer (recommended)"),
    MANAGED_CLOUD("Online service (your API key)"),
    CUSTOM("Your own server (Ollama)");

    private final String label;

    TranslationBackendType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
