package com.nichat

import com.mojang.authlib.GameProfile
import net.minecraft.network.chat.Component

data class DisplayMessage(
    /** Message reformatted by NiChat (nickname colors, sender detection). */
    val content: Component,
    val senderProfile: GameProfile,
    val receivedTimeNano: Long,
    /** Message exactly as the game delivered it. */
    val originalContent: Component = content
) {
    /** Content to show for the given style settings. */
    fun displayContent(formatted: Boolean): Component = if (formatted) content else originalContent
}
