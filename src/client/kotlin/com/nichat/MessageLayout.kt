package com.nichat

import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max

data class MessageBlock(
    val message: DisplayMessage,
    val lines: List<FormattedCharSequence>,
    val textWidth: Int,
    val drawHead: Boolean,
    val contentHeight: Int,
    val boxWidth: Int,
    val boxHeight: Int
)

object MessageLayout {
    @JvmStatic
    fun buildBlock(
        font: Font,
        message: DisplayMessage,
        maxTextWidth: Int,
        headSize: Int,
        headTextSpacing: Int,
        padding: Int
    ): MessageBlock {
        val drawHead = message.senderProfile != NiChatClient.SYSTEM_PROFILE
        val lines = font.split(message.content, maxTextWidth)
        val textWidth = lines.maxOfOrNull(font::width) ?: 0
        val headSpace = if (drawHead) headSize + headTextSpacing else 0
        val contentHeight = max(font.lineHeight * lines.size, if (drawHead) headSize else 0)
        val boxWidth = headSpace + textWidth + padding * 2
        val boxHeight = contentHeight + padding * 2

        return MessageBlock(
            message = message,
            lines = lines,
            textWidth = textWidth,
            drawHead = drawHead,
            contentHeight = contentHeight,
            boxWidth = boxWidth,
            boxHeight = boxHeight
        )
    }

    @JvmStatic
    fun calculateTotalHeight(
        blocks: List<MessageBlock>,
        paddingBetweenMessages: Int
    ): Double {
        if (blocks.isEmpty()) {
            return 0.0
        }

        var totalHeight = 0.0
        for (block in blocks) {
            totalHeight += block.boxHeight + paddingBetweenMessages
        }

        return totalHeight - paddingBetweenMessages
    }
}
