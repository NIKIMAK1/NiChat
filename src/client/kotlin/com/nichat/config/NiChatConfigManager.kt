package com.nichat.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object NiChatConfigManager {
    private val logger = LoggerFactory.getLogger("nichat")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("nichat.json")

    @JvmStatic
    val config: NiChatConfig = NiChatConfig()

    @JvmStatic
    fun load() {
        if (!Files.exists(configPath)) {
            save()
            return
        }

        try {
            Files.newBufferedReader(configPath).use { reader ->
                val loaded = gson.fromJson(reader, NiChatConfig::class.java) ?: NiChatConfig()
                copyInto(config, loaded)
            }
        } catch (e: IOException) {
            logger.error("Failed to read config from {}", configPath, e)
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse config from {}", configPath, e)
        }
    }

    @JvmStatic
    fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.newBufferedWriter(configPath).use { writer ->
                gson.toJson(config, writer)
            }
        } catch (e: IOException) {
            logger.error("Failed to write config to {}", configPath, e)
        }
    }

    private fun copyInto(target: NiChatConfig, source: NiChatConfig) {
        target.general.nicknameColor = source.general.nicknameColor
        target.general.randomNicknameColor = source.general.randomNicknameColor

        target.hud.hudMessageDuration = source.hud.hudMessageDuration
        target.hud.hudMaxMessages = source.hud.hudMaxMessages
        target.hud.hudBackgroundOpacity = source.hud.hudBackgroundOpacity
        target.hud.hudWidthScale = source.hud.hudWidthScale
        target.hud.hudFadeInDurationMs = source.hud.hudFadeInDurationMs
        target.hud.hudFadeOutDurationMs = source.hud.hudFadeOutDurationMs
        target.hud.hudAnimationOffsetPx = source.hud.hudAnimationOffsetPx
        target.hud.hudVerticalOffset = source.hud.hudVerticalOffset
        target.hud.hudHorizontalAlignment = source.hud.hudHorizontalAlignment
        target.hud.hudHeadSize = source.hud.hudHeadSize
        target.hud.hudPadding = source.hud.hudPadding
        target.hud.hudHeadTextSpacing = source.hud.hudHeadTextSpacing
        target.hud.hudPaddingBetweenMessages = source.hud.hudPaddingBetweenMessages

        target.chatLog.maxLogSize = source.chatLog.maxLogSize
        target.chatLog.logBackgroundOpacity = source.chatLog.logBackgroundOpacity
        target.chatLog.logWidthScale = source.chatLog.logWidthScale
        target.chatLog.logScrollSpeed = source.chatLog.logScrollSpeed
        target.chatLog.logHeadSize = source.chatLog.logHeadSize
        target.chatLog.logPadding = source.chatLog.logPadding
        target.chatLog.logHeadTextSpacing = source.chatLog.logHeadTextSpacing
        target.chatLog.logPaddingBetweenMessages = source.chatLog.logPaddingBetweenMessages
        target.chatLog.logChatLogInputPadding = source.chatLog.logChatLogInputPadding
    }
}
