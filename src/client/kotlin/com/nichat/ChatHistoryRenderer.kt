package com.nichat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.TextAlignment
import net.minecraft.network.chat.Style
import org.joml.Matrix3x2f
import java.util.function.Consumer
import kotlin.math.floor

object ChatHistoryRenderer {
    @JvmStatic
    fun render(
        context: GuiGraphicsExtractor,
        client: Minecraft,
        logBottomY: Int,
        scrollOffset: Double,
        opacity: Float,
        hoveredStyleConsumer: Consumer<Style>?
    ) {
        if (opacity <= 0f) return

        val config = NiChatClient.config.chatLog
        val font = client.font
        val screenWidth = context.guiWidth()
        val maxTextWidth = MessageLayout.logTextWidth(screenWidth)
        val logTopY = MessageLayout.logTopY(logBottomY)
        val textOpacity = MessageLayout.textOpacity()
        val backgroundOpacity = MessageLayout.backgroundOpacity(config.logBackgroundOpacity)

        context.enableScissor(0, logTopY, screenWidth, logBottomY)

        val textCollector = if (hoveredStyleConsumer != null) {
            context.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR, hoveredStyleConsumer)
        } else {
            context.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.NONE)
        }

        val blocks = MessageLayoutCache.getBlocks(
            font,
            NiChatClient.allMessages,
            maxTextWidth,
            config.logHeadSize,
            config.logHeadTextSpacing,
            config.logPadding
        )

        val now = System.nanoTime()
        var cursorY = (logBottomY + scrollOffset).toFloat()
        var stackShift = 0f
        val messageSpacing = MessageLayout.spacing(config.logPaddingBetweenMessages)

        for (block in blocks.asReversed()) {
            val blockSpacing = block.boxHeight + messageSpacing
            val appearTime = Animation.linear(now - block.message.receivedTimeNano, config.logFadeInDurationMs)
            stackShift += (1f - Animation.easeOutCubic(appearTime)) * blockSpacing
            val alpha = Animation.smoothStep(appearTime) * opacity

            val exactBottomY = cursorY + stackShift
            val blockBottomY = floor(exactBottomY).toInt()
            val blockTopY = blockBottomY - block.boxHeight
            cursorY -= blockSpacing

            if (alpha <= 0f || blockTopY > logBottomY || blockBottomY < logTopY) continue

            context.pose().pushMatrix()
            context.pose().translate(0f, exactBottomY - blockBottomY)

            val blockLeftX = MessageLayout.logLeftX()
            val backgroundColor = Animation.scaleColorAlpha(
                client.options.getBackgroundColor(backgroundOpacity),
                alpha
            )
            context.fill(blockLeftX, blockTopY, blockLeftX + block.boxWidth, blockBottomY, backgroundColor)

            val contentTopY = blockTopY + block.paddingY
            if (block.drawHead) {
                val headX = blockLeftX + block.headOffsetX
                val headY = contentTopY + block.headOffsetY
                NiChatClient.drawPlayerHead(context, client, block.message.senderProfile, headX, headY, block.headSize, alpha)
            }
            val textLeftX = blockLeftX + block.textOffsetX

            var lineY = contentTopY + (block.contentHeight - block.lines.size * font.lineHeight) / 2
            val textParameters = textCollector.defaultParameters()
                .withPose(Matrix3x2f(context.pose()))
                .withOpacity(alpha * textOpacity)
            for (line in block.lines) {
                textCollector.accept(TextAlignment.LEFT, textLeftX, lineY, textParameters, line)
                lineY += font.lineHeight
            }

            context.pose().popMatrix()
        }

        context.disableScissor()
    }
}
