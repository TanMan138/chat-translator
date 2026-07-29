package com.tanman.chattranslator.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Local-only HUD notices (never sent to the server).
 */
public final class LocalNotices {

    private LocalNotices() {
    }

    public static void show(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            if (minecraft.gui != null) {
                minecraft.gui.getChat().addClientSystemMessage(
                        Component.literal("[Chat Translator] " + text)
                                .withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
