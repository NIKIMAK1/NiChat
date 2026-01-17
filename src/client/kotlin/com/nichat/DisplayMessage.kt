package com.nichat

import com.mojang.authlib.GameProfile
import net.minecraft.network.chat.Component

data class DisplayMessage(
    val content: Component,
    val senderProfile: GameProfile,
    val receivedTimeNano: Long
)