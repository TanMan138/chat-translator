package com.tanman.chattranslator.client.mixin;

import com.tanman.chattranslator.client.event.IncomingChatHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles our {@code chat-translator:translate} click locally, and starts
 * translation when the player hovers a prepared line (tooltip path).
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    private ChatComponent.DisplayMode displayMode;

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void chatTranslator$handleTranslateClick(
            Style style, boolean rightClick, CallbackInfoReturnable<Boolean> cir) {
        if (style == null || style.getClickEvent() == null) {
            return;
        }
        if (IncomingChatHandler.requestTranslate(style.getClickEvent())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * While chat is open, hovering a prepared line kicks off download/translate
     * so the tooltip can update to English once ready (after rescale).
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void chatTranslator$hoverTranslate(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(minecraft.font, mouseX, mouseY);
        minecraft.gui.getChat().captureClickableText(
                finder,
                minecraft.getWindow().getGuiScaledHeight(),
                minecraft.gui.getGuiTicks(),
                displayMode);
        Style style = finder.result();
        if (style != null && style.getClickEvent() != null) {
            IncomingChatHandler.requestTranslate(style.getClickEvent());
        }
    }
}
