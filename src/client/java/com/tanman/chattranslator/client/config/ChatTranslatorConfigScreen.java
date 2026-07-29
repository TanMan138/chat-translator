package com.tanman.chattranslator.client.config;

import com.tanman.chattranslator.client.ChatTranslatorServices;
import com.tanman.chattranslator.client.guide.GuideScreen;
import com.tanman.chattranslator.client.guide.PlayerGuide;
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
 * YACL config screen with beginner-friendly labels and a link to the full guide.
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
                .category(buildStartHereCategory(parent))
                .category(buildHowToTranslateCategory(config))
                .category(buildOnlineServicesCategory(config))
                .category(buildYourServerCategory(config))
                .category(buildWhatYouSendCategory(config, state))
                .category(buildSavedDownloadsCategory())
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory buildStartHereCategory(Screen parent) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Start here"))
                .tooltip(Component.literal(
                        "New to this mod? Read this first, then open the full step-by-step guide."))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Open full guide"))
                        .description(OptionDescription.of(Component.literal(
                                PlayerGuide.START_HERE_BLURB + "\n\n"
                                        + "Click to open the step-by-step guide with pros/cons "
                                        + "of each method and a command list.")))
                        .action(screen -> GuideScreen.open(screen))
                        .build())
                .build();
    }

    private static ConfigCategory buildHowToTranslateCategory(TranslatorConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("How to translate"))
                .tooltip(Component.literal(
                        "Choose where translation happens. Most players should use "
                                + "\"On your computer\" — no account needed."))
                .option(Option.<TranslationBackendType>createBuilder()
                        .name(Component.literal("Translation method"))
                        .description(OptionDescription.of(Component.literal(
                                "On your computer: downloads small language files once, then works offline.\n"
                                        + "Online service: you paste an API key from DeepL or Google.\n"
                                        + "Your own server: for players who run Ollama on a PC or VPS.")))
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

    private static ConfigCategory buildOnlineServicesCategory(TranslatorConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Online services"))
                .tooltip(Component.literal(
                        "Only needed if you picked \"Online service\" above. "
                                + "You must create an account and copy your API key here."))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Which service?"))
                        .option(Option.<CloudProvider>createBuilder()
                                .name(Component.literal("Pick your service"))
                                .description(OptionDescription.of(Component.literal(
                                        "DeepL: sign up at deepl.com and copy your API key.\n"
                                                + "Google Translate: needs a Google Cloud key (starts with AIza).\n"
                                                + "Langbly: sign up at langbly.com, free tier available. "
                                                + "A Langbly key only works with Langbly selected here — "
                                                + "it will be rejected if you pick Google.")))
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
                        .name(Component.literal("DeepL setup"))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("DeepL API key"))
                                .description(OptionDescription.of(Component.literal(
                                        "Step 1: Create a free or paid account at deepl.com.\n"
                                                + "Step 2: Copy your API key from their website.\n"
                                                + "Step 3: Paste it here. Leave \"free API\" on for free keys.")))
                                .binding("", () -> config.deeplApiKey, value -> config.deeplApiKey = value)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("I have a free DeepL key"))
                                .description(OptionDescription.of(Component.literal(
                                        "Turn this on if you signed up for DeepL's free plan. "
                                                + "Turn off if you pay for DeepL Pro.")))
                                .binding(true, () -> config.deeplUseFreeApi, value -> config.deeplUseFreeApi = value)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Google setup"))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Google API key"))
                                .description(OptionDescription.of(Component.literal(
                                        "Step 1: Get a Google Cloud Translation API key "
                                                + "(it starts with AIza).\n"
                                                + "Step 2: Paste it here.\n"
                                                + "Needs internet every time you translate.\n"
                                                + "Have a Langbly key instead? Pick Langbly above — "
                                                + "Langbly keys do not work here.")))
                                .binding("", () -> config.googleApiKey, value -> config.googleApiKey = value)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Langbly setup"))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Langbly API key"))
                                .description(OptionDescription.of(Component.literal(
                                        "Step 1: Sign up at langbly.com and create a key in the dashboard.\n"
                                                + "Step 2: Paste it here and pick Langbly above.\n"
                                                + "Free tier included; needs internet every time you translate.")))
                                .binding("", () -> config.langblyApiKey, value -> config.langblyApiKey = value)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Keep my data in the EU"))
                                .description(OptionDescription.of(Component.literal(
                                        "On: sends your chat to Langbly's EU-only servers "
                                                + "(eu.langbly.com) for EU data residency.\n"
                                                + "Off (default): uses the global server (api.langbly.com).")))
                                .binding(
                                        false,
                                        () -> config.langblyUseEuEndpoint,
                                        value -> config.langblyUseEuEndpoint = value)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .build();
    }

    private static ConfigCategory buildYourServerCategory(TranslatorConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Your own server"))
                .tooltip(Component.literal(
                        "Only needed if you picked \"Your own server\" above and run Ollama yourself."))
                .option(Option.<String>createBuilder()
                        .name(Component.literal("Server address"))
                        .description(OptionDescription.of(Component.literal(
                                "The web address where Ollama runs.\n"
                                        + "On the same PC: http://localhost:11434\n"
                                        + "On another machine: http://YOUR-IP:11434")))
                        .binding("", () -> config.customEndpointUrl, value -> config.customEndpointUrl = value)
                        .controller(StringControllerBuilder::create)
                        .build())
                .option(Option.<String>createBuilder()
                        .name(Component.literal("Model name"))
                        .description(OptionDescription.of(Component.literal(
                                "The model tag Ollama uses. Default qwen2.5:1.5b works well. "
                                        + "Must match a model you already pulled in Ollama.")))
                        .binding("qwen2.5:1.5b", () -> config.ollamaModel, value -> config.ollamaModel = value)
                        .controller(StringControllerBuilder::create)
                        .build())
                .build();
    }

    private static ConfigCategory buildWhatYouSendCategory(TranslatorConfig config, TranslationState state) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("What you send"))
                .tooltip(Component.literal(
                        "Controls outgoing chat only — what happens when YOU type and press Enter."))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Use Latin letters when sending"))
                        .description(OptionDescription.of(Component.literal(
                                "On (recommended): sends Privet instead of Привет so public servers "
                                        + "with AntiSpam do not block your message.\n"
                                        + "Off: sends real foreign letters — looks authentic but may fail.")))
                        .binding(true, state::isLatinOutgoing, state::setLatinOutgoing)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Auto-pick reply language"))
                        .description(OptionDescription.of(Component.literal(
                                "On: after you hover-read someone's chat, replies use their language.\n"
                                        + "Off: always use the language code you type below.")))
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
                        .name(Component.literal("Locked language code"))
                        .description(OptionDescription.of(Component.literal(
                                "Only when auto is off. Two-letter codes: ru Russian, fr French, "
                                        + "de German, es Spanish, ja Japanese. Or use /translate <code>.")))
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

    private static ConfigCategory buildSavedDownloadsCategory() {
        ModelManager models = ChatTranslatorServices.modelManager();
        return ConfigCategory.createBuilder()
                .name(Component.literal("Saved downloads"))
                .tooltip(Component.literal(
                        "Language files saved on your computer when using \"On your computer\" mode."))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Delete all saved language files"))
                        .description(OptionDescription.of(Component.literal(
                                "Frees disk space. You will re-download next time you need a language.\n"
                                        + "Click twice to confirm. Current: " + formatCacheStatus(models))))
                        .action(screen -> clearAllCachedModels(models))
                        .build())
                .build();
    }

    private static String formatCacheStatus(ModelManager models) {
        int folders = models.listPairKeys().size();
        return folders + " language pack(s), " + models.formatTotalSize();
    }

    private static boolean clearArmed;

    private static void clearAllCachedModels(ModelManager models) {
        if (!clearArmed) {
            clearArmed = true;
            LocalNoticesHolder.show("Click Delete again to confirm.");
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
        LocalNoticesHolder.show("Deleted " + removed + " saved language pack(s).");
    }

    private static final class LocalNoticesHolder {
        private static void show(String text) {
            com.tanman.chattranslator.client.LocalNotices.show(text);
        }
    }
}
