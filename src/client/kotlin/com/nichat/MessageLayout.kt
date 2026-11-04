package com.nichat

import com.nichat.config.MessageStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max

data class MessageBlock(
    val message: DisplayMessage,
    val lines: List<FormattedCharSequence>,
    val textWidth: Int,
    val drawHead: Boolean,
    val headSize: Int,
    val contentHeight: Int,
    val boxWidth: Int,
    val boxHeight: Int,
    /** Vertical offset of the content area from the top of the box. */
    val paddingY: Int,
    /** Head position relative to the box's left edge and the content area's top. */
    val headOffsetX: Int,
    val headOffsetY: Int,
    /** Horizontal offset of the text column from the left edge of the box. */
    val textOffsetX: Int
)

/** Snapshot of the style settings that affect message layout. */
data class LayoutStyle(
    val classic: Boolean,
    val formatted: Boolean,
    val playerHeads: Boolean
)

object MessageLayout {
    // Geometry of the vanilla chat (see ChatComponent.extractRenderState).
    private const val CLASSIC_PADDING_X = 4
    private const val CLASSIC_TEXT_TOP_OFFSET = 1
    private const val CLASSIC_BOTTOM_MARGIN = 40
    private const val CLASSIC_HEAD_TEXT_SPACING = 2
    private const val CLASSIC_SCROLLBAR_WIDTH = 2
    private const val CLASSIC_HUD_DURATION_MS = 10_000L
    private const val CLASSIC_HUD_FADE_OUT_MS = 1_000L
    private const val MODERN_LOG_LEFT_X = 2
    private const val MODERN_SCROLLBAR_WIDTH = 3
    private const val MODERN_SCROLLBAR_MARGIN = 4

    @JvmStatic
    val isClassic: Boolean
        get() = NiChatClient.config.general.messageStyle == MessageStyle.CLASSIC

    @JvmStatic
    fun currentStyle(): LayoutStyle {
        val general = NiChatClient.config.general
        val classic = general.messageStyle == MessageStyle.CLASSIC
        return LayoutStyle(
            classic = classic,
            formatted = !classic || general.classicFormatting,
            playerHeads = !classic || general.classicPlayerHeads
        )
    }

    /** Width of the vanilla chat, taken from the game's "Chat Width" option. */
    private fun classicChatWidth(): Int =
        ChatComponent.getWidth(Minecraft.getInstance().options.chatWidth().get())

    @JvmStatic
    fun logTextWidth(screenWidth: Int): Int =
        if (isClassic) classicChatWidth() else (screenWidth * NiChatClient.config.chatLog.logWidthScale).toInt()

    @JvmStatic
    fun hudTextWidth(screenWidth: Int): Int =
        if (isClassic) classicChatWidth() else (screenWidth * NiChatClient.config.hud.hudWidthScale).toInt()

    /** Bottom edge of the chat history; the classic style keeps vanilla's position above the hotbar. */
    @JvmStatic
    fun logBottomY(inputY: Int, screenHeight: Int): Int =
        if (isClassic) screenHeight - CLASSIC_BOTTOM_MARGIN
        else inputY - NiChatClient.config.chatLog.logChatLogInputPadding

    /** Bottom edge of the popup stack; the classic style ignores the HUD offset setting. */
    @JvmStatic
    fun hudBottomY(screenHeight: Int): Int =
        if (isClassic) screenHeight - CLASSIC_BOTTOM_MARGIN
        else screenHeight - NiChatClient.config.hud.hudVerticalOffset

    @JvmStatic
    fun logLeftX(): Int = if (isClassic) 0 else MODERN_LOG_LEFT_X

    /** Top edge of the chat history; the classic style is limited by "Chat Height (Focused)". */
    @JvmStatic
    fun logTopY(logBottomY: Int): Int =
        if (isClassic) max(0, logBottomY - ChatComponent.getHeight(Minecraft.getInstance().options.chatHeightFocused().get()))
        else 0

