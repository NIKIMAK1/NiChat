package com.nichat

import net.minecraft.client.gui.Font

object MessageLayoutCache {
    private data class LayoutKey(
        val maxTextWidth: Int,
        val headSize: Int,
        val headTextSpacing: Int,
        val padding: Int,
        val style: LayoutStyle
    )

    private val cache = mutableMapOf<LayoutKey, MutableMap<DisplayMessage, MessageBlock>>()

    @JvmStatic
    fun getBlocks(
        font: Font,
        messages: List<DisplayMessage>,
        maxTextWidth: Int,
        headSize: Int,
        headTextSpacing: Int,
        padding: Int
    ): List<MessageBlock> {
        val style = MessageLayout.currentStyle()
        val key = LayoutKey(maxTextWidth, headSize, headTextSpacing, padding, style)
        val blocksByMessage = cache.getOrPut(key) { mutableMapOf() }
        if (blocksByMessage.size > messages.size * 2) {
            blocksByMessage.keys.retainAll(messages.toSet())
        }

        return messages.map { message ->
            blocksByMessage.getOrPut(message) {
                MessageLayout.buildBlock(
                    font = font,
                    message = message,
                    maxTextWidth = maxTextWidth,
                    headSize = headSize,
                    headTextSpacing = headTextSpacing,
                    padding = padding,
                    style = style
                )
            }
        }
    }

    @JvmStatic
    fun invalidateMessage(message: DisplayMessage) {
        cache.values.forEach { it.remove(message) }
    }

    @JvmStatic
    fun invalidateAll() {
        cache.clear()
    }
}
