package com.tanman.chattranslator.client.mixin;

import com.tanman.chattranslator.client.event.IncomingChatHandler;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Chat bakes {@link Style} into line buffers when a message is added. Mutating the
 * original component's hover afterward does nothing. For our translate click-ids,
 * resolve the tooltip from live pending state instead.
 */
@Mixin(Style.class)
public class StyleMixin {

    @Inject(method = "getHoverEvent", at = @At("RETURN"), cancellable = true)
    private void chatTranslator$liveTranslateTooltip(CallbackInfoReturnable<HoverEvent> cir) {
        Style self = (Style) (Object) this;
        ClickEvent click = self.getClickEvent();
        Optional<String> live = IncomingChatHandler.liveTooltip(click);
        if (live.isPresent()) {
            cir.setReturnValue(new HoverEvent.ShowText(Component.literal(live.get())));
        }
    }
}
