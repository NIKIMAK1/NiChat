package com.nichat.config

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry

enum class HorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT
}

@Config(name = "nichat")
class NiChatConfig : ConfigData {

    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.TransitiveObject
    var general: GeneralSettings = GeneralSettings()

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.TransitiveObject
    var hud: HudSettings = HudSettings()

    @ConfigEntry.Category("chat_log")
    @ConfigEntry.Gui.TransitiveObject
    var chatLog: ChatLogSettings = ChatLogSettings()

    class GeneralSettings {
        @ConfigEntry.ColorPicker
        var nicknameColor: String = "14b414"
        var randomNicknameColor: Boolean = false
    }

    class HudSettings {
        var hudMessageDuration: Long = 5L
        var hudBackgroundOpacity: Float = 0.7f
        var hudWidthScale: Float = 0.5f
        var hudVerticalOffset: Int = 50
        var hudHorizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER
        var hudHeadSize: Int = 13
        var hudPadding: Int = 5
        var hudHeadTextSpacing: Int = 4
    }

    class ChatLogSettings {
        var maxLogSize: Int = 300
        var logBackgroundOpacity: Float = 0.5f
        var logWidthScale: Float = 0.9f
        var logScrollSpeed: Double = 1.5
        var logHeadSize: Int = 13
        var logPadding: Int = 5
        var logHeadTextSpacing: Int = 5
        var logPaddingBetweenMessages: Int = 4
        var logChatLogInputPadding: Int = 5
    }
}