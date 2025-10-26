package com.nichat.mixin.client;

import com.nichat.NiChatClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessageWithSignature(
            Text message,
            @Nullable MessageSignatureData signature,
            @Nullable MessageIndicator indicator,
            CallbackInfo ci
    ) {
        NiChatClient.processNewMessage(message, NiChatClient.getSYSTEM_PROFILE());
        ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessage(Text message, CallbackInfo ci) {
        NiChatClient.processNewMessage(message, NiChatClient.getSYSTEM_PROFILE());
        ci.cancel();
    }

    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRender(
            DrawContext context,
            int currentTick,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo ci
    ) {
        if (this.client.currentScreen != null) {
            ci.cancel();
            return;
        }

        NiChatClient.renderCustomHud(context, this.client);

        ci.cancel();
    }
}