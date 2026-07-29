package com.tanman.chattranslator.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tanman.chattranslator.client.ChatTranslatorServices;
import com.tanman.chattranslator.client.guide.GuideScreen;
import com.tanman.chattranslator.client.guide.PlayerGuide;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
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
                                        "Opening the Chat Translator guide…"));
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
                                            "Chat Translator is still loading."));
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
                                                    + "They download automatically when you hover-read chat "
                                                    + "(if you use \"On your computer\" mode)."));
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
                || lang.equals("models") || lang.equals("clear");
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
