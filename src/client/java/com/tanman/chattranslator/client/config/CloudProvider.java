package com.tanman.chattranslator.client.config;

public enum CloudProvider {
    DEEPL("DeepL"),
    GOOGLE("Google Translate (also Langbly-style keys)");

    private final String label;

    CloudProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
