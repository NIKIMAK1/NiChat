package com.nichat

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import java.util.UUID

object ServerChatSender {
    private const val MAX_SENDER_PART_LENGTH = 64
    private val SENDER_PART = Regex("""^(.*?)([A-Za-z0-9_]{1,16})(?: \[[^\]]{1,12}\])?$""")
    private val KNOWN_PREFIX = Regex(
        """^(?:(?:Guild|Party|Officer|Co-op|Coop) > |From |To )?(?:(?:\[[^\]]{1,24}\]|[^\p{L}\p{N}\s\[\]]{1,3}) )*$"""
    )

    class Result(val profile: GameProfile, val content: Component)

    fun detect(message: Component, nicknameColor: (String) -> TextColor): Result? {
        val styled = StyledText.of(message)
        val plain = styled.plain

        val separatorIndex = plain.indexOf(": ")
        if (separatorIndex !in 1..MAX_SENDER_PART_LENGTH) return null

        val match = SENDER_PART.find(plain.substring(0, separatorIndex)) ?: return null
        val prefix = match.groupValues[1]
        val name = match.groupValues[2]
        if (prefix.isNotEmpty() && prefix.last() !in " ]>") return null
        if (prefix.contains("[NPC]", ignoreCase = true)) return null

        val playerInfo = Minecraft.getInstance().connection?.getPlayerInfo(name)
        val looksLikePlayerChat = prefix.isNotBlank() && KNOWN_PREFIX.matches(prefix)
        if (playerInfo == null && !looksLikePlayerChat) return null

        val profile = playerInfo?.profile ?: GameProfile(offlineId(name), name)
        val nameStart = match.groups[2]!!.range.first
        val color = nicknameColor(name)
        val content = styled
            .restyle(nameStart, nameStart + name.length) { it.withColor(color) }
            .toComponent()

        return Result(profile, content)
    }

    private fun offlineId(name: String): UUID =
        UUID.nameUUIDFromBytes("NiChat:${name.lowercase()}".toByteArray(Charsets.UTF_8))
}
