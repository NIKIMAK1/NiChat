package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.HorizontalAlignment
import com.nichat.config.NiChatConfig
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
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
            get() = AutoConfig.getConfigHolder(NiChatConfig::class.java).config

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
                val boxWidth = headSpace + textWidth + padding * 2
                val boxHeight = max(font.lineHeight * lines.size, if (drawHead) headSize else 0) + padding * 2

                MessageBox(message, lines, boxWidth, boxHeight, textWidth, drawHead)
            }

            val totalHeight = messageBoxes.sumOf { it.boxHeight } + messagePadding * (messageBoxes.size - 1)

            var currentY = screenHeight - hudConfig.hudVerticalOffset - totalHeight

            for (box in messageBoxes) {
                val posX = when (hudConfig.hudHorizontalAlignment) {
                    HorizontalAlignment.LEFT -> 10
                    HorizontalAlignment.CENTER -> (screenWidth - box.boxWidth) / 2
                    HorizontalAlignment.RIGHT -> screenWidth - box.boxWidth - 10
                }

                val backgroundColor = client.options.getBackgroundColor(hudConfig.hudBackgroundOpacity)
                context.fill(posX, currentY, posX + box.boxWidth, currentY + box.boxHeight, backgroundColor)

                val contentTopY = currentY + padding
                val textLeftX: Int

                if (box.drawHead) {
                    val contentLeftX = posX + padding
                    val profile = box.message.senderProfile
                    val playerListEntry = client.connection?.getPlayerInfo(profile.id)
                    val skinTextures = playerListEntry?.skin ?: DefaultPlayerSkin.get(profile.id)
                    PlayerFaceRenderer.draw(context, skinTextures, contentLeftX, contentTopY, headSize)
                    textLeftX = contentLeftX + headSize + headTextSpacing
                } else {
                    textLeftX = posX + (box.boxWidth - box.textWidth) / 2
                }

                val verticalCenterOffset = (box.boxHeight - padding * 2 - box.lines.size * font.lineHeight) / 2
                var textTopY = contentTopY + verticalCenterOffset + 1

                for (line in box.lines) {
                    context.drawString(font, line, textLeftX, textTopY, 0xFFFFFFFF.toInt())
                    textTopY += font.lineHeight
                }

                currentY += box.boxHeight + messagePadding
            }
        }
    }

    override fun onInitializeClient() {
        AutoConfig.register(NiChatConfig::class.java, ::Toml4jConfigSerializer)
        logger.info("NiChat initialized and config registered!")

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