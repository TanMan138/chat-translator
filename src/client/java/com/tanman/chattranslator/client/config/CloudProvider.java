package com.tanman.chattranslator.client.config;

public enum CloudProvider {
    DEEPL("DeepL"),
    GOOGLE("Google Translate"),
    LANGBLY("Langbly");

    private final String label;

    CloudProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
