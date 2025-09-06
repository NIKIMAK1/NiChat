package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.NiChatConfig
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.text.*
import org.slf4j.LoggerFactory
import java.util.*

class NiChatClient : ClientModInitializer {

    companion object {
        @JvmStatic val logger = LoggerFactory.getLogger("nichat")
        @JvmStatic lateinit var config: NiChatConfig
            private set

        @JvmStatic val allMessages: MutableList<DisplayMessage> = mutableListOf()
        @JvmStatic val SYSTEM_PROFILE = GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), "System")

        @JvmStatic
        fun processNewMessage(content: Text, profile: GameProfile) {
            if (content.string.isBlank()) return

            // Форматируем сообщение перед сохранением
            val finalContent = reformatChatMessage(content, profile)
            val newMessage = DisplayMessage(finalContent, profile, System.nanoTime())

            allMessages.add(newMessage)
            if (allMessages.size > config.chatLog.maxLogSize) {
                allMessages.removeFirst()
            }
        }

        private fun reformatChatMessage(originalMessage: Text, profile: GameProfile): Text {
            if (profile == SYSTEM_PROFILE) return originalMessage

            val content = originalMessage.content
            if (content is TranslatableTextContent && content.key == "chat.type.text" && content.args.isNotEmpty()) {
                val messageBody = content.args.getOrNull(1) as? Text ?: return originalMessage

                val color: TextColor
                if (config.general.randomNicknameColor) {
                    val random = Random(profile.name.hashCode().toLong())
                    val r = random.nextInt(155) + 100
                    val g = random.nextInt(155) + 100
                    val b = random.nextInt(155) + 100
                    color = TextColor.fromRgb((r shl 16) or (g shl 8) or b)
                } else {
                    color = try {
                        TextColor.fromRgb(Integer.parseInt(config.general.nicknameColor.removePrefix("#"), 16))
                    } catch (e: NumberFormatException) {
                        logger.warn("Invalid nickname color format: ${config.general.nicknameColor}")
                        TextColor.fromRgb(0xFFFFFF)
                    }
                }

                val nickComponent = Text.empty()
                    .append(Text.literal("[").setStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal(profile.name).setStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal(": ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))

                return Text.empty().append(nickComponent).append(messageBody)
            }

            return originalMessage
        }
    }

    override fun onInitializeClient() {
        AutoConfig.register(NiChatConfig::class.java, ::GsonConfigSerializer)
        config = AutoConfig.getConfigHolder(NiChatConfig::class.java).config
        logger.info("NiChat config loaded.")

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