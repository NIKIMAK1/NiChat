package com.nichat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import java.util.concurrent.TimeUnit

object ChatScreenAnimation {
    const val SLIDE_OFFSET_PX = 12f
    private const val INPUT_TEXT_COLOR = 0xFFE0E0E0.toInt()

    private var closing = false
    private var closeStartNano = 0L
    private var closeStartVisibility = 0f
    private var closeLogBottomY = 0
    private var closeScrollOffset = 0.0
    private var closeInputText = ""

    @JvmStatic var openProgress = 0f
    @JvmStatic var fadingInput: EditBox? = null
    @JvmStatic var inputOpacity = 1f

    @JvmStatic
    fun offsetFor(progress: Float): Float = (1f - progress) * SLIDE_OFFSET_PX

    @JvmStatic
    fun takeCloseVisibility(): Float {
        val visibility = closeVisibility(System.nanoTime())
        closing = false
        return visibility
    }

    @JvmStatic
    fun startClosing(openVisibility: Float, logBottomY: Int, scrollOffset: Double, inputText: String) {
        openProgress = 0f
        if (openVisibility <= 0f) return
        closing = true
        closeStartNano = System.nanoTime()
        closeStartVisibility = openVisibility
        closeLogBottomY = logBottomY
        closeScrollOffset = scrollOffset
        closeInputText = inputText
    }

    @JvmStatic
    fun renderClosing(context: GuiGraphicsExtractor, client: Minecraft): Float {
        val visibility = closeVisibility(System.nanoTime())
        if (visibility <= 0f) {
            closing = false
            return 0f
        }

        val progress = Animation.smoothStep(visibility)
        context.pose().pushMatrix()
        context.pose().translate(0f, offsetFor(progress))

        ChatHistoryRenderer.render(context, client, closeLogBottomY, closeScrollOffset, progress, null)

        val screenWidth = context.guiWidth()
        val screenHeight = context.guiHeight()
        val inputBackground = Animation.scaleColorAlpha(client.options.getBackgroundColor(Int.MIN_VALUE), progress)
        context.fill(2, screenHeight - 14, screenWidth - 2, screenHeight - 2, inputBackground)

        if (closeInputText.isNotEmpty()) {
            val font = client.font
            val maxWidth = screenWidth - 8
            val visibleText = if (font.width(closeInputText) <= maxWidth) {
                closeInputText
            } else {
                font.plainSubstrByWidth(closeInputText, maxWidth, true)
            }
            val textColor = Animation.scaleColorAlpha(INPUT_TEXT_COLOR, progress)
            context.text(font, visibleText, 4, screenHeight - 12, textColor, true)
        }

        context.pose().popMatrix()
        return progress
    }

    private fun closeVisibility(now: Long): Float {
        if (!closing) return 0f
        val durationMs = NiChatClient.config.chatLog.logOpenAnimationMs
        if (durationMs <= 0L) return 0f
        val elapsed = (now - closeStartNano).toDouble() / TimeUnit.MILLISECONDS.toNanos(durationMs).toDouble()
        return (closeStartVisibility - elapsed.toFloat()).coerceIn(0f, 1f)
    }
}
