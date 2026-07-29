package com.tanman.chattranslator.client.config;

public enum TranslationBackendType {
    ON_DEVICE("On-Device (OPUS-MT)"),
    MANAGED_CLOUD("Managed Cloud (BYOK)"),
    CUSTOM("Custom / Self-Hosted (Ollama)");

    private final String label;

    TranslationBackendType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
