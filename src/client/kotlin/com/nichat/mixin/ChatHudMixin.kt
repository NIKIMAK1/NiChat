package com.nichat.mixin

import com.nichat.NiChatClient
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.gui.hud.ChatHud
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.text.Text
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Mixin(ChatHud::class)
class ChatHudMixin {

    @Shadow @Final private lateinit var client: MinecraftClient

    @Inject(method = ["addMessage(Lnet/minecraft/text/Text;)V"], at = [At("HEAD")], cancellable = true)
    private fun onAddMessage(message: Text, ci: CallbackInfo) {
        NiChatClient.processNewMessage(message, NiChatClient.SYSTEM_PROFILE)
        ci.cancel()
    }

    @Inject(method = ["render"], at = [At("HEAD")], cancellable = true)
    private fun onRender(context: DrawContext, tickCounter: Int, mouseX: Int, mouseY: Int, focused: Boolean, ci: CallbackInfo) {

        if (this.client.currentScreen != null) {
            ci.cancel()
            return
        }

        renderCustomHud(context)

        ci.cancel()
    }

    private fun renderCustomHud(context: DrawContext) {
        val hudConfig = NiChatClient.config.hud
        val durationNanos = TimeUnit.SECONDS.toNanos(hudConfig.hudMessageDuration)
        val messageToRender = NiChatClient.allMessages.lastOrNull {
            System.nanoTime() - it.receivedTimeNano <= durationNanos
        } ?: return

        val font = this.client.textRenderer
        val screenWidth = context.scaledWindowWidth
        val maxTextWidth = (screenWidth * hudConfig.hudWidthScale).toInt()
        val headSize = hudConfig.hudHeadSize
        val padding = hudConfig.hudPadding
        val headTextSpacing = hudConfig.hudHeadTextSpacing
        val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
        val lines = font.wrapLines(messageToRender.content, maxTextWidth)
        val textWidth = lines.maxOfOrNull { font.getWidth(it) } ?: 0
        val headSpace = if (drawHead) headSize + headTextSpacing else 0
        val boxWidth = headSpace + textWidth + padding * 2
        val boxHeight = max(font.fontHeight * lines.size, headSize) + padding * 2
        val posX = when (hudConfig.hudHorizontalAlignment) {
            com.nichat.config.HorizontalAlignment.LEFT -> 10
            com.nichat.config.HorizontalAlignment.CENTER -> (context.scaledWindowWidth - boxWidth) / 2
            com.nichat.config.HorizontalAlignment.RIGHT -> context.scaledWindowWidth - boxWidth - 10
        }
        val posY = context.scaledWindowHeight - boxHeight - hudConfig.hudVerticalOffset
        context.fill(posX, posY, posX + boxWidth, posY + boxHeight, client.options.getTextBackgroundColor(hudConfig.hudBackgroundOpacity))
        val contentTopY = posY + padding
        val textLeftX: Int
        if (drawHead) {
            val contentLeftX = posX + padding
            val profile = messageToRender.senderProfile
            val playerListEntry = client.networkHandler?.getPlayerListEntry(profile.id)
            val skinTextures = playerListEntry?.skinTextures ?: DefaultSkinHelper.getSkinTextures(profile.id)
            PlayerSkinDrawer.draw(context, skinTextures, contentLeftX, contentTopY, headSize)
            textLeftX = contentLeftX + headSize + headTextSpacing
        } else {
            textLeftX = posX + (boxWidth - textWidth) / 2
        }
        var textTopY = contentTopY + (boxHeight - padding * 2 - lines.size * font.fontHeight) / 2 + 1
        for (line in lines) {
            context.drawTextWithShadow(font, line, textLeftX, textTopY, 0xFFFFFFFF.toInt())
            textTopY += font.fontHeight
        }
    }
}