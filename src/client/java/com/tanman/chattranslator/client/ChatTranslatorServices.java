package com.tanman.chattranslator.client;

import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import com.tanman.chattranslator.client.translation.backend.TranslationService;

/**
 * Shared client services for commands, chat handlers, and optional Mod Menu UI.
 */
public final class ChatTranslatorServices {

    private static TranslationState state;
    private static ModelManager modelManager;
    private static Translator translator;
    private static TranslatorConfig config;
    private static TranslationService translationService;

    private ChatTranslatorServices() {
    }

    public static void init(
            TranslationState state,
            ModelManager modelManager,
            Translator translator,
            TranslatorConfig config,
            TranslationService translationService
    ) {
        ChatTranslatorServices.state = state;
        ChatTranslatorServices.modelManager = modelManager;
        ChatTranslatorServices.translator = translator;
        ChatTranslatorServices.config = config;
        ChatTranslatorServices.translationService = translationService;
    }

    public static boolean ready() {
        return state != null && modelManager != null && translator != null && translationService != null;
    }

    public static TranslationState state() {
        return state;
    }

    public static ModelManager modelManager() {
        return modelManager;
    }

    public static Translator translator() {
        return translator;
    }

    public static TranslatorConfig config() {
        return config;
    }

    public static TranslationService translationService() {
        return translationService;
    }

    public static void persistState() {
        if (config == null || state == null) {
            return;
        }
        config.captureFrom(state);
        config.save();
    }
}
