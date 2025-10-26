package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.HorizontalAlignment
import com.nichat.config.NiChatConfig
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.text.*
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
        fun processNewMessage(content: Text, profile: GameProfile) {
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

        private fun reformatChatMessage(originalMessage: Text, profile: GameProfile): Text {
            if (profile == SYSTEM_PROFILE) {
                return originalMessage
            }

            val content = originalMessage.content
            if (content is TranslatableTextContent && content.key == "chat.type.text" && content.args.size >= 2) {
                val senderName = (content.args[0] as? Text)?.string ?: profile.name
                val messageBody = content.args[1] as? Text ?: return originalMessage

                val color: TextColor = if (config.general.randomNicknameColor) {
                    val random = Random(senderName.hashCode().toLong())
                    val r = random.nextInt(155) + 100
                    val g = random.nextInt(155) + 100
                    val b = random.nextInt(155) + 100
                    TextColor.fromRgb((r shl 16) or (g shl 8) or b)
                } else {
                    try {
                        val colorHex = config.general.nicknameColor.let { if (it.startsWith("#")) it else "#$it" }
                        TextColor.parse(colorHex) as TextColor
                    } catch (e: Exception) {
                        logger.warn("Invalid nickname color in config: '{}'", config.general.nicknameColor)
                        TextColor.fromRgb(0x55FF55)
                    }
                }


                return Text.empty()
                    .append(Text.literal("[").setStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal(senderName).setStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal(": ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))
                    .append(messageBody)
            }

            return originalMessage
        }

        @JvmStatic
        fun renderCustomHud(context: DrawContext, client: MinecraftClient) {
            val hudConfig = config.hud
            val durationNanos = TimeUnit.SECONDS.toNanos(hudConfig.hudMessageDuration)

            val messageToRender = synchronized(allMessages) {
                allMessages.lastOrNull { message ->
                    System.nanoTime() - message.receivedTimeNano <= durationNanos
                }
            } ?: return

            val font = client.textRenderer
            val screenWidth = context.scaledWindowWidth
            val screenHeight = context.scaledWindowHeight
            val maxTextWidth = (screenWidth * hudConfig.hudWidthScale).toInt()

            val headSize = hudConfig.hudHeadSize
            val padding = hudConfig.hudPadding
            val headTextSpacing = hudConfig.hudHeadTextSpacing
            val drawHead = messageToRender.senderProfile != SYSTEM_PROFILE

            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val textWidth = lines.maxOfOrNull { font.getWidth(it) } ?: 0

            val headSpace = if (drawHead) headSize + headTextSpacing else 0
            val boxWidth = headSpace + textWidth + padding * 2
            val boxHeight = max(font.fontHeight * lines.size, if (drawHead) headSize else 0) + padding * 2

            val posX = when (hudConfig.hudHorizontalAlignment) {
                HorizontalAlignment.LEFT -> 10
                HorizontalAlignment.CENTER -> (screenWidth - boxWidth) / 2
                HorizontalAlignment.RIGHT -> screenWidth - boxWidth - 10
            }
            val posY = screenHeight - boxHeight - hudConfig.hudVerticalOffset

            val backgroundColor = client.options.getTextBackgroundColor(hudConfig.hudBackgroundOpacity)
            context.fill(posX, posY, posX + boxWidth, posY + boxHeight, backgroundColor)

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

            val verticalCenterOffset = (boxHeight - padding * 2 - lines.size * font.fontHeight) / 2
            var textTopY = contentTopY + verticalCenterOffset + 1

            for (line in lines) {
                context.drawTextWithShadow(font, line, textLeftX, textTopY, 0xFFFFFFFF.toInt())
                textTopY += font.fontHeight
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