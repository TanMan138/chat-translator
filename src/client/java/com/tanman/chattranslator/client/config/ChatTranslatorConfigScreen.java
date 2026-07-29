package com.tanman.chattranslator.client.config;

import com.tanman.chattranslator.client.ChatTranslatorServices;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * YACL config screen: backend tier, cloud keys, Ollama endpoint, outgoing prefs, cache.
 */
public final class ChatTranslatorConfigScreen {

    private ChatTranslatorConfigScreen() {
    }

    public static Screen create(Screen parent) {
        if (!ChatTranslatorServices.ready()) {
            return parent;
        }

        TranslatorConfig config = ChatTranslatorServices.config();
        TranslationState state = ChatTranslatorServices.state();
        config.normalize();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Chat Translator"))
                .save(() -> {
                    config.captureFrom(state);
                    config.save();
                })
                .category(buildBackendCategory(config))
                .category(buildCloudCategory(config))
                .category(buildCustomCategory(config))
                .category(buildOutgoingCategory(config, state))
                .category(buildOnDeviceCategory(config))
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory buildBackendCategory(TranslatorConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Backend"))
                .tooltip(Component.literal(
                        "Choose how chat is translated: local ONNX models, a cloud API you provide keys for, "
                                + "or a self-hosted Ollama instance."))
                .option(Option.<TranslationBackendType>createBuilder()
                        .name(Component.literal("Translation backend"))
                        .description(OptionDescription.of(Component.literal(
                                "On-Device: OPUS-MT/ONNX (default, offline after download).\n"
                                        + "Managed Cloud: DeepL or Google Translate v2 (BYOK).\n"
                                        + "Custom: Ollama /api/generate endpoint.")))
                        .binding(
                                TranslationBackendType.ON_DEVICE,
                                () -> config.backend,
                                value -> config.backend = value)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(TranslationBackendType.class)
                                .formatValue(value -> Component.literal(value.label())))
                        .build())
                .build();
    }

