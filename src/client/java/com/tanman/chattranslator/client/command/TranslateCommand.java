package com.tanman.chattranslator.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tanman.chattranslator.client.ChatTranslatorServices;
import com.tanman.chattranslator.client.config.CloudProvider;
import com.tanman.chattranslator.client.config.TranslationBackendType;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.guide.GuideScreen;
import com.tanman.chattranslator.client.guide.PlayerGuide;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import com.tanman.chattranslator.client.translation.UsageTracker;
import com.tanman.chattranslator.client.translation.backend.TranslationService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Locale;

public class TranslateCommand {

    public static void register(
            TranslationState state,
            ModelManager modelManager,
            Translator translator
    ) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("translate")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(PlayerGuide.HELP_CHEAT_SHEET));
                        return 1;
                    })
                    .then(ClientCommands.literal("help")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(PlayerGuide.HELP_CHEAT_SHEET));
                                return 1;
                            }))
                    .then(ClientCommands.literal("guide")
                            .executes(ctx -> {
                                openGuide(ctx.getSource());
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Opening the Lingo guide…"));
                                return 1;
                            }))
                    .then(ClientCommands.literal("latin")
                            .executes(ctx -> {
                                state.setLatinOutgoing(true);
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Outgoing style: Latin letters (default).\n"
                                                + "Your translated messages use normal English letters "
                                                + "(e.g. Privet instead of Привет).\n"
                                                + "Best for public servers that block foreign scripts."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("native")
                            .executes(ctx -> {
                                state.setLatinOutgoing(false);
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Outgoing style: real foreign letters.\n"
                                                + "Messages look authentic but some servers block them.\n"
                                                + "If chat fails, switch back with /translate latin."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("read")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Reading mode: " + readModeLabel() + ".\n"
                                                + "/translate read auto — English is added to foreign "
                                                + "lines automatically\n"
                                                + "/translate read hover — only when you hover a line"));
                                return 1;
                            })
                            .then(ClientCommands.literal("auto")
                                    .executes(ctx -> setReadMode(ctx.getSource(), true)))
                            .then(ClientCommands.literal("hover")
                                    .executes(ctx -> setReadMode(ctx.getSource(), false))))
                    .then(ClientCommands.literal("usage")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(usageReport()));
                                return 1;
                            }))
                    .then(ClientCommands.literal("backend")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(backendHelp()));
                                return 1;
                            })
                            .then(ClientCommands.argument("name", StringArgumentType.word())
                                    .executes(ctx -> setBackend(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "name")
                                                    .toLowerCase(Locale.ROOT)))))
                    .then(ClientCommands.literal("download")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Usage: /translate download <langcode> — saves that "
                                                + "language now so chat translates instantly later."));
                                return 1;
                            })
                            .then(ClientCommands.argument("langcode", StringArgumentType.word())
                                    .executes(ctx -> download(
                                            ctx.getSource(),
                                            modelManager,
                                            StringArgumentType.getString(ctx, "langcode")
                                                    .toLowerCase(Locale.ROOT)))))
                    .then(ClientCommands.literal("auto")
                            .executes(ctx -> {
                                state.setAuto();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Outgoing language: auto.\n"
                                                + "Replies will follow the last language you read in chat."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("status")
                            .executes(ctx -> {
                                if (!ChatTranslatorServices.ready()) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Lingo is still loading."));
                                    return 1;
                                }
                                ctx.getSource().sendFeedback(Component.literal(
                                        PlayerGuide.formatStatus(
                                                state,
                                                modelManager,
                                                ChatTranslatorServices.config())));
                                return 1;
                            }))
                    .then(ClientCommands.literal("models")
                            .executes(ctx -> {
                                List<String> keys = modelManager.listPairKeys();
                                if (keys.isEmpty()) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "No language files saved yet.\n"
                                                    + "Hover a foreign chat line, or run "
                                                    + "/translate download <code>, to save one "
                                                    + "(\"On your computer\" mode only)."));
                                    return 1;
                                }
                                StringBuilder sb = new StringBuilder("Saved language files (")
                                        .append(modelManager.formatTotalSize())
                                        .append("):\n");
                                for (String key : keys) {
                                    sb.append("  ")
                                            .append(key)
                                            .append(" — ")
                                            .append(modelManager.formatPairSize(key))
                                            .append('\n');
                                }
                                sb.append("Delete: /translate clear <pair|lang|all>");
                                ctx.getSource().sendFeedback(Component.literal(sb.toString()));
                                return 1;
                            }))
                    .then(ClientCommands.literal("clear")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Usage: /translate clear <en-ru|ru|all>"));
                                return 1;
                            })
                            .then(ClientCommands.argument("target", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String target = StringArgumentType
                                                .getString(ctx, "target")
                                                .toLowerCase(Locale.ROOT);
                                        return clearModels(
                                                ctx.getSource()::sendFeedback,
                                                modelManager,
                                                translator,
                                                target);
                                    })))
                    .then(ClientCommands.argument("langcode", StringArgumentType.word())
                            .executes(ctx -> {
                                String lang = StringArgumentType.getString(ctx, "langcode")
                                        .toLowerCase();
                                if (isReserved(lang)) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Use /translate " + lang + " as a subcommand."));
                                    return 0;
                                }
                                state.setManualTarget(lang);
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Outgoing language locked to: " + lang + ".\n"
                                                + "Type in English — the mod translates before sending.\n"
                                                + "Style: "
                                                + (state.isLatinOutgoing()
                                                ? "Latin letters (/translate native for real script)"
                                                : "real foreign letters")));
                                return 1;
                            })));
        });
    }

    private static void openGuide(FabricClientCommandSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            GuideScreen.open(minecraft.screen);
        }
    }

    private static boolean isReserved(String lang) {
        return lang.equals("latin") || lang.equals("native")
                || lang.equals("auto") || lang.equals("help")
                || lang.equals("guide") || lang.equals("status")
                || lang.equals("models") || lang.equals("clear")
                || lang.equals("read") || lang.equals("backend")
                || lang.equals("download") || lang.equals("usage");
    }

    private static String usageReport() {
        if (!ChatTranslatorServices.ready()) {
            return "Lingo is still loading.";
        }
        TranslatorConfig config = ChatTranslatorServices.config();
        UsageTracker usage = ChatTranslatorServices.translationService().usage();
        String month = TranslationService.currentMonth();
        Map<String, Long> counts = usage.snapshot();

        StringBuilder sb = new StringBuilder("Online translation used this month (")
                .append(month).append("):\n");
        if (counts.isEmpty()) {
            sb.append("  nothing — you are translating on your computer.\n");
        } else {
            counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sb.append("  ")
                            .append(entry.getKey())
                            .append(": ~")
                            .append(entry.getValue())
                            .append(" characters\n"));
        }
        sb.append(config.monthlyCharacterBudget > 0
                ? "Budget: " + config.monthlyCharacterBudget
                + " characters, then it switches to your computer."
                : "Budget: off — the mod will not stop you going over.");
        sb.append("\nThis is an estimate of what this mod sent. It cannot see the same "
                + "key used elsewhere, and your provider's billing month may differ. "
                + "Check your provider's dashboard for the real number.");
        return sb.toString();
    }

    private static String readModeLabel() {
        if (!ChatTranslatorServices.ready()) {
            return "still loading";
        }
        return ChatTranslatorServices.config().autoTranslateIncoming ? "auto" : "hover";
    }

    private static int setReadMode(FabricClientCommandSource source, boolean auto) {
        if (!ChatTranslatorServices.ready()) {
            source.sendFeedback(Component.literal("Lingo is still loading."));
            return 0;
        }
        TranslatorConfig config = ChatTranslatorServices.config();
        config.autoTranslateIncoming = auto;
        config.save();
        source.sendFeedback(Component.literal(auto
                ? "Reading mode: auto.\n"
                + "Foreign chat now shows English on the same line.\n"
                + "Languages that are not saved yet still need one hover to download."
                : "Reading mode: hover.\n"
                + "Chat is left alone until you hover a line."));
        return 1;
    }

    private static String backendHelp() {
        StringBuilder sb = new StringBuilder("Translation method: ");
        sb.append(ChatTranslatorServices.ready()
                        ? PlayerGuide.backendLabel(ChatTranslatorServices.config())
                        : "still loading")
                .append("\nChange with /translate backend <name>:\n");
        for (String name : BACKEND_NAMES) {
            sb.append("  ").append(name).append('\n');
        }
        sb.append("API keys and the Ollama address live in config/chat-translator.json "
                + "(or Mods → Lingo → Config with Mod Menu + YACL).");
        return sb.toString();
    }

    private static final List<String> BACKEND_NAMES =
            List.of("ondevice", "deepl", "google", "langbly", "ollama");

    private static int setBackend(FabricClientCommandSource source, String name) {
        if (!ChatTranslatorServices.ready()) {
            source.sendFeedback(Component.literal("Lingo is still loading."));
            return 0;
        }
        TranslatorConfig config = ChatTranslatorServices.config();
        switch (name) {
            case "ondevice", "local", "computer" -> config.backend = TranslationBackendType.ON_DEVICE;
            case "deepl" -> {
                config.backend = TranslationBackendType.MANAGED_CLOUD;
                config.cloudProvider = CloudProvider.DEEPL;
            }
            case "google" -> {
                config.backend = TranslationBackendType.MANAGED_CLOUD;
                config.cloudProvider = CloudProvider.GOOGLE;
            }
            case "langbly" -> {
                config.backend = TranslationBackendType.MANAGED_CLOUD;
                config.cloudProvider = CloudProvider.LANGBLY;
            }
            case "ollama", "custom", "server" -> config.backend = TranslationBackendType.CUSTOM;
            default -> {
                source.sendFeedback(Component.literal(
                        "Unknown method \"" + name + "\". Options: "
                                + String.join(", ", BACKEND_NAMES)));
                return 0;
            }
        }
        config.save();
        ChatTranslatorServices.translationService().clearCache();
        source.sendFeedback(Component.literal(
                "Translation method: " + PlayerGuide.backendLabel(config) + "."));
        return 1;
    }

    /**
     * Fetches both directions for a language up front, so the first foreign line in
     * chat translates immediately instead of waiting on a ~100 MB download.
     */
    private static int download(
            FabricClientCommandSource source, ModelManager modelManager, String lang) {
        if (!ChatTranslatorServices.ready()) {
            source.sendFeedback(Component.literal("Lingo is still loading."));
            return 0;
        }
        if (!lang.matches("[a-z]{2,3}")) {
            source.sendFeedback(Component.literal(
                    "That doesn't look like a language code. Try /translate download ru"));
            return 0;
        }

        int started = 0;
        for (String[] pair : new String[][] {{lang, "en"}, {"en", lang}}) {
            if (modelManager.isCached(pair[0], pair[1])) {
                source.sendFeedback(Component.literal(
                        pair[0] + "-" + pair[1] + " is already saved."));
                continue;
            }
            if (!ChatTranslatorServices.translationService().prewarm(pair[0], pair[1])) {
                source.sendFeedback(Component.literal(
                        "Your translation method is online, so nothing needs downloading."));
                return 1;
            }
            started++;
        }
        if (started > 0) {
            source.sendFeedback(Component.literal(
                    "Downloading " + lang + " in the background — progress shows in chat. "
                            + "You can keep playing."));
        }
        return 1;
    }

    private static int clearModels(
            java.util.function.Consumer<Component> feedback,
            ModelManager modelManager,
            Translator translator,
            String target
    ) {
        List<String> keys = modelManager.resolveClearTargets(target);
        if (keys.isEmpty()) {
            feedback.accept(Component.literal(
                    "Nothing to delete for \"" + target
                            + "\". Try /translate models, or clear en-ru / ru / all."));
            return 0;
        }

        int removed = 0;
        for (String key : keys) {
            String[] parts = key.split("-", 2);
            if (parts.length == 2) {
                translator.unload(modelManager.modelDir(parts[0], parts[1]));
            }
            if (modelManager.deletePair(key)) {
                removed++;
                feedback.accept(Component.literal("Deleted " + key + "."));
            }
        }
        if (target.equals("all")) {
            translator.unloadAll();
        }
        feedback.accept(Component.literal(
                "Removed " + removed + " language pack(s). Saved space: "
                        + modelManager.formatTotalSize() + "."));
        return removed > 0 ? 1 : 0;
    }
}
