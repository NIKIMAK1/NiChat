package com.nichat

import com.mojang.authlib.GameProfile
import net.minecraft.text.Text

data class DisplayMessage(
    val content: Text,
    val senderProfile: GameProfile,
    val receivedTimeNano: Long
)