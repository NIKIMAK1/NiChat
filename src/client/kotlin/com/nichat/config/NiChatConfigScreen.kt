package com.nichat.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class NiChatConfigScreen(
    private val parent: Screen?
) : Screen(Component.translatable("nichat.config.title")) {

    private enum class Category {
        GENERAL,
        HUD,
        CHAT_LOG
    }

    private data class EntryItem(
        val label: Component,
        val widget: AbstractWidget,
        val width: Int
    )

    private val initialConfig = NiChatConfig().also { copyConfig(it, NiChatConfigManager.config) }
    private var selectedCategory: Category = Category.GENERAL
    private var configList: ConfigList? = null

    private var nicknameColorBox: EditBox? = null
    private var randomNicknameColorButton: CycleButton<Boolean>? = null

    private var hudMessageDurationBox: EditBox? = null
    private var hudMaxMessagesBox: EditBox? = null
    private var hudBackgroundOpacityBox: EditBox? = null
    private var hudWidthScaleBox: EditBox? = null
    private var hudFadeInDurationMsBox: EditBox? = null
    private var hudFadeOutDurationMsBox: EditBox? = null
    private var hudAnimationOffsetPxBox: EditBox? = null
    private var hudVerticalOffsetBox: EditBox? = null
    private var hudHorizontalAlignmentButton: CycleButton<HorizontalAlignment>? = null
    private var hudHeadSizeBox: EditBox? = null
    private var hudPaddingBox: EditBox? = null
    private var hudHeadTextSpacingBox: EditBox? = null
    private var hudPaddingBetweenMessagesBox: EditBox? = null

    private var maxLogSizeBox: EditBox? = null
    private var logBackgroundOpacityBox: EditBox? = null
    private var logWidthScaleBox: EditBox? = null
    private var logScrollSpeedBox: EditBox? = null
    private var logHeadSizeBox: EditBox? = null
    private var logPaddingBox: EditBox? = null
    private var logHeadTextSpacingBox: EditBox? = null
    private var logPaddingBetweenMessagesBox: EditBox? = null
    private var logChatLogInputPaddingBox: EditBox? = null

    override fun init() {
        super.init()

        val tabY = 28
        val tabWidth = 98
        val tabGap = 6
        val tabStartX = this.width / 2 - (tabWidth * 3 + tabGap * 2) / 2
        val listTop = tabY + 30
        val bottomY = this.height - 27
        val listBottom = bottomY - 8

        addRenderableWidget(categoryButton(tabStartX, tabY, tabWidth, Category.GENERAL, Component.translatable("nichat.config.category.general")))
        addRenderableWidget(categoryButton(tabStartX + tabWidth + tabGap, tabY, tabWidth, Category.HUD, Component.translatable("nichat.config.category.hud")))
        addRenderableWidget(categoryButton(tabStartX + (tabWidth + tabGap) * 2, tabY, tabWidth, Category.CHAT_LOG, Component.translatable("nichat.config.category.chat_log")))

        configList = addRenderableWidget(ConfigList(Minecraft.getInstance(), this.width, listTop, listBottom).also { list ->
            populateEntries(list)
        })

        addRenderableWidget(
            Button.builder(Component.translatable("nichat.config.reset")) {
                resetCurrentCategory()
                rebuildWidgets()
            }.bounds(this.width / 2 - 155, bottomY, 100, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) {
                closeWithoutSaving()
            }.bounds(this.width / 2 - 50, bottomY, 100, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) {
                saveAndClose()
            }.bounds(this.width / 2 + 55, bottomY, 100, 20).build()
        )
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractMenuBackground(guiGraphics)
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFF)
    }

    override fun onClose() {
        closeWithoutSaving()
    }

    private fun closeWithoutSaving() {
        copyConfig(NiChatConfigManager.config, initialConfig)
        Minecraft.getInstance().setScreen(parent)
    }

    private fun categoryButton(x: Int, y: Int, width: Int, category: Category, label: Component): Button {
        return Button.builder(label) {
            applyCurrentCategoryInputs()
            selectedCategory = category
            rebuildWidgets()
        }.bounds(x, y, width, 20).build().apply {
            active = category != selectedCategory
        }
    }

    private fun populateEntries(list: ConfigList) {
        when (selectedCategory) {
            Category.GENERAL -> initGeneralFields(list)
            Category.HUD -> initHudFields(list)
            Category.CHAT_LOG -> initChatLogFields(list)
        }
    }

    private fun initGeneralFields(list: ConfigList) {
        val config = NiChatConfigManager.config.general
        nicknameColorBox = createTextField(Component.translatable("nichat.config.option.general.nicknameColor"), config.nicknameColor, 250)
        list.addSingleRow(Component.translatable("nichat.config.option.general.nicknameColor"), nicknameColorBox!!, 250)

        randomNicknameColorButton = createBooleanField(
            Component.translatable("nichat.config.option.general.randomNicknameColor"),
            config.randomNicknameColor,
            250
        )
        list.addSingleRow(Component.translatable("nichat.config.option.general.randomNicknameColor"), randomNicknameColorButton!!, 250)
    }

    private fun initHudFields(list: ConfigList) {
        val config = NiChatConfigManager.config.hud
        hudMessageDurationBox = createTextField(Component.translatable("nichat.config.option.hud.hudMessageDuration"), config.hudMessageDuration.toString())
        hudMaxMessagesBox = createTextField(Component.translatable("nichat.config.option.hud.hudMaxMessages"), config.hudMaxMessages.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.hud.hudMessageDuration"), hudMessageDurationBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.hud.hudMaxMessages"), hudMaxMessagesBox!!, 150)
        )

        hudBackgroundOpacityBox = createTextField(Component.translatable("nichat.config.option.hud.hudBackgroundOpacity"), config.hudBackgroundOpacity.toString())
        hudWidthScaleBox = createTextField(Component.translatable("nichat.config.option.hud.hudWidthScale"), config.hudWidthScale.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.hud.hudBackgroundOpacity"), hudBackgroundOpacityBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.hud.hudWidthScale"), hudWidthScaleBox!!, 150)
        )

        hudFadeInDurationMsBox = createTextField(Component.translatable("nichat.config.option.hud.hudFadeInDurationMs"), config.hudFadeInDurationMs.toString())
        hudFadeOutDurationMsBox = createTextField(Component.translatable("nichat.config.option.hud.hudFadeOutDurationMs"), config.hudFadeOutDurationMs.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.hud.hudFadeInDurationMs"), hudFadeInDurationMsBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.hud.hudFadeOutDurationMs"), hudFadeOutDurationMsBox!!, 150)
        )

        hudAnimationOffsetPxBox = createTextField(Component.translatable("nichat.config.option.hud.hudAnimationOffsetPx"), config.hudAnimationOffsetPx.toString(), 250)
        list.addSingleRow(Component.translatable("nichat.config.option.hud.hudAnimationOffsetPx"), hudAnimationOffsetPxBox!!, 250)

        hudVerticalOffsetBox = createTextField(Component.translatable("nichat.config.option.hud.hudVerticalOffset"), config.hudVerticalOffset.toString())
        hudHorizontalAlignmentButton = createCycleField(
            Component.translatable("nichat.config.option.hud.hudHorizontalAlignment"),
            HorizontalAlignment.entries.toList(),
            config.hudHorizontalAlignment,
            150
        ) { alignment ->
            Component.translatable("nichat.config.enum.HorizontalAlignment.${alignment.name}")
        }
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.hud.hudVerticalOffset"), hudVerticalOffsetBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.hud.hudHorizontalAlignment"), hudHorizontalAlignmentButton!!, 150)
        )

        hudHeadSizeBox = createTextField(Component.translatable("nichat.config.option.hud.hudHeadSize"), config.hudHeadSize.toString())
        hudPaddingBox = createTextField(Component.translatable("nichat.config.option.hud.hudPadding"), config.hudPadding.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.hud.hudHeadSize"), hudHeadSizeBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.hud.hudPadding"), hudPaddingBox!!, 150)
        )

        hudHeadTextSpacingBox = createTextField(Component.translatable("nichat.config.option.hud.hudHeadTextSpacing"), config.hudHeadTextSpacing.toString())
        hudPaddingBetweenMessagesBox = createTextField(Component.translatable("nichat.config.option.hud.hudPaddingBetweenMessages"), config.hudPaddingBetweenMessages.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.hud.hudHeadTextSpacing"), hudHeadTextSpacingBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.hud.hudPaddingBetweenMessages"), hudPaddingBetweenMessagesBox!!, 150)
        )
    }

    private fun initChatLogFields(list: ConfigList) {
        val config = NiChatConfigManager.config.chatLog
        maxLogSizeBox = createTextField(Component.translatable("nichat.config.option.chatLog.maxLogSize"), config.maxLogSize.toString())
        logBackgroundOpacityBox = createTextField(Component.translatable("nichat.config.option.chatLog.logBackgroundOpacity"), config.logBackgroundOpacity.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.chatLog.maxLogSize"), maxLogSizeBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.chatLog.logBackgroundOpacity"), logBackgroundOpacityBox!!, 150)
        )

        logWidthScaleBox = createTextField(Component.translatable("nichat.config.option.chatLog.logWidthScale"), config.logWidthScale.toString())
        logScrollSpeedBox = createTextField(Component.translatable("nichat.config.option.chatLog.logScrollSpeed"), config.logScrollSpeed.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.chatLog.logWidthScale"), logWidthScaleBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.chatLog.logScrollSpeed"), logScrollSpeedBox!!, 150)
        )

        logHeadSizeBox = createTextField(Component.translatable("nichat.config.option.chatLog.logHeadSize"), config.logHeadSize.toString())
        logPaddingBox = createTextField(Component.translatable("nichat.config.option.chatLog.logPadding"), config.logPadding.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.chatLog.logHeadSize"), logHeadSizeBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.chatLog.logPadding"), logPaddingBox!!, 150)
        )

        logHeadTextSpacingBox = createTextField(Component.translatable("nichat.config.option.chatLog.logHeadTextSpacing"), config.logHeadTextSpacing.toString())
        logPaddingBetweenMessagesBox = createTextField(Component.translatable("nichat.config.option.chatLog.logPaddingBetweenMessages"), config.logPaddingBetweenMessages.toString())
        list.addDoubleRow(
            EntryItem(Component.translatable("nichat.config.option.chatLog.logHeadTextSpacing"), logHeadTextSpacingBox!!, 150),
            EntryItem(Component.translatable("nichat.config.option.chatLog.logPaddingBetweenMessages"), logPaddingBetweenMessagesBox!!, 150)
        )

        logChatLogInputPaddingBox = createTextField(Component.translatable("nichat.config.option.chatLog.logChatLogInputPadding"), config.logChatLogInputPadding.toString(), 250)
        list.addSingleRow(Component.translatable("nichat.config.option.chatLog.logChatLogInputPadding"), logChatLogInputPaddingBox!!, 250)
    }

    private fun createTextField(label: Component, value: String, width: Int = 150): EditBox {
        return EditBox(this.font, 0, 0, width, 16, label).apply {
            setValue(value)
        }
    }

    private fun createBooleanField(label: Component, value: Boolean, width: Int): CycleButton<Boolean> {
        return createCycleField(label, listOf(false, true), value, width) { current ->
            if (current) Component.translatable("options.on") else Component.translatable("options.off")
        }
    }

    private fun <T : Any> createCycleField(
        label: Component,
        values: List<T>,
        initialValue: T,
        width: Int,
        valueToText: (T) -> Component = { Component.literal(it.toString()) }
    ): CycleButton<T> {
        return CycleButton.builder({ value: T -> valueToText(value) }, initialValue)
            .displayOnlyValue()
            .withValues(values)
            .create(0, 0, width, 16, Component.empty())
    }

    private fun resetCurrentCategory() {
        val defaults = NiChatConfig()
        val config = NiChatConfigManager.config
        when (selectedCategory) {
            Category.GENERAL -> {
                config.general.nicknameColor = defaults.general.nicknameColor
                config.general.randomNicknameColor = defaults.general.randomNicknameColor
            }
            Category.HUD -> {
                config.hud.hudMessageDuration = defaults.hud.hudMessageDuration
                config.hud.hudMaxMessages = defaults.hud.hudMaxMessages
                config.hud.hudBackgroundOpacity = defaults.hud.hudBackgroundOpacity
                config.hud.hudWidthScale = defaults.hud.hudWidthScale
                config.hud.hudFadeInDurationMs = defaults.hud.hudFadeInDurationMs
                config.hud.hudFadeOutDurationMs = defaults.hud.hudFadeOutDurationMs
                config.hud.hudAnimationOffsetPx = defaults.hud.hudAnimationOffsetPx
                config.hud.hudVerticalOffset = defaults.hud.hudVerticalOffset
                config.hud.hudHorizontalAlignment = defaults.hud.hudHorizontalAlignment
                config.hud.hudHeadSize = defaults.hud.hudHeadSize
                config.hud.hudPadding = defaults.hud.hudPadding
                config.hud.hudHeadTextSpacing = defaults.hud.hudHeadTextSpacing
                config.hud.hudPaddingBetweenMessages = defaults.hud.hudPaddingBetweenMessages
            }
            Category.CHAT_LOG -> {
                config.chatLog.maxLogSize = defaults.chatLog.maxLogSize
                config.chatLog.logBackgroundOpacity = defaults.chatLog.logBackgroundOpacity
                config.chatLog.logWidthScale = defaults.chatLog.logWidthScale
                config.chatLog.logScrollSpeed = defaults.chatLog.logScrollSpeed
                config.chatLog.logHeadSize = defaults.chatLog.logHeadSize
                config.chatLog.logPadding = defaults.chatLog.logPadding
                config.chatLog.logHeadTextSpacing = defaults.chatLog.logHeadTextSpacing
                config.chatLog.logPaddingBetweenMessages = defaults.chatLog.logPaddingBetweenMessages
                config.chatLog.logChatLogInputPadding = defaults.chatLog.logChatLogInputPadding
            }
        }
    }

    private fun saveAndClose() {
        applyCurrentCategoryInputs()
        NiChatConfigManager.save()
        Minecraft.getInstance().setScreen(parent)
    }

    private fun applyCurrentCategoryInputs() {
        val config = NiChatConfigManager.config
        when (selectedCategory) {
            Category.GENERAL -> {
                val general = config.general
                general.nicknameColor = nicknameColorBox?.value?.trim()?.ifBlank { general.nicknameColor } ?: general.nicknameColor
                general.randomNicknameColor = randomNicknameColorButton?.value ?: general.randomNicknameColor
            }
            Category.HUD -> {
                val hud = config.hud
                hud.hudMessageDuration = parseLong(hudMessageDurationBox?.value, hud.hudMessageDuration)
                hud.hudMaxMessages = parseInt(hudMaxMessagesBox?.value, hud.hudMaxMessages)
                hud.hudBackgroundOpacity = parseFloat(hudBackgroundOpacityBox?.value, hud.hudBackgroundOpacity)
                hud.hudWidthScale = parseFloat(hudWidthScaleBox?.value, hud.hudWidthScale)
                hud.hudFadeInDurationMs = parseLong(hudFadeInDurationMsBox?.value, hud.hudFadeInDurationMs)
                hud.hudFadeOutDurationMs = parseLong(hudFadeOutDurationMsBox?.value, hud.hudFadeOutDurationMs)
                hud.hudAnimationOffsetPx = parseInt(hudAnimationOffsetPxBox?.value, hud.hudAnimationOffsetPx)
                hud.hudVerticalOffset = parseInt(hudVerticalOffsetBox?.value, hud.hudVerticalOffset)
                hud.hudHorizontalAlignment = hudHorizontalAlignmentButton?.value ?: hud.hudHorizontalAlignment
                hud.hudHeadSize = parseInt(hudHeadSizeBox?.value, hud.hudHeadSize)
                hud.hudPadding = parseInt(hudPaddingBox?.value, hud.hudPadding)
                hud.hudHeadTextSpacing = parseInt(hudHeadTextSpacingBox?.value, hud.hudHeadTextSpacing)
                hud.hudPaddingBetweenMessages = parseInt(hudPaddingBetweenMessagesBox?.value, hud.hudPaddingBetweenMessages)
            }
            Category.CHAT_LOG -> {
                val chatLog = config.chatLog
                chatLog.maxLogSize = parseInt(maxLogSizeBox?.value, chatLog.maxLogSize)
                chatLog.logBackgroundOpacity = parseFloat(logBackgroundOpacityBox?.value, chatLog.logBackgroundOpacity)
                chatLog.logWidthScale = parseFloat(logWidthScaleBox?.value, chatLog.logWidthScale)
                chatLog.logScrollSpeed = parseDouble(logScrollSpeedBox?.value, chatLog.logScrollSpeed)
                chatLog.logHeadSize = parseInt(logHeadSizeBox?.value, chatLog.logHeadSize)
                chatLog.logPadding = parseInt(logPaddingBox?.value, chatLog.logPadding)
                chatLog.logHeadTextSpacing = parseInt(logHeadTextSpacingBox?.value, chatLog.logHeadTextSpacing)
                chatLog.logPaddingBetweenMessages = parseInt(logPaddingBetweenMessagesBox?.value, chatLog.logPaddingBetweenMessages)
                chatLog.logChatLogInputPadding = parseInt(logChatLogInputPaddingBox?.value, chatLog.logChatLogInputPadding)
            }
        }
    }

    private fun parseInt(value: String?, current: Int): Int = value?.trim()?.toIntOrNull() ?: current
    private fun parseLong(value: String?, current: Long): Long = value?.trim()?.toLongOrNull() ?: current
    private fun parseDouble(value: String?, current: Double): Double = value?.trim()?.toDoubleOrNull() ?: current
    private fun parseFloat(value: String?, current: Float): Float = value?.trim()?.toFloatOrNull() ?: current

    private fun copyConfig(target: NiChatConfig, source: NiChatConfig) {
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

    private inner class ConfigList(
        minecraft: Minecraft,
        width: Int,
        top: Int,
        bottom: Int
    ) : ContainerObjectSelectionList<ConfigEntry>(minecraft, width, bottom - top, top, 42) {

        init {
            centerListVertically = false
        }

        override fun getRowWidth(): Int = 340

        fun addSingleRow(label: Component, widget: AbstractWidget, widgetWidth: Int) {
            addEntry(ConfigEntry(EntryItem(label, widget, widgetWidth), null))
        }

        fun addDoubleRow(left: EntryItem, right: EntryItem) {
            addEntry(ConfigEntry(left, right))
        }
    }

    private inner class ConfigEntry(
        private val left: EntryItem,
        private val right: EntryItem?
    ) : ContainerObjectSelectionList.Entry<ConfigEntry>() {

        private val widgets = listOfNotNull(left.widget, right?.widget)

        override fun extractContent(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
            renderItem(guiGraphics, left, if (right == null) getContentXMiddle() - (left.width + 14) / 2 else getContentX() + 6, getContentY(), mouseX, mouseY, partialTick)

            if (right != null) {
                renderItem(guiGraphics, right, getContentRight() - right.width - 20, getContentY(), mouseX, mouseY, partialTick)
            }
        }

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

        private fun renderItem(
            guiGraphics: GuiGraphicsExtractor,
            item: EntryItem,
            x: Int,
            y: Int,
            mouseX: Int,
            mouseY: Int,
            partialTick: Float
        ) {
            val boxWidth = item.width + 14
            guiGraphics.fill(x, y, x + boxWidth, y + 38, 0x5A101318)
            guiGraphics.fill(x, y, x + boxWidth, y + 1, 0x90FFFFFF.toInt())
            guiGraphics.textRenderer().acceptScrollingWithDefaultCenter(
                item.label,
                x + 6,
                x + boxWidth - 6,
                y + 4,
                y + 4 + this@NiChatConfigScreen.font.lineHeight
            )

            item.widget.setX(x + 7)
            item.widget.setY(y + 18)
            item.widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
        }
    }
}
