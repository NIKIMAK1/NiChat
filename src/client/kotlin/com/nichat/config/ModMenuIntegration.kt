package com.nichat.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.autoconfig.AutoConfig

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            AutoConfig.getConfigScreen(NiChatConfig::class.java, parent).get()
        }
    }
}