    private static ConfigCategory buildCloudCategory(TranslatorConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Managed Cloud"))
                .tooltip(Component.literal("DeepL or Google Cloud Translation API v2. Keys stored in config/chat-translator.json."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Provider"))
                        .option(Option.<CloudProvider>createBuilder()
                                .name(Component.literal("Cloud provider"))
                                .description(OptionDescription.of(Component.literal(
                                        "DeepL: form POST /v2/translate.\n"
                                                + "Google: Cloud Translation API v2 REST.")))
                                .binding(
                                        CloudProvider.DEEPL,
                                        () -> config.cloudProvider,
                                        value -> config.cloudProvider = value)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(CloudProvider.class)
                                        .formatValue(value -> Component.literal(value.label())))
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("DeepL"))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("DeepL API key"))
                                .description(OptionDescription.of(Component.literal(
                                        "Your DeepL API key. Free keys use api-free.deepl.com.")))
                                .binding("", () -> config.deeplApiKey, value -> config.deeplApiKey = value)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Use DeepL Free API"))
                                .description(OptionDescription.of(Component.literal(
                                        "When enabled, uses api-free.deepl.com. Disable for Pro keys.")))
                                .binding(true, () -> config.deeplUseFreeApi, value -> config.deeplUseFreeApi = value)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Google Translate"))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Google API key"))
                                .description(OptionDescription.of(Component.literal(
                                        "Google Cloud Translation API v2 key (covers Langbly-style BYOK).")))
                                .binding("", () -> config.googleApiKey, value -> config.googleApiKey = value)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .build())
                .build();
    }

    private static ConfigCategory buildCustomCategory(TranslatorConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Custom / Ollama"))
                .tooltip(Component.literal("Self-hosted Ollama instance for translation via /api/generate."))
                .option(Option.<String>createBuilder()
                        .name(Component.literal("Endpoint URL"))
                        .description(OptionDescription.of(Component.literal(
                                "Base URL, e.g. http://localhost:11434 or https://your-host:11434")))
                        .binding("", () -> config.customEndpointUrl, value -> config.customEndpointUrl = value)
                        .controller(StringControllerBuilder::create)
                        .build())
                .option(Option.<String>createBuilder()
                        .name(Component.literal("Ollama model"))
                        .description(OptionDescription.of(Component.literal(
                                "Model tag served by Ollama, e.g. qwen2.5:1.5b")))
                        .binding("qwen2.5:1.5b", () -> config.ollamaModel, value -> config.ollamaModel = value)
                        .controller(StringControllerBuilder::create)
                        .build())
                .build();
    }

    private static ConfigCategory buildOutgoingCategory(TranslatorConfig config, TranslationState state) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Outgoing"))
                .tooltip(Component.literal("How your typed English is translated before sending."))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Latin outgoing (romanized)"))
                        .description(OptionDescription.of(Component.literal(
                                "When enabled, outgoing translations are romanized to Latin ASCII "
                                        + "(AntiSpam-safe on English-only servers).")))
                        .binding(true, state::isLatinOutgoing, state::setLatinOutgoing)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Auto target language"))
                        .description(OptionDescription.of(Component.literal(
                                "Follow the last language detected from incoming hover-translate. "
                                        + "When off, use the manual code below.")))
                        .binding(true, state::isAuto, auto -> {
                            if (auto) {
                                state.setAuto();
                            } else {
                                String code = config.targetLanguage;
                                if (code == null || code.isBlank()) {
                                    code = state.getCurrentTargetLanguage().orElse("ru");
                                }
                                state.setManualTarget(code);
                            }
                        })
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<String>createBuilder()
                        .name(Component.literal("Manual target language code"))
                        .description(OptionDescription.of(Component.literal(
                                "ISO 639-1 code when auto is off, e.g. ru, fr, de.")))
                        .binding("", () -> {
                            if (state.isAuto()) {
                                return "";
                            }
                            return state.getCurrentTargetLanguage().orElse(
                                    config.targetLanguage == null ? "" : config.targetLanguage);
                        }, code -> {
                            if (!state.isAuto()) {
                                String normalized = code.trim().toLowerCase(Locale.ROOT);
                                if (normalized.matches("[a-z]{2,3}")) {
                                    state.setManualTarget(normalized);
                                }
                            }
                        })
                        .controller(StringControllerBuilder::create)
                        .build())
                .build();
    }

    private static ConfigCategory buildOnDeviceCategory(TranslatorConfig config) {
        ModelManager models = ChatTranslatorServices.modelManager();
        return ConfigCategory.createBuilder()
                .name(Component.literal("On-Device cache"))
                .tooltip(Component.literal(
                        "OPUS-MT models cached under .minecraft/chattranslator/models/"))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Clear all cached models"))
                        .description(OptionDescription.of(Component.literal(
                                "Deletes every downloaded OPUS-MT pair. Click twice to confirm.\n"
                                        + "Current cache: " + formatCacheStatus(models))))
                        .action(screen -> clearAllCachedModels(models))
                        .build())
                .build();
    }

    private static String formatCacheStatus(ModelManager models) {
        int folders = models.listPairKeys().size();
        return folders + " pair(s), " + models.formatTotalSize();
    }

    private static boolean clearArmed;

    private static void clearAllCachedModels(ModelManager models) {
        if (!clearArmed) {
            clearArmed = true;
            LocalNoticesHolder.show("Click Clear again to confirm deleting all cached models.");
            return;
        }
        clearArmed = false;
        Translator translator = ChatTranslatorServices.translator();
        List<String> keys = models.listPairKeys();
        int removed = 0;
        for (String key : keys) {
            String[] parts = key.split("-", 2);
            if (parts.length == 2) {
                translator.unload(models.modelDir(parts[0], parts[1]));
            }
            if (models.deletePair(key)) {
                removed++;
            }
        }
        translator.unloadAll();
        LocalNoticesHolder.show("Cleared " + removed + " cached model folder(s).");
    }

    /** Avoid importing LocalNotices into config package cycle — thin holder. */
    private static final class LocalNoticesHolder {
        private static void show(String text) {
            com.tanman.chattranslator.client.LocalNotices.show(text);
        }
    }
}
