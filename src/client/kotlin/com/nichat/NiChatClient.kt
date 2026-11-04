package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.HorizontalAlignment
import com.nichat.config.NiChatConfig
import com.nichat.config.NiChatConfigManager
import com.nichat.translation.MessageTranslation
import com.nichat.translation.Translator
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.util.FormattedCharSequence
import org.slf4j.LoggerFactory

import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor

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
        fun processNewMessage(content: Component, receivedProfile: GameProfile) {
            if (content.string.isBlank()) return

            var profile = receivedProfile
            var finalContent = reformatChatMessage(content, profile)

            if (profile == SYSTEM_PROFILE && content.contents !is TranslatableContents) {
                ServerChatSender.detect(content, ::nicknameColor)?.let { detected ->
                    profile = detected.profile
                    finalContent = detected.content
                }
            }

            val newMessage = DisplayMessage(finalContent, profile, System.nanoTime(), content)

            synchronized(allMessages) {
                allMessages.add(newMessage)
                if (allMessages.size > config.chatLog.maxLogSize) {
                    MessageLayoutCache.invalidateMessage(allMessages.removeFirst())
                }
            }
            logger.debug("Message added: ${finalContent.string}")

            if (shouldTranslate(content, profile)) {
                requestTranslation(newMessage)
            }
        }

        private fun shouldTranslate(originalContent: Component, profile: GameProfile): Boolean {
            val settings = config.translation
            if (!settings.enabled) return false

            if (!settings.translateOwnMessages && profile.id == Minecraft.getInstance().player?.uuid) {
                return false
            }

            val contents = originalContent.contents
            return !(contents is TranslatableContents && !contents.key.startsWith("chat.type."))
        }

        private fun requestTranslation(message: DisplayMessage) {
            val parts = MessageTranslation.split(message.content) ?: return

            Translator.translate(parts.body, config.translation.targetLanguage).thenAccept { translated ->
                if (translated == null) return@thenAccept
                val translatedContent = parts.build(translated, message.content)
                val translatedOriginal = if (message.originalContent === message.content) {
                    translatedContent
                } else {
                    MessageTranslation.split(message.originalContent)
                        ?.takeIf { it.body == parts.body }
                        ?.build(translated, message.originalContent)
                        ?: message.originalContent
                }

                Minecraft.getInstance().execute {
                    synchronized(allMessages) {
                        val index = allMessages.indexOfFirst { it === message }
                        if (index < 0) return@execute
                        allMessages[index] = message.copy(content = translatedContent, originalContent = translatedOriginal)
                    }
                    MessageLayoutCache.invalidateMessage(message)
                }
            }
        }

        private fun nicknameColor(senderName: String): TextColor {
            if (config.general.randomNicknameColor) {
                val random = Random(senderName.hashCode().toLong())
                val r = random.nextInt(155) + 100
                val g = random.nextInt(155) + 100
                val b = random.nextInt(155) + 100
                return TextColor.fromRgb((r shl 16) or (g shl 8) or b)
            }

            val rawColor = config.general.nicknameColor.trim()
            val colorInt = try {
                Integer.parseInt(rawColor.removePrefix("#"), 16)
            } catch (e: Exception) {
                logger.warn("Invalid nickname color in config: '{}'", rawColor)
                0x55FF55
            }
            return TextColor.fromRgb(colorInt)
        }

        private fun reformatChatMessage(originalMessage: Component, profile: GameProfile): Component {
            if (profile == SYSTEM_PROFILE) {
                return originalMessage
            }

            val content = originalMessage.contents
            if (content is TranslatableContents && content.key == "chat.type.text" && content.args.size >= 2) {
                val senderName = (content.args[0] as? Component)?.string ?: profile.name
                val messageBody = content.args[1] as? Component ?: return originalMessage

                val color = nicknameColor(senderName)

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
        fun renderCustomHud(context: GuiGraphicsExtractor, client: Minecraft, hudOpacity: Float) {
            if (hudOpacity <= 0f) return
            val hudConfig = config.hud
            val classic = MessageLayout.isClassic
            val durationNanos = TimeUnit.MILLISECONDS.toNanos(MessageLayout.hudDurationMs(hudConfig.hudMessageDuration))
            val fadeOutDurationNanos = TimeUnit.MILLISECONDS.toNanos(MessageLayout.hudFadeOutMs(hudConfig.hudFadeOutDurationMs).coerceAtLeast(0L))
            // Vanilla lines fade in place and are pushed out of the clipped area instead of being evicted.
            val animationOffsetPx = if (classic) 0f else hudConfig.hudAnimationOffsetPx.coerceAtLeast(0).toFloat()
            val currentTime = System.nanoTime()

            val activeMessages = synchronized(allMessages) {
                allMessages.filter { message ->
                    currentTime - message.receivedTimeNano <= durationNanos
                }
            }
            val maxMessages = if (classic) activeMessages.size else hudConfig.hudMaxMessages.coerceAtLeast(1)

            val disappearProgressByMessage = HashMap<DisplayMessage, Float>()
            val messagesToRender = activeMessages.filterIndexed { index, message ->
                val newerIndex = index + maxMessages
                val evictedAt = activeMessages.getOrNull(newerIndex)?.receivedTimeNano
                val fadeOutStart = minOf(
                    message.receivedTimeNano + durationNanos - fadeOutDurationNanos,
                    evictedAt ?: Long.MAX_VALUE
                )
                val progress = getFadeOutProgress(currentTime - fadeOutStart, fadeOutDurationNanos)
                disappearProgressByMessage[message] = progress
                progress > 0f
            }

            if (messagesToRender.isEmpty()) return

            val font = client.font
            val screenWidth = context.guiWidth()
            val screenHeight = context.guiHeight()
            val maxTextWidth = MessageLayout.hudTextWidth(screenWidth)

            val headSize = hudConfig.hudHeadSize
            val padding = hudConfig.hudPadding
            val headTextSpacing = hudConfig.hudHeadTextSpacing
            val messagePadding = MessageLayout.spacing(hudConfig.hudPaddingBetweenMessages)

            val messageBoxes = MessageLayoutCache.getBlocks(
                font = font,
                messages = messagesToRender,
                maxTextWidth = maxTextWidth,
                headSize = headSize,
                headTextSpacing = headTextSpacing,
                padding = padding
            )

            val hudBottomY = MessageLayout.hudBottomY(screenHeight)
            var cursorY = hudBottomY.toFloat()
            val textOpacity = MessageLayout.textOpacity()
            val backgroundOpacity = MessageLayout.backgroundOpacity(hudConfig.hudBackgroundOpacity)
            if (classic) context.enableScissor(0, MessageLayout.hudTopY(hudBottomY), screenWidth, hudBottomY)
            var stackShift = 0f

            for (box in messageBoxes.asReversed()) {
                val messageAge = currentTime - box.message.receivedTimeNano
                val appearTime = Animation.linear(messageAge, hudConfig.hudFadeInDurationMs)
                val appearProgress = Animation.smoothStep(appearTime)
                val disappearProgress = disappearProgressByMessage[box.message] ?: 1f
                val animationProgress = minOf(appearProgress, disappearProgress) * hudOpacity

                stackShift += (1f - Animation.easeOutCubic(appearTime)) * (box.boxHeight + messagePadding)
                val fadeOutOffset = (1f - disappearProgress) * animationOffsetPx
                val exactBottomY = cursorY + stackShift - fadeOutOffset
                val boxBottomY = floor(exactBottomY).toInt()
                cursorY -= box.boxHeight + messagePadding

                if (animationProgress <= 0f) continue

                context.pose().pushMatrix()
                context.pose().translate(0f, exactBottomY - boxBottomY)

                val posX = if (classic) 0 else when (hudConfig.hudHorizontalAlignment) {
                    HorizontalAlignment.LEFT -> 10
                    HorizontalAlignment.CENTER -> (screenWidth - box.boxWidth) / 2
                    HorizontalAlignment.RIGHT -> screenWidth - box.boxWidth - 10
                }

                val boxTopY = boxBottomY - box.boxHeight
                val backgroundColor = Animation.scaleColorAlpha(
                    client.options.getBackgroundColor(backgroundOpacity),
                    animationProgress
                )
                context.fill(posX, boxTopY, posX + box.boxWidth, boxBottomY, backgroundColor)

                val contentTopY = boxTopY + box.paddingY
                val contentHeight = box.contentHeight

                if (box.drawHead) {
                    val headY = contentTopY + box.headOffsetY
                    drawPlayerHead(
                        context,
                        client,
                        box.message.senderProfile,
                        posX + box.headOffsetX,
                        headY,
                        box.headSize,
                        animationProgress
                    )
                }
                val textLeftX = posX + box.textOffsetX

                val verticalCenterOffset = (contentHeight - box.lines.size * font.lineHeight) / 2
                var textTopY = contentTopY + verticalCenterOffset
                val textColor = Animation.scaleColorAlpha(0xFFFFFFFF.toInt(), animationProgress * textOpacity)

                for (line in box.lines) {
                    context.text(font, line, textLeftX, textTopY, textColor)
                    textTopY += font.lineHeight
                }

                context.pose().popMatrix()
            }

            if (classic) context.disableScissor()
        }

        private fun getFadeOutProgress(elapsedSinceFadeStartNanos: Long, fadeOutDurationNanos: Long): Float {
            if (elapsedSinceFadeStartNanos < 0L) return 1f
            if (fadeOutDurationNanos <= 0L) return 0f
            return 1f - Animation.smoothStep((elapsedSinceFadeStartNanos.toDouble() / fadeOutDurationNanos.toDouble()).toFloat())
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
            val playerSkin = PlayerSkins.get(client, profile)
            val texture = playerSkin.body().texturePath()
            val tint = Animation.scaleColorAlpha(0xFFFFFFFF.toInt(), alpha)

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

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay) return@register true

            processNewMessage(message, SYSTEM_PROFILE)
            false
        }
    }
}
