package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.HorizontalAlignment
import com.nichat.config.NiChatConfig
import com.nichat.config.NiChatConfigManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.util.FormattedCharSequence
import org.slf4j.LoggerFactory

import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

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

        @JvmStatic
        fun processNewMessage(content: Component, profile: GameProfile) {
            if (content.string.isBlank()) return

            val finalContent = reformatChatMessage(content, profile)
            val newMessage = DisplayMessage(finalContent, profile, System.nanoTime())

            synchronized(allMessages) {
                allMessages.add(newMessage)
                if (allMessages.size > config.chatLog.maxLogSize) {
                    allMessages.removeFirst()
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
        fun renderCustomHud(context: GuiGraphics, client: Minecraft) {
            val hudConfig = config.hud
            val durationNanos = TimeUnit.SECONDS.toNanos(hudConfig.hudMessageDuration)
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

            data class MessageBox(
                val message: DisplayMessage,
                val lines: List<FormattedCharSequence>,
                val boxWidth: Int,
                val boxHeight: Int,
                val textWidth: Int,
                val drawHead: Boolean
            )

            val messageBoxes = messagesToRender.map { message ->
                val drawHead = message.senderProfile != SYSTEM_PROFILE
                val lines = font.split(message.content, maxTextWidth)
                val textWidth = lines.maxOfOrNull { font.width(it) } ?: 0
                val headSpace = if (drawHead) headSize + headTextSpacing else 0
                val contentHeight = max(font.lineHeight * lines.size, if (drawHead) headSize else 0)
                val boxWidth = headSpace + textWidth + padding * 2
                val boxHeight = contentHeight + padding * 2

                MessageBox(message, lines, boxWidth, boxHeight, textWidth, drawHead)
            }

            var cursorY = screenHeight - hudConfig.hudVerticalOffset

            for (box in messageBoxes.asReversed()) {
                val posX = when (hudConfig.hudHorizontalAlignment) {
                    HorizontalAlignment.LEFT -> 10
                    HorizontalAlignment.CENTER -> (screenWidth - box.boxWidth) / 2
                    HorizontalAlignment.RIGHT -> screenWidth - box.boxWidth - 10
                }

                val boxBottomY = cursorY
                val boxTopY = boxBottomY - box.boxHeight
                val backgroundColor = client.options.getBackgroundColor(hudConfig.hudBackgroundOpacity)
                context.fill(posX, boxTopY, posX + box.boxWidth, boxBottomY, backgroundColor)

                val contentTopY = boxTopY + padding
                val contentHeight = box.boxHeight - padding * 2
                val textLeftX: Int

                if (box.drawHead) {
                    val contentLeftX = posX + padding
                    val profile = box.message.senderProfile
                    val playerListEntry = client.connection?.getPlayerInfo(profile.id)
                    val skinTextures = playerListEntry?.skin ?: DefaultPlayerSkin.get(profile.id)
                    val headY = contentTopY + (contentHeight - headSize) / 2
                    PlayerFaceRenderer.draw(context, skinTextures, contentLeftX, headY, headSize)
                    textLeftX = contentLeftX + headSize + headTextSpacing
                } else {
                    textLeftX = posX + (box.boxWidth - box.textWidth) / 2
                }

                val verticalCenterOffset = (contentHeight - box.lines.size * font.lineHeight) / 2
                var textTopY = contentTopY + verticalCenterOffset

                for (line in box.lines) {
                    context.drawString(font, line, textLeftX, textTopY, 0xFFFFFFFF.toInt())
                    textTopY += font.lineHeight
                }

                cursorY = boxTopY - messagePadding
            }
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
