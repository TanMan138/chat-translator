package com.tanman.chattranslator.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class TranslateCommand {

    private static final String LANG_GUIDE = """
            Common language codes (ISO 639-1):
              fr French   de German    es Spanish   ru Russian
              ja Japanese zh Chinese   ko Korean    pt Portuguese
              it Italian  nl Dutch     pl Polish    uk Ukrainian
              ar Arabic   tr Turkish   sv Swedish   cs Czech
              fi Finnish  hu Hungarian ro Romanian  el Greek
              hi Hindi    id Indonesian vi Vietnamese th Thai
            /translate <code>       — lock outgoing language
            /translate latin        — outgoing as Latin letters (default)
                                      e.g. Привет → Privet — AntiSpam-safe
            /translate native       — outgoing real script (may be blocked)
            /translate models       — list cached models + sizes
            /translate clear <x>    — delete cache (ru | en-ru | all)
            Protect words: wrap in {{double braces}} e.g. hi {{Steve}}
            Incoming: open chat, hover a line — English on tooltip.
            GUI: Mods → Chat Translator → Config (needs Mod Menu).""";

    public static void register(
            TranslationState state,
            ModelManager modelManager,
            Translator translator
    ) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("translate")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(LANG_GUIDE));
                        return 1;
                    })
                    .then(ClientCommands.literal("help")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(LANG_GUIDE));
                                return 1;
                            }))
                    .then(ClientCommands.literal("latin")
                            .executes(ctx -> {
                                state.setLatinOutgoing(true);
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Outgoing script: LATIN (romanized).\n"
                                                + "What this means: after English→target "
                                                + "translation, letters are converted to "
                                                + "Latin ASCII. Example: Привет → Privet.\n"
                                                + "Why: many public / AntiSpam servers "
                                                + "(e.g. Mineberry) reject Cyrillic, CJK, "
                                                + "Arabic, etc. as \"forbidden symbols\".\n"
                                                + "This is the default — safest for public chat."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("native")
                            .executes(ctx -> {
                                state.setLatinOutgoing(false);
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Outgoing script: NATIVE.\n"
                                                + "What this means: send the real target "
                                                + "script (Cyrillic, Japanese, Arabic, …).\n"
                                                + "Looks authentic, but English-only servers "
                                                + "often block it. Switch back with "
                                                + "/translate latin if messages fail."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("auto")
                            .executes(ctx -> {
                                state.setAuto();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Translation target set to auto."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("status")
                            .executes(ctx -> {
                                String status = state.isAuto()
                                        ? "auto (" + state.getCurrentTargetLanguage()
                                        .orElse("none detected yet") + ")"
                                        : "manual (" + state.getCurrentTargetLanguage()
                                        .orElse("none") + ")";
                                String script = state.isLatinOutgoing() ? "latin" : "native";
                                ctx.getSource().sendFeedback(Component.literal(
                                        "Translation mode: " + status
                                                + " | script: " + script
                                                + " | cache: " + modelManager.formatTotalSize()
                                                + " | /translate models"));
                                return 1;
                            }))
                    .then(ClientCommands.literal("models")
                            .executes(ctx -> {
                                List<String> keys = modelManager.listPairKeys();
                                if (keys.isEmpty()) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "No models cached. Path: "
                                                    + modelManager.baseDir()));
                                    return 1;
                                }
                                StringBuilder sb = new StringBuilder("Cached models (")
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
                                        "Translation target manually set to " + lang
                                                + ". Script mode: "
                                                + (state.isLatinOutgoing() ? "latin" : "native")
                                                + "."));
                                return 1;
                            })));
        });
    }

    private static boolean isReserved(String lang) {
        return lang.equals("latin") || lang.equals("native")
                || lang.equals("auto") || lang.equals("help")
                || lang.equals("status") || lang.equals("models")
                || lang.equals("clear");
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
                "Cleared " + removed + " model folder(s). Cache now: "
                        + modelManager.formatTotalSize() + "."));
        return removed > 0 ? 1 : 0;
    }
}
