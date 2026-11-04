package com.nichat.translation

import com.nichat.NiChatClient
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.chat.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture

object OutgoingTranslation {
    private val TIMEOUT: Duration = Duration.ofSeconds(4)
    private var pending: CompletableFuture<*> = CompletableFuture.completedFuture(null)

    @JvmStatic
    fun sendChat(connection: ClientPacketListener, message: String) {
        val settings = NiChatClient.config.translation
        if (!settings.translateOutgoing && pending.isDone) {
            connection.sendChat(message)
            return
        }

        val translation = if (settings.translateOutgoing) {
            Translator.translateNow(message, settings.outgoingLanguage, TIMEOUT)
                .handle { translated, error -> Outcome(translated, error != null) }
        } else {
            CompletableFuture.completedFuture(Outcome(null, false))
        }

        pending = pending
            .thenCombine(translation) { _, outcome -> outcome }
            .thenAcceptAsync({ outcome -> send(message, outcome) }, Minecraft.getInstance())
            .exceptionally { error ->
                NiChatClient.logger.warn("Failed to send chat message", error)
                null
            }
    }

    private class Outcome(val translated: String?, val failed: Boolean)

    private fun send(original: String, outcome: Outcome) {
        val client = Minecraft.getInstance()
        val connection = client.player?.connection ?: return
        val text = outcome.translated?.take(256) ?: original
        connection.sendChat(text)

        if (outcome.failed) {
            client.gui.hud.chat.addClientSystemMessage(Component.translatable("nichat.translation.outgoingFailed"))
        }
    }
}
