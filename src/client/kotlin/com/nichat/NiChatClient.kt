package com.nichat

import com.mojang.authlib.GameProfile
import com.nichat.config.NiChatConfig
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.client.util.InputUtil
import net.minecraft.text.*
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

object NiChatClient : ClientModInitializer {

	val logger = LoggerFactory.getLogger("nichat")
	lateinit var config: NiChatConfig
		private set

	internal val allMessages: MutableList<DisplayMessage> = mutableListOf()
	private var currentHudMessage: DisplayMessage? = null
	private val client: MinecraftClient = MinecraftClient.getInstance()
	private lateinit var toggleChatLogKey: KeyBinding

	internal val SYSTEM_PROFILE = GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), "System")

	override fun onInitializeClient() {
		AutoConfig.register(NiChatConfig::class.java, ::GsonConfigSerializer)
		config = AutoConfig.getConfigHolder(NiChatConfig::class.java).config
		logger.info("NiChat config loaded.")

		toggleChatLogKey = KeyBindingHelper.registerKeyBinding(
			KeyBinding("key.nichat.toggle_log", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.category.nichat")
		)

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			while (toggleChatLogKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(ChatLogScreen())
				}
			}
		}

		ClientReceiveMessageEvents.ALLOW_CHAT.register { message, _, sender, _, _ ->
			val profile = sender ?: SYSTEM_PROFILE
			processNewMessage(message, profile)
			false
		}

		ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
			processNewMessage(message, SYSTEM_PROFILE)
			false
		}

		HudRenderCallback.EVENT.register { context, _ ->
			val hudConfig = config.hud

			if (client.options.hudHidden) return@register
			if (client.currentScreen != null) {
				currentHudMessage = null
				return@register
			}

			val messageToRender = currentHudMessage ?: return@register

			if (System.nanoTime() - messageToRender.receivedTimeNano > TimeUnit.SECONDS.toNanos(hudConfig.hudMessageDuration)) {
				currentHudMessage = null
				return@register
			}

			val font = client.textRenderer
			val screenWidth = context.scaledWindowWidth
			val maxTextWidth = (screenWidth * hudConfig.hudWidthScale).toInt()

			val headSize = hudConfig.hudHeadSize
			val padding = hudConfig.hudPadding
			val headTextSpacing = hudConfig.hudHeadTextSpacing
			val drawHead = messageToRender.senderProfile != SYSTEM_PROFILE

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

	private fun reformatChatMessage(originalMessage: Text, profile: GameProfile): Text {
		val content = originalMessage.content
		if (content is TranslatableTextContent && content.key == "chat.type.text" && content.args.size >= 2) {
			val messageBody = content.args[1] as? Text ?: return originalMessage

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
				.append(Text.literal(": ").setStyle(Style.EMPTY))

			return Text.empty().append(nickComponent).append(messageBody)
		}

		return originalMessage
	}

	private fun processNewMessage(content: Text, profile: GameProfile) {
		if (content.string.isBlank()) return

		val finalContent = if (profile != SYSTEM_PROFILE) {
			reformatChatMessage(content, profile)
		} else {
			content
		}

		val newMessage = DisplayMessage(finalContent, profile, System.nanoTime())
		allMessages.add(newMessage)

		if (allMessages.size > config.chatLog.maxLogSize) {
			allMessages.removeFirst()
		}

		if (!client.options.hudHidden) {
			currentHudMessage = newMessage
		}
	}
}