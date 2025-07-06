package com.nichat

import com.mojang.authlib.GameProfile
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
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

object NiChatClient : ClientModInitializer {

	internal val allMessages: MutableList<DisplayMessage> = mutableListOf()
	private var currentHudMessage: DisplayMessage? = null
	private val client: MinecraftClient = MinecraftClient.getInstance()
	private lateinit var toggleChatLogKey: KeyBinding

	private const val HUD_MESSAGE_DURATION_SECONDS = 5L
	internal val SYSTEM_PROFILE = GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), "System")

	override fun onInitializeClient() {
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
			if (client.options.hudHidden) {
				return@register
			}

			if (client.currentScreen != null) {
				currentHudMessage = null
				return@register
			}

			val messageToRender = currentHudMessage ?: return@register

			if (System.nanoTime() - messageToRender.receivedTimeNano > TimeUnit.SECONDS.toNanos(HUD_MESSAGE_DURATION_SECONDS)) {
				currentHudMessage = null
				return@register
			}

			val font = client.textRenderer
			val screenWidth = context.scaledWindowWidth
			val maxTextWidth = (screenWidth * 0.5f).toInt()

			val headSize = 16
			val padding = 3
			val headTextSpacing = 4
			val drawHead = messageToRender.senderProfile != SYSTEM_PROFILE

			val lines = font.wrapLines(messageToRender.content, maxTextWidth)
			val textWidth = lines.maxOfOrNull { font.getWidth(it) } ?: 0

			val headSpace = headSize + headTextSpacing
			val boxWidth = headSpace + textWidth + padding * 2
			val boxHeight = max(font.fontHeight * lines.size, headSize) + padding * 2

			val posX = (context.scaledWindowWidth - boxWidth) / 2
			val posY = context.scaledWindowHeight - boxHeight - 40

			context.fill(posX, posY, posX + boxWidth, posY + boxHeight, client.options.getTextBackgroundColor(0.7f))

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

			var textTopY = contentTopY + (boxHeight - padding * 2 - lines.size * font.fontHeight) / 2
			for (line in lines) {
				context.drawTextWithShadow(font, line, textLeftX, textTopY, 0xFFFFFFFF.toInt())
				textTopY += font.fontHeight
			}
		}
	}

	private fun processNewMessage(content: Text, profile: GameProfile) {
		if (content.string.isBlank()) {
			return
		}

		val newMessage = DisplayMessage(content, profile, System.nanoTime())
		allMessages.add(newMessage)
		if (allMessages.size > 300) {
			allMessages.removeFirst()
		}

		if (!client.options.hudHidden) {
			currentHudMessage = newMessage
		}
	}
}