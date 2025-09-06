package com.nichat.mixin

import com.nichat.NiChatClient
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.gui.hud.ChatHud
import net.minecraft.client.gui.screen.ChatInputSuggestor
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.text.Style
import net.minecraft.text.Text
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.math.max

@Mixin(ChatScreen::class)
abstract class ChatScreenMixin(title: Text?) : Screen(title) {

    @Shadow @Final protected lateinit var chatField: TextFieldWidget
    @Shadow var chatInputSuggestor: ChatInputSuggestor? = null

    @Unique private var scrollOffset = 0.0
    @Unique private var totalContentHeight = 0.0
    @Unique private val nichat_config = NiChatClient.config.chatLog
    @Unique private val nichat_mc: MinecraftClient = MinecraftClient.getInstance()

    @Inject(method = ["init"], at = [At("TAIL")])
    private fun onInit(ci: CallbackInfo) {
        scrollOffset = 0.0
    }

    @Redirect(
        method = ["render"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V"
        )
    )
    private fun redirectChatHudRender(instance: ChatHud, context: DrawContext, tickCounter: Int, mouseX: Int, mouseY: Int, focused: Boolean) {
        nichat_renderCustomChatHistory(context, mouseX, mouseY)
    }

    @Inject(method = ["mouseScrolled"], at = [At("HEAD")], cancellable = true)
    private fun onMouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double, cir: CallbackInfoReturnable<Boolean>) {
        if (this.chatInputSuggestor?.mouseScrolled(verticalAmount) == true) {
            cir.returnValue = true
            return
        }
        nichat_recalculateTotalContentHeight()
        val logBottomY = this.chatField.y - nichat_config.logChatLogInputPadding
        val availableLogHeight = logBottomY
        val maxScroll = max(0.0, totalContentHeight - availableLogHeight)
        val scrollAmount = verticalAmount * nichat_mc.textRenderer.fontHeight * nichat_config.logScrollSpeed
        scrollOffset = (scrollOffset + scrollAmount).coerceIn(0.0, maxScroll)
        cir.returnValue = true
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    private fun onMouseClicked(mouseX: Double, mouseY: Double, button: Int, cir: CallbackInfoReturnable<Boolean>) {
        val style = nichat_getStyleAt(mouseX, mouseY)
        if (style != null && this.handleTextClick(style)) {
            cir.returnValue = true
        }
    }

    @Unique
    private fun nichat_renderCustomChatHistory(context: DrawContext, mouseX: Int, mouseY: Int) {
        nichat_recalculateTotalContentHeight()
        val font = nichat_mc.textRenderer
        val screenWidth = context.scaledWindowWidth
        val maxTextWidth = (screenWidth * nichat_config.logWidthScale).toInt()
        val logBottomY = this.chatField.y - nichat_config.logChatLogInputPadding
        val logTopY = 0
        context.enableScissor(logTopY, logTopY, screenWidth, logBottomY)
        var cursorY = (logBottomY + scrollOffset).toInt()
        for (messageToRender in NiChatClient.allMessages.reversed()) {
            val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val widestLine = lines.maxOfOrNull { font.getWidth(it) } ?: 0
            val headSpace = if (drawHead) nichat_config.logHeadSize + nichat_config.logHeadTextSpacing else 0
            val textBlockHeight = lines.size * font.fontHeight
            val contentHeight = max(textBlockHeight, nichat_config.logHeadSize)
            val totalBlockWidth = headSpace + widestLine + nichat_config.logPadding * 2
            val totalBlockHeight = contentHeight + nichat_config.logPadding * 2
            val blockBottomY = cursorY
            val blockTopY = blockBottomY - totalBlockHeight
            if (blockTopY > logBottomY || blockBottomY < logTopY) {
                cursorY = blockTopY - nichat_config.logPaddingBetweenMessages
                continue
            }

            val blockLeftX = 2

            context.fill(blockLeftX, blockTopY, blockLeftX + totalBlockWidth, blockBottomY, nichat_mc.options.getTextBackgroundColor(nichat_config.logBackgroundOpacity))

            val contentTopY = blockTopY + nichat_config.logPadding
            val textLeftX: Int
            if (drawHead) {
                val profile = messageToRender.senderProfile
                val playerListEntry = nichat_mc.networkHandler?.getPlayerListEntry(profile.id)
                val skinTextures = playerListEntry?.skinTextures ?: DefaultSkinHelper.getSkinTextures(profile.id)
                val headY = contentTopY + (contentHeight - nichat_config.logHeadSize) / 2
                val headX = blockLeftX + nichat_config.logPadding
                PlayerSkinDrawer.draw(context, skinTextures, headX, headY, nichat_config.logHeadSize)
                textLeftX = headX + headSpace
            } else {
                textLeftX = blockLeftX + (totalBlockWidth - widestLine) / 2
            }
            val textOffsetY = (contentHeight - textBlockHeight) / 2 + 1
            var lineY = contentTopY + textOffsetY
            for (line in lines) {
                context.drawTextWithShadow(font, line, textLeftX, lineY, 0xFFFFFFFF.toInt())
                lineY += font.fontHeight
            }
            cursorY = blockTopY - nichat_config.logPaddingBetweenMessages
        }
        context.disableScissor()
        nichat_getStyleAt(mouseX.toDouble(), mouseY.toDouble())?.let { style ->
            if (style.hoverEvent != null) {
                context.drawHoverEvent(font, style, mouseX, mouseY)
            }
        }
    }

    @Unique
    private fun nichat_recalculateTotalContentHeight() {
        val font = nichat_mc.textRenderer
        val maxTextWidth = (width * nichat_config.logWidthScale).toInt()
        var calculatedHeight = 0.0
        if (NiChatClient.allMessages.isNotEmpty()) {
            NiChatClient.allMessages.forEach { msg ->
                val lines = font.wrapLines(msg.content, maxTextWidth)
                val textBlockHeight = lines.size * font.fontHeight
                val contentHeight = max(textBlockHeight, nichat_config.logHeadSize)
                calculatedHeight += contentHeight + nichat_config.logPadding * 2 + nichat_config.logPaddingBetweenMessages
            }
            if (calculatedHeight > 0) calculatedHeight -= nichat_config.logPaddingBetweenMessages
        }
        totalContentHeight = calculatedHeight
    }

    @Unique
    private fun nichat_getStyleAt(mouseX: Double, mouseY: Double): Style? {
        val font = nichat_mc.textRenderer
        val maxTextWidth = (this.width * nichat_config.logWidthScale).toInt()
        val logBottomY = this.chatField.y - nichat_config.logChatLogInputPadding
        var cursorY = (logBottomY + scrollOffset).toInt()
        for (messageToRender in NiChatClient.allMessages.reversed()) {
            val drawHead = messageToRender.senderProfile != NiChatClient.SYSTEM_PROFILE
            val lines = font.wrapLines(messageToRender.content, maxTextWidth)
            val widestLine = lines.maxOfOrNull { font.getWidth(it) } ?: 0
            val headSpace = if (drawHead) nichat_config.logHeadSize + nichat_config.logHeadTextSpacing else 0
            val textBlockHeight = lines.size * font.fontHeight
            val contentHeight = max(textBlockHeight, nichat_config.logHeadSize)
            val totalBlockWidth = headSpace + widestLine + nichat_config.logPadding * 2
            val totalBlockHeight = contentHeight + nichat_config.logPadding * 2
            val blockBottomY = cursorY
            val blockTopY = blockBottomY - totalBlockHeight
            if (mouseY >= blockTopY && mouseY < blockBottomY) {

                val blockLeftX = 2

                val textBlockLeftX = if (drawHead) {
                    blockLeftX + nichat_config.logPadding + headSpace
                } else {
                    blockLeftX + (totalBlockWidth - widestLine) / 2
                }
                val contentTopY = blockTopY + nichat_config.logPadding
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
            cursorY = blockTopY - nichat_config.logPaddingBetweenMessages
        }
        return null
    }
}