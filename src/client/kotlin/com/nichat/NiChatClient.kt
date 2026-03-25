package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.HorizontalAlignment
import com.nichat.config.NiChatConfig
import com.nichat.config.NiChatConfigManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.util.FormattedCharSequence
import org.slf4j.LoggerFactory

import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class NiChatClient : ClientModInitializer {

    companion object {
        @JvmStatic val logger = LoggerFactory.getLogger("nichat")

        @JvmStatic val config: NiChatConfig
            get() = NiChatConfigManager.config

        @JvmStatic val allMessages: MutableList<DisplayMessage> = mutableListOf()
        @JvmStatic val SYSTEM_PROFILE = GameProfile(
            UUID.fromString("00000000-0000-0000-0000-000000000000"),
            "System"
        )
        private const val SKIN_TEXTURE_SIZE = 64f
        private const val FACE_U = 8f
        private const val FACE_V = 8f
        private const val FACE_OVERLAY_U = 40f
        private const val FACE_OVERLAY_V = 8f
        private const val FACE_REGION_SIZE = 8

        @JvmStatic
        fun processNewMessage(content: Component, profile: GameProfile) {
            if (content.string.isBlank()) return

            val finalContent = reformatChatMessage(content, profile)
            val newMessage = DisplayMessage(finalContent, profile, System.nanoTime())

            synchronized(allMessages) {
                allMessages.add(newMessage)
                if (allMessages.size > config.chatLog.maxLogSize) {
                    MessageLayoutCache.invalidateMessage(allMessages.removeFirst())
                }
            }
            logger.debug("Message added: ${finalContent.string}")
        }

        private fun reformatChatMessage(originalMessage: Component, profile: GameProfile): Component {
            if (profile == SYSTEM_PROFILE) {
                return originalMessage
            }

            val content = originalMessage.contents
            if (content is TranslatableContents && content.key == "chat.type.text" && content.args.size >= 2) {
                val senderName = (content.args[0] as? Component)?.string ?: profile.name
                val messageBody = content.args[1] as? Component ?: return originalMessage

                val color: TextColor = if (config.general.randomNicknameColor) {
                    val random = Random(senderName.hashCode().toLong())
                    val r = random.nextInt(155) + 100
                    val g = random.nextInt(155) + 100
                    val b = random.nextInt(155) + 100
                    TextColor.fromRgb((r shl 16) or (g shl 8) or b)
                } else {
                    val rawColor = config.general.nicknameColor.trim()
                    val colorHex = rawColor.removePrefix("#")

                    val colorInt = try {
                        Integer.parseInt(colorHex, 16)
                    } catch (e: Exception) {
                        logger.warn("Invalid nickname color in config: '{}'", rawColor)
                        0x55FF55
                    }

                    TextColor.fromRgb(colorInt)
                }

                return Component.empty()
                    .append(Component.literal("[").setStyle(Style.EMPTY.withColor(color)))
                    .append(Component.literal(senderName).setStyle(Style.EMPTY.withColor(color)))
                    .append(Component.literal("]").setStyle(Style.EMPTY.withColor(color)))
                    .append(Component.literal(": ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))
                    .append(messageBody)
            }

            return originalMessage
        }

        @JvmStatic
        fun renderCustomHud(context: GuiGraphicsExtractor, client: Minecraft) {
            val hudConfig = config.hud
            val durationNanos = TimeUnit.SECONDS.toNanos(hudConfig.hudMessageDuration)
            val fadeInDurationNanos = TimeUnit.MILLISECONDS.toNanos(hudConfig.hudFadeInDurationMs.coerceAtLeast(0L))
            val fadeOutDurationNanos = TimeUnit.MILLISECONDS.toNanos(hudConfig.hudFadeOutDurationMs.coerceAtLeast(0L))
            val animationOffsetPx = hudConfig.hudAnimationOffsetPx.coerceAtLeast(0).toFloat()
            val currentTime = System.nanoTime()

            val messagesToRender = synchronized(allMessages) {
                allMessages.filter { message ->
                    currentTime - message.receivedTimeNano <= durationNanos
                }.takeLast(hudConfig.hudMaxMessages) // Берём последние N сообщений
            }

            if (messagesToRender.isEmpty()) return

            val font = client.font
            val screenWidth = context.guiWidth()
            val screenHeight = context.guiHeight()
            val maxTextWidth = (screenWidth * hudConfig.hudWidthScale).toInt()

            val headSize = hudConfig.hudHeadSize
            val padding = hudConfig.hudPadding
            val headTextSpacing = hudConfig.hudHeadTextSpacing
            val messagePadding = hudConfig.hudPaddingBetweenMessages

            val messageBoxes = MessageLayoutCache.getBlocks(
                font = font,
                messages = messagesToRender,
                maxTextWidth = maxTextWidth,
                headSize = headSize,
                headTextSpacing = headTextSpacing,
                padding = padding
            )

            var cursorY = screenHeight - hudConfig.hudVerticalOffset

            for (box in messageBoxes.asReversed()) {
                val messageAge = currentTime - box.message.receivedTimeNano
                val animationProgress = getHudAnimationProgress(
                    messageAge,
                    durationNanos,
                    fadeInDurationNanos,
                    fadeOutDurationNanos
                )
                if (animationProgress <= 0f) continue

                val posX = when (hudConfig.hudHorizontalAlignment) {
                    HorizontalAlignment.LEFT -> 10
                    HorizontalAlignment.CENTER -> (screenWidth - box.boxWidth) / 2
                    HorizontalAlignment.RIGHT -> screenWidth - box.boxWidth - 10
                }

                val boxBottomY = cursorY
                val boxTopY = boxBottomY - box.boxHeight
                val backgroundColor = scaleColorAlpha(
                    client.options.getBackgroundColor(hudConfig.hudBackgroundOpacity),
                    animationProgress
                )
                val animatedOffsetY = ((1f - animationProgress) * animationOffsetPx).roundToInt()
                val animatedTopY = boxTopY + animatedOffsetY
                val animatedBottomY = boxBottomY + animatedOffsetY
                context.fill(posX, animatedTopY, posX + box.boxWidth, animatedBottomY, backgroundColor)

                val contentTopY = animatedTopY + padding
                val contentHeight = box.boxHeight - padding * 2
                val textLeftX: Int

                if (box.drawHead) {
                    val contentLeftX = posX + padding
                    val headY = contentTopY + (contentHeight - headSize) / 2
                    drawPlayerHead(
                        context,
                        client,
                        box.message.senderProfile,
                        contentLeftX,
                        headY,
                        headSize,
                        animationProgress
                    )
                    textLeftX = posX + padding + headSize + headTextSpacing
                } else {
                    textLeftX = posX + (box.boxWidth - box.textWidth) / 2
                }

                val verticalCenterOffset = (contentHeight - box.lines.size * font.lineHeight) / 2
                var textTopY = contentTopY + verticalCenterOffset
                val textColor = scaleColorAlpha(0xFFFFFFFF.toInt(), animationProgress)

                for (line in box.lines) {
                    context.text(font, line, textLeftX, textTopY, textColor)
                    textTopY += font.lineHeight
                }

                cursorY = boxTopY - messagePadding
            }
        }

        private fun getHudAnimationProgress(
            messageAgeNanos: Long,
            totalDurationNanos: Long,
            fadeInDurationNanos: Long,
            fadeOutDurationNanos: Long
        ): Float {
            if (totalDurationNanos <= 0L) return 1f

            val fadeInProgress = if (fadeInDurationNanos <= 0L) {
                1f
            } else {
                (messageAgeNanos.toDouble() / fadeInDurationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
            }

            val remainingNanos = totalDurationNanos - messageAgeNanos
            val fadeOutProgress = if (fadeOutDurationNanos <= 0L) {
                1f
            } else {
                (remainingNanos.toDouble() / fadeOutDurationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
            }

            return smoothStep(minOf(fadeInProgress, fadeOutProgress))
        }

        private fun smoothStep(value: Float): Float {
            val clamped = value.coerceIn(0f, 1f)
            return clamped * clamped * (3f - 2f * clamped)
        }

        private fun scaleColorAlpha(color: Int, alphaMultiplier: Float): Int {
            val scaledAlpha = (((color ushr 24) and 0xFF) * alphaMultiplier.coerceIn(0f, 1f)).roundToInt()
            return (color and 0x00FFFFFF) or (scaledAlpha.coerceIn(0, 255) shl 24)
        }

        @JvmStatic
        fun drawPlayerHead(
            context: GuiGraphicsExtractor,
            client: Minecraft,
            profile: GameProfile,
            x: Int,
            y: Int,
            size: Int,
            alpha: Float
        ) {
            val playerSkin = client.connection?.getPlayerInfo(profile.id)?.skin ?: DefaultPlayerSkin.get(profile)
            val texture = playerSkin.body().texturePath()
            val tint = scaleColorAlpha(0xFFFFFFFF.toInt(), alpha)

            context.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                FACE_U,
                FACE_V,
                size,
                size,
                FACE_REGION_SIZE,
                FACE_REGION_SIZE,
                SKIN_TEXTURE_SIZE.toInt(),
                SKIN_TEXTURE_SIZE.toInt(),
                tint
            )
            context.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                FACE_OVERLAY_U,
                FACE_OVERLAY_V,
                size,
                size,
                FACE_REGION_SIZE,
                FACE_REGION_SIZE,
                SKIN_TEXTURE_SIZE.toInt(),
                SKIN_TEXTURE_SIZE.toInt(),
                tint
            )
        }
    }

    override fun onInitializeClient() {
        NiChatConfigManager.load()
        logger.info("NiChat initialized and config loaded")

        ClientReceiveMessageEvents.ALLOW_CHAT.register { message, _, sender, _, _ ->
            processNewMessage(message, sender ?: SYSTEM_PROFILE)
            false
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            processNewMessage(message, SYSTEM_PROFILE)
            false
        }
    }
}
