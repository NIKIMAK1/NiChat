package com.nichat.mixin.client;

import com.nichat.NiChatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.DisplayMode;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(
            method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessageWithSignature(
            Component message,
            @Nullable MessageSignature signature,
            @Nullable GuiMessageTag indicator,
            CallbackInfo ci
    ) {
        // Перехватываем сообщение и отдаем его NiChat
        NiChatClient.processNewMessage(message, NiChatClient.getSYSTEM_PROFILE());
        ci.cancel(); // Отменяем добавление в ванильный чат
    }

    @Inject(
            method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddClientSystemMessage(Component message, CallbackInfo ci) {
        NiChatClient.processNewMessage(message, NiChatClient.getSYSTEM_PROFILE());
        ci.cancel();
    }

    @Inject(
            method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddServerSystemMessage(Component message, CallbackInfo ci) {
        NiChatClient.processNewMessage(message, NiChatClient.getSYSTEM_PROFILE());
        ci.cancel();
    }

    @Inject(
            method = "extractRenderState",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRender(
            GuiGraphicsExtractor context,
            Font font,
            int currentTick,
            int mouseX,
            int mouseY,
            DisplayMode displayMode,
            boolean focused,
            CallbackInfo ci
    ) {
        if (this.minecraft.screen != null) {
            ci.cancel();
            return;
        }

        NiChatClient.renderCustomHud(context, this.minecraft);

        ci.cancel();
    }
}
