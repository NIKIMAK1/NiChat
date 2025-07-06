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

    private val headSize = 16
    private val padding = 4
    private val headTextSpacing = 5
    private val paddingBetweenMessages = 4
    private val textColor = 0xFFFFFFFF.toInt()
    private val backgroundOpacity = 0.8f
    private val chatInputAreaHeight = 14
    private val chatLogInputPadding = 5

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
        val availableLogHeight = this.height - chatInputAreaHeight - chatLogInputPadding
        val maxScroll = max(0.0, totalContentHeight - availableLogHeight)

        scrollOffset = (scrollOffset + verticalAmount * mc.textRenderer.fontHeight * 1.5).coerceIn(0.0, maxScroll)
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
        val screenWidth = this.width
        val maxTextWidth = (screenWidth * 0.9f).toInt()
        val logBottomY = this.height - chatInputAreaHeight - chatLogInputPadding
        val logTopY = 0

        var cursorY = (logBottomY + scrollOffset).toInt()

        for (messageToRender in NiChatClient.allMessages.reversed()) {
            val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val widestLine = lines.maxOfOrNull { font.getWidth(it) } ?: 0

            val headSpace = if (drawHead) headSize + headTextSpacing else 0
            val textBlockHeight = lines.size * font.fontHeight
            val contentHeight = max(textBlockHeight, headSize)
            val totalBlockWidth = headSpace + widestLine + padding * 2
            val totalBlockHeight = contentHeight + padding * 2

            val blockBottomY = cursorY
            val blockTopY = blockBottomY - totalBlockHeight

            if (mouseY >= blockTopY && mouseY < blockBottomY && blockTopY < logBottomY && blockBottomY > logTopY) {
                val blockLeftX = padding
                val textBlockLeftX = if (drawHead) {
                    blockLeftX + padding + headSpace
                } else {
                    blockLeftX + (totalBlockWidth - widestLine) / 2
                }

                val contentTopY = blockTopY + padding
                val textBlockOffsetY = (contentHeight - textBlockHeight) / 2

                lines.forEachIndexed { index, line ->
                    val lineTopY = contentTopY + textBlockOffsetY + (index * font.fontHeight)
                    val lineBottomY = lineTopY + font.fontHeight
                    if (mouseY >= lineTopY && mouseY < lineBottomY) {
                        val relativeMouseX = (mouseX - textBlockLeftX).toInt()
                        if (relativeMouseX >= 0) {
                            return font.textHandler.getStyleAt(line, relativeMouseX)
                        }
                    }
                }
            }
            cursorY = blockTopY - paddingBetweenMessages
        }
        return null
    }

    private fun recalculateTotalContentHeight() {
        val font = mc.textRenderer
        val maxTextWidth = (width * 0.9f).toInt()
        var calculatedHeight = 0.0

        if (NiChatClient.allMessages.isNotEmpty()) {
            NiChatClient.allMessages.forEach { msg ->
                val lines = font.wrapLines(msg.content, maxTextWidth)
                val textBlockHeight = lines.size * font.fontHeight
                val contentHeight = max(textBlockHeight, headSize)
                calculatedHeight += contentHeight + padding * 2 + paddingBetweenMessages
            }
            calculatedHeight -= paddingBetweenMessages
        }
        totalContentHeight = calculatedHeight
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        this.renderBackground(context, mouseX, mouseY, delta)
        recalculateTotalContentHeight()

        val font = mc.textRenderer
        val screenWidth = context.scaledWindowWidth
        val maxTextWidth = (screenWidth * 0.9f).toInt()
        val logBottomY = this.height - chatInputAreaHeight - chatLogInputPadding
        val logTopY = 0

        context.enableScissor(logTopY, logTopY, screenWidth, logBottomY)

        var cursorY = (logBottomY + scrollOffset).toInt()

        for (messageToRender in NiChatClient.allMessages.reversed()) {
            val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val widestLine = lines.maxOfOrNull { font.getWidth(it) } ?: 0

            val headSpace = if (drawHead) headSize + headTextSpacing else 0
            val textBlockHeight = lines.size * font.fontHeight
            val contentHeight = max(textBlockHeight, headSize)
            val totalBlockWidth = headSpace + widestLine + padding * 2
            val totalBlockHeight = contentHeight + padding * 2

            val blockBottomY = cursorY
            val blockTopY = blockBottomY - totalBlockHeight

            if (blockTopY > logBottomY) {
                cursorY = blockTopY - paddingBetweenMessages
                continue
            }
            if (blockBottomY < logTopY) break

            val blockLeftX = padding
            context.fill(blockLeftX, blockTopY, blockLeftX + totalBlockWidth, blockBottomY, mc.options.getTextBackgroundColor(backgroundOpacity))

            val contentTopY = blockTopY + padding
            val textLeftX: Int

            if (drawHead) {
                val profile = messageToRender.senderProfile
                val playerListEntry = mc.networkHandler?.getPlayerListEntry(profile.id)
                val skinTextures = playerListEntry?.skinTextures ?: DefaultSkinHelper.getSkinTextures(profile.id)
                val headY = contentTopY + (contentHeight - headSize) / 2
                PlayerSkinDrawer.draw(context, skinTextures, blockLeftX + padding, headY, headSize)
                textLeftX = blockLeftX + padding + headSpace
            } else {
                textLeftX = blockLeftX + (totalBlockWidth - widestLine) / 2
            }

            val textOffsetY = (contentHeight - textBlockHeight) / 2
            var lineY = contentTopY + textOffsetY
            for (line in lines) {
                context.drawTextWithShadow(font, line, textLeftX, lineY, textColor)
                lineY += font.fontHeight
            }

            cursorY = blockTopY - paddingBetweenMessages
        }

        context.disableScissor()

        context.fill(2, this.height - 14, this.width - 2, this.height - 2, this.client!!.options.getTextBackgroundColor(0.8f))
        this.chatField.render(context, mouseX, mouseY, delta)
        this.foundSuggestor?.render(context, mouseX, mouseY)

        getStyleAt(mouseX.toDouble(), mouseY.toDouble())?.let { style ->
            if (style.hoverEvent != null) {
                context.drawHoverEvent(font, style, mouseX, mouseY)
            }
        }
    }
}