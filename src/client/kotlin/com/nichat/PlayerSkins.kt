package com.nichat

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.util.Util
import net.minecraft.world.entity.player.PlayerSkin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object PlayerSkins {
    private val resolved = ConcurrentHashMap<String, PlayerSkin>()
    private val requested = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun get(client: Minecraft, profile: GameProfile): PlayerSkin {
        client.connection?.getPlayerInfo(profile.id)?.let { return it.skin }
        client.connection?.getPlayerInfo(profile.name)?.let { return it.skin }

        val key = profile.name.lowercase()
        resolved[key]?.let { return it }
        if (requested.add(key)) request(client, profile.name, key)

        return DefaultPlayerSkin.get(profile)
    }

    private fun request(client: Minecraft, name: String, key: String) {
        CompletableFuture
            .supplyAsync({ client.services().profileResolver().fetchByName(name).orElse(null) }, Util.nonCriticalIoPool())
            .thenCompose { profile ->
                if (profile == null) {
                    CompletableFuture.completedFuture(null)
                } else {
                    client.skinManager.get(profile).thenApply { it.orElse(null) }
                }
            }
            .thenAccept { skin -> if (skin != null) resolved[key] = skin }
            .exceptionally { error ->
                NiChatClient.logger.debug("Failed to resolve skin for {}", name, error)
                null
            }
    }
}
