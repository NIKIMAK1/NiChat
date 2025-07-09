package com.nichat

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.gui.screen.ChatInputSuggestor
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.text.Style
import kotlin.math.max

class ChatLogScreen : ChatScreen("") {

    private var scrollOffset = 0.0
    private var totalContentHeight = 0.0
    private val mc: MinecraftClient = MinecraftClient.getInstance()
    private var foundSuggestor: ChatInputSuggestor? = null

    private val config = NiChatClient.config.chatLog
    private val chatInputAreaHeight = 14

    override fun init() {
        super.init()
        this.foundSuggestor = this.children().filterIsInstance<ChatInputSuggestor>().firstOrNull()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollOffset = 0.0
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (this.foundSuggestor?.mouseScrolled(verticalAmount) == true) return true
        recalculateTotalContentHeight()
        val availableLogHeight = this.height - chatInputAreaHeight - config.logChatLogInputPadding
        val maxScroll = max(0.0, totalContentHeight - availableLogHeight)
        val scrollAmount = verticalAmount * mc.textRenderer.fontHeight * config.logScrollSpeed
        scrollOffset = (scrollOffset + scrollAmount).coerceIn(0.0, maxScroll)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        getStyleAt(mouseX, mouseY)?.let {
            if (this.handleTextClick(it)) return true
        }
        return false
    }

    private fun getStyleAt(mouseX: Double, mouseY: Double): Style? {
        val font = mc.textRenderer
        val maxTextWidth = (this.width * config.logWidthScale).toInt()
        val logBottomY = this.height - chatInputAreaHeight - config.logChatLogInputPadding
        val logTopY = 0
        var cursorY = (logBottomY + scrollOffset).toInt()

        for (messageToRender in NiChatClient.allMessages.reversed()) {
            val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val widestLine = lines.maxOfOrNull { font.getWidth(it) } ?: 0

            val headSpace = if (drawHead) config.logHeadSize + config.logHeadTextSpacing else 0
            val textBlockHeight = lines.size * font.fontHeight
            val contentHeight = max(textBlockHeight, config.logHeadSize)
            val totalBlockWidth = headSpace + widestLine + config.logPadding * 2
            val totalBlockHeight = contentHeight + config.logPadding * 2

            val blockBottomY = cursorY
            val blockTopY = blockBottomY - totalBlockHeight

            if (mouseY >= blockTopY && mouseY < blockBottomY && blockTopY < logBottomY && blockBottomY > logTopY) {
                val blockLeftX = config.logPadding
                val textBlockLeftX = if (drawHead) {
                    blockLeftX + config.logPadding + headSpace
                } else {
                    blockLeftX + (totalBlockWidth - widestLine) / 2
                }
                val contentTopY = blockTopY + config.logPadding
                val textOffsetY = (contentHeight - textBlockHeight) / 2

                lines.forEachIndexed { index, line ->
                    val lineTopY = contentTopY + textOffsetY + (index * font.fontHeight)
                    val lineBottomY = lineTopY + font.fontHeight
                    if (mouseY >= lineTopY && mouseY < lineBottomY) {
                        val relativeMouseX = (mouseX - textBlockLeftX).toInt()
                        if (relativeMouseX >= 0) {
                            return font.textHandler.getStyleAt(line, relativeMouseX)
                        }
                    }
                }
            }
            cursorY = blockTopY - config.logPaddingBetweenMessages
        }
        return null
    }

    private fun recalculateTotalContentHeight() {
        val font = mc.textRenderer
        val maxTextWidth = (width * config.logWidthScale).toInt()
        var calculatedHeight = 0.0

        if (NiChatClient.allMessages.isNotEmpty()) {
            NiChatClient.allMessages.forEach { msg ->
                val lines = font.wrapLines(msg.content, maxTextWidth)
                val textBlockHeight = lines.size * font.fontHeight
                val contentHeight = max(textBlockHeight, config.logHeadSize)
                calculatedHeight += contentHeight + config.logPadding * 2 + config.logPaddingBetweenMessages
            }
            if (calculatedHeight > 0) calculatedHeight -= config.logPaddingBetweenMessages
        }
        totalContentHeight = calculatedHeight
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        this.renderBackground(context, mouseX, mouseY, delta)
        recalculateTotalContentHeight()

        val font = mc.textRenderer
        val screenWidth = context.scaledWindowWidth
        val maxTextWidth = (screenWidth * config.logWidthScale).toInt()
        val logBottomY = this.height - chatInputAreaHeight - config.logChatLogInputPadding
        val logTopY = 0

        context.enableScissor(logTopY, logTopY, screenWidth, logBottomY)
        var cursorY = (logBottomY + scrollOffset).toInt()

        for (messageToRender in NiChatClient.allMessages.reversed()) {
            val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val widestLine = lines.maxOfOrNull { font.getWidth(it) } ?: 0

            val headSpace = if (drawHead) config.logHeadSize + config.logHeadTextSpacing else 0
            val textBlockHeight = lines.size * font.fontHeight
            val contentHeight = max(textBlockHeight, config.logHeadSize)
            val totalBlockWidth = headSpace + widestLine + config.logPadding * 2
            val totalBlockHeight = contentHeight + config.logPadding * 2

            val blockBottomY = cursorY
            val blockTopY = blockBottomY - totalBlockHeight

            if (blockTopY > logBottomY) {
                cursorY = blockTopY - config.logPaddingBetweenMessages
                continue
            }
            if (blockBottomY < logTopY) break

            val blockLeftX = config.logPadding
            context.fill(blockLeftX, blockTopY, blockLeftX + totalBlockWidth, blockBottomY, mc.options.getTextBackgroundColor(config.logBackgroundOpacity))

            val contentTopY = blockTopY + config.logPadding
            val textLeftX: Int

            if (drawHead) {
                val profile = messageToRender.senderProfile
                val playerListEntry = mc.networkHandler?.getPlayerListEntry(profile.id)
                val skinTextures = playerListEntry?.skinTextures ?: DefaultSkinHelper.getSkinTextures(profile.id)
                val headY = contentTopY + (contentHeight - config.logHeadSize) / 2
                PlayerSkinDrawer.draw(context, skinTextures, blockLeftX + config.logPadding, headY, config.logHeadSize)
                textLeftX = blockLeftX + config.logPadding + headSpace
            } else {
                textLeftX = blockLeftX + (totalBlockWidth - widestLine) / 2
            }

            val textOffsetY = (contentHeight - textBlockHeight) / 2 + 1
            var lineY = contentTopY + textOffsetY
            for (line in lines) {
                context.drawTextWithShadow(font, line, textLeftX, lineY, 0xFFFFFFFF.toInt())
                lineY += font.fontHeight
            }
            cursorY = blockTopY - config.logPaddingBetweenMessages
        }
        context.disableScissor()
        context.fill(2, this.height - chatInputAreaHeight, this.width - 2, this.height - 2, this.client!!.options.getTextBackgroundColor(config.logBackgroundOpacity))
        this.chatField.render(context, mouseX, mouseY, delta)
        this.foundSuggestor?.render(context, mouseX, mouseY)
        getStyleAt(mouseX.toDouble(), mouseY.toDouble())?.let { style ->
            if (style.hoverEvent != null) {
                context.drawHoverEvent(font, style, mouseX, mouseY)
            }
        }
    }
}