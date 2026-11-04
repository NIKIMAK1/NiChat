package com.nichat.mixin.client;

import com.nichat.Animation;
import com.nichat.ChatScreenAnimation;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EditBox.class)
public class EditBoxMixin {

    @Unique
    private int nichat_fade(int color) {
        if ((Object) this != ChatScreenAnimation.getFadingInput()) return color;
        return Animation.scaleColorAlpha(color, ChatScreenAnimation.getInputOpacity());
    }

    @ModifyArg(
            method = "extractWidgetRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"
            ),
            index = 4,
            require = 2,
            allow = 2
    )
    private int nichat_fadeText(int color) {
        return nichat_fade(color);
    }

    @ModifyArg(
            method = "extractWidgetRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            ),
            index = 4
    )
    private int nichat_fadeHint(int color) {
        return nichat_fade(color);
    }

    @ModifyArg(
            method = "extractWidgetRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"
            ),
            index = 4
    )
    private int nichat_fadeSuggestion(int color) {
        return nichat_fade(color);
    }

    @ModifyArg(
            method = "extractWidgetRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"
            ),
            index = 3
    )
    private int nichat_fadeInsertCursor(int color) {
        return nichat_fade(color);
    }

    @ModifyArg(
            method = "extractWidgetRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"
            ),
            index = 4
    )
    private int nichat_fadeAppendCursor(int color) {
        return nichat_fade(color);
    }
}