    /** Top edge of the popup stack; the classic style is limited by "Chat Height (Unfocused)". */
    @JvmStatic
    fun hudTopY(hudBottomY: Int): Int =
        if (isClassic) max(0, hudBottomY - ChatComponent.getHeight(Minecraft.getInstance().options.chatHeightUnfocused().get()))
        else 0

    /** Horizontal bounds of the scrollbar: right next to the chat in classic, at the screen edge otherwise. */
    @JvmStatic
    fun scrollbarLeftX(screenWidth: Int): Int =
        if (isClassic) classicChatWidth() + CLASSIC_PADDING_X * 2
        else screenWidth - MODERN_SCROLLBAR_MARGIN - MODERN_SCROLLBAR_WIDTH

    @JvmStatic
    fun scrollbarRightX(screenWidth: Int): Int =
        scrollbarLeftX(screenWidth) + if (isClassic) CLASSIC_SCROLLBAR_WIDTH else MODERN_SCROLLBAR_WIDTH

    /** Vertical gap between the scrollbar track and the history edges. */
    @JvmStatic
    fun scrollbarMargin(): Int = if (isClassic) 0 else MODERN_SCROLLBAR_MARGIN

    /** Background opacity; the classic style follows the game's "Text Background Opacity". */
    @JvmStatic
    fun backgroundOpacity(configured: Float): Float =
        if (isClassic) Minecraft.getInstance().options.textBackgroundOpacity().get().toFloat() else configured

    /** Text opacity multiplier; the classic style follows the game's "Chat Text Opacity". */
    @JvmStatic
    fun textOpacity(): Float =
        if (isClassic) Minecraft.getInstance().options.chatOpacity().get().toFloat() * 0.9f + 0.1f else 1f

    @JvmStatic
    fun hudDurationMs(configuredSeconds: Long): Long =
        if (isClassic) CLASSIC_HUD_DURATION_MS else configuredSeconds * 1000L

    @JvmStatic
    fun hudFadeOutMs(configured: Long): Long = if (isClassic) CLASSIC_HUD_FADE_OUT_MS else configured

    /** Spacing between message boxes; the classic style stacks lines without gaps. */
    @JvmStatic
    fun spacing(configured: Int): Int = if (isClassic) 0 else configured

    @JvmStatic
    fun buildBlock(
        font: Font,
        message: DisplayMessage,
        maxTextWidth: Int,
        headSize: Int,
        headTextSpacing: Int,
        padding: Int,
        style: LayoutStyle
    ): MessageBlock {
        val content = message.displayContent(style.formatted)
        val drawHead = style.playerHeads && message.senderProfile != NiChatClient.SYSTEM_PROFILE

        if (style.classic) {
            // Inline head the size of one text line, next to the first line.
            val classicHeadSize = font.lineHeight - 1
            val headSpace = if (drawHead) classicHeadSize + CLASSIC_HEAD_TEXT_SPACING else 0
            val lines = font.split(content, maxTextWidth - headSpace)
            val textWidth = lines.maxOfOrNull(font::width) ?: 0
            val contentHeight = font.lineHeight * lines.size
            return MessageBlock(
                message = message,
                lines = lines,
                textWidth = textWidth,
                drawHead = drawHead,
                headSize = classicHeadSize,
                contentHeight = contentHeight,
                boxWidth = maxTextWidth + CLASSIC_PADDING_X * 2,
                boxHeight = contentHeight,
                paddingY = CLASSIC_TEXT_TOP_OFFSET,
                headOffsetX = CLASSIC_PADDING_X,
                headOffsetY = 0,
                textOffsetX = CLASSIC_PADDING_X + headSpace
            )
        }

        val lines = font.split(content, maxTextWidth)
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
            headSize = headSize,
            contentHeight = contentHeight,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            paddingY = padding,
            headOffsetX = padding,
            headOffsetY = (contentHeight - headSize) / 2,
            textOffsetX = if (drawHead) padding + headSpace else (boxWidth - textWidth) / 2
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

        val spacing = spacing(paddingBetweenMessages)
        var totalHeight = 0.0
        for (block in blocks) {
            totalHeight += block.boxHeight + spacing
        }

        return totalHeight - spacing
    }
}
