package com.nichat.mixin.client;

import com.mojang.authlib.GameProfile;
import com.nichat.DisplayMessage;
import com.nichat.MessageBlock;
import com.nichat.MessageLayout;
import com.nichat.MessageLayoutCache;
import com.nichat.NiChatClient;
import com.nichat.config.NiChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow @Final @Mutable protected EditBox input;
    @Shadow protected CommandSuggestions commandSuggestions;

    @Unique private double scrollOffset = 0.0;
    @Unique private double totalContentHeight = 0.0;
    @Unique private @Nullable Style nichat_hoveredStyle = null;
    @Unique private final NiChatConfig.ChatLogSettings nichat_config = NiChatClient.getConfig().getChatLog();
    @Unique private final Minecraft nichat_mc = Minecraft.getInstance();

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.scrollOffset = 0.0;
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        nichat_renderCustomChatHistory(context, mouseX, mouseY);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.commandSuggestions != null && this.commandSuggestions.mouseScrolled(verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }

        nichat_recalculateTotalContentHeight();
        int logBottomY = this.input.getY() - this.nichat_config.getLogChatLogInputPadding();
        double maxScroll = Math.max(0.0, this.totalContentHeight - logBottomY);
        double scrollAmount = verticalAmount * this.nichat_mc.font.lineHeight * this.nichat_config.getLogScrollSpeed();
        this.scrollOffset = Math.max(0.0, Math.min(this.scrollOffset - scrollAmount, maxScroll));
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean button, CallbackInfoReturnable<Boolean> cir) {
        if (this.nichat_hoveredStyle != null && this.nichat_handleStyleClick(this.nichat_hoveredStyle)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Unique
    private boolean nichat_handleStyleClick(Style style) {
        if (this.minecraft == null) return false;
        if (style.getClickEvent() == null) return false;

        Screen.defaultHandleGameClickEvent(style.getClickEvent(), this.minecraft, this);
        return true;
    }

    @Unique
    private void nichat_renderCustomChatHistory(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        nichat_recalculateTotalContentHeight();

        Font font = this.nichat_mc.font;
        int screenWidth = context.guiWidth();
        int maxTextWidth = (int)(screenWidth * this.nichat_config.getLogWidthScale());
        if (this.input == null) return;

        int logBottomY = this.input.getY() - this.nichat_config.getLogChatLogInputPadding();
        int logTopY = 0;

        this.nichat_hoveredStyle = null;
        context.enableScissor(0, logTopY, screenWidth, logBottomY);

        int cursorY = (int)(logBottomY + this.scrollOffset);
        var textCollector = context.textRenderer(
                GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR,
                style -> this.nichat_hoveredStyle = style
        );

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        List<MessageBlock> blocks = MessageLayoutCache.getBlocks(
                font,
                messages,
                maxTextWidth,
                this.nichat_config.getLogHeadSize(),
                this.nichat_config.getLogHeadTextSpacing(),
                this.nichat_config.getLogPadding()
        );
        for (int i = blocks.size() - 1; i >= 0; i--) {
            MessageBlock block = blocks.get(i);
            GameProfile senderProfile = block.getMessage().getSenderProfile();

            int blockBottomY = cursorY;
            int blockTopY = blockBottomY - block.getBoxHeight();

            if (blockTopY > logBottomY || blockBottomY < logTopY) {
                cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
                continue;
            }

            int blockLeftX = 2;
            int backgroundColor = this.nichat_mc.options.getBackgroundColor(this.nichat_config.getLogBackgroundOpacity());
            context.fill(blockLeftX, blockTopY, blockLeftX + block.getBoxWidth(), blockBottomY, backgroundColor);

            int contentTopY = blockTopY + this.nichat_config.getLogPadding();
            int textLeftX;

            if (block.getDrawHead()) {
                int headX = blockLeftX + this.nichat_config.getLogPadding();
                int headY = contentTopY + (block.getContentHeight() - this.nichat_config.getLogHeadSize()) / 2;
                NiChatClient.drawPlayerHead(context, this.nichat_mc, senderProfile, headX, headY, this.nichat_config.getLogHeadSize(), 1.0f);
                textLeftX = headX + this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing();
            } else {
                textLeftX = blockLeftX + (block.getBoxWidth() - block.getTextWidth()) / 2;
            }

            int textOffsetY = (block.getContentHeight() - block.getLines().size() * font.lineHeight) / 2;
            int lineY = contentTopY + textOffsetY;
            for (FormattedCharSequence line : block.getLines()) {
                textCollector.accept(TextAlignment.LEFT, textLeftX, lineY, line);
                lineY += font.lineHeight;
            }

            cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
        }

        context.disableScissor();
    }

    @Unique
    private void nichat_recalculateTotalContentHeight() {
        if (this.width == 0) return;
        Font font = this.nichat_mc.font;
        int maxTextWidth = (int)(this.width * this.nichat_config.getLogWidthScale());
        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        List<MessageBlock> blocks = MessageLayoutCache.getBlocks(
                font,
                messages,
                maxTextWidth,
                this.nichat_config.getLogHeadSize(),
                this.nichat_config.getLogHeadTextSpacing(),
                this.nichat_config.getLogPadding()
        );
        this.totalContentHeight = MessageLayout.calculateTotalHeight(
                blocks,
                this.nichat_config.getLogPaddingBetweenMessages()
        );
    }
}
