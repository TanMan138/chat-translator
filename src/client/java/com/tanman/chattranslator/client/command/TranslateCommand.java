package com.tanman.chattranslator.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tanman.chattranslator.client.state.TranslationState;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

public class TranslateCommand {

    public static void register(TranslationState state) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("translate")
                    .then(ClientCommands.literal("auto")
                            .executes(ctx -> {
                                state.setAuto();
                                ctx.getSource().sendFeedback(Component.literal("Translation target set to auto."));
                                return 1;
                            }))
                    .then(ClientCommands.literal("status")
                            .executes(ctx -> {
                                String status = state.isAuto()
                                        ? "auto (" + state.getCurrentTargetLanguage().orElse("none detected yet") + ")"
                                        : "manual (" + state.getCurrentTargetLanguage().orElse("none") + ")";
                                ctx.getSource().sendFeedback(Component.literal("Translation mode: " + status));
                                return 1;
                            }))
                    .then(ClientCommands.argument("langcode", StringArgumentType.word())
                            .executes(ctx -> {
                                String lang = StringArgumentType.getString(ctx, "langcode");
                                state.setManualTarget(lang);
                                ctx.getSource().sendFeedback(Component.literal("Translation target manually set to " + lang + "."));
                                return 1;
                            })));
        });
    }
}
