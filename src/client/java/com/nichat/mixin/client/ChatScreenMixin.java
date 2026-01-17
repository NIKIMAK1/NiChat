package com.nichat.mixin.client;

import com.mojang.authlib.GameProfile;
import com.nichat.DisplayMessage;
import com.nichat.NiChatClient;
import com.nichat.config.NiChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
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
import java.util.UUID;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow @Final @Mutable protected EditBox input;
    @Shadow protected CommandSuggestions commandSuggestions;

    @Unique private double scrollOffset = 0.0;
    @Unique private double totalContentHeight = 0.0;
    @Unique private final NiChatConfig.ChatLogSettings nichat_config = NiChatClient.getConfig().getChatLog();
    @Unique private final Minecraft nichat_mc = Minecraft.getInstance();

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.scrollOffset = 0.0;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
        double mouseX = click.x();
        double mouseY = click.y();

        Style style = nichat_getStyleAt(mouseX, mouseY);
        if (style != null && this.nichat_handleStyleClick(style)) {
            cir.setReturnValue(true);
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
    private void nichat_renderCustomChatHistory(GuiGraphics context, int mouseX, int mouseY) {
        nichat_recalculateTotalContentHeight();

        Font font = this.nichat_mc.font;
        int screenWidth = context.guiWidth();
        int maxTextWidth = (int)(screenWidth * this.nichat_config.getLogWidthScale());
        if (this.input == null) return;

        int logBottomY = this.input.getY() - this.nichat_config.getLogChatLogInputPadding();
        int logTopY = 0;

        context.enableScissor(0, logTopY, screenWidth, logBottomY);

        int cursorY = (int)(logBottomY + this.scrollOffset);

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            DisplayMessage messageToRender = messages.get(i);
            GameProfile senderProfile = messageToRender.getSenderProfile();
            boolean drawHead = !senderProfile.equals(NiChatClient.getSYSTEM_PROFILE());
            List<FormattedCharSequence> lines = font.split(messageToRender.getContent(), maxTextWidth);

            int widestLine = lines.stream().mapToInt(font::width).max().orElse(0);
            int headSpace = drawHead ? (this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing()) : 0;
            int textBlockHeight = lines.size() * font.lineHeight;
            int contentHeight = Math.max(textBlockHeight, (drawHead ? this.nichat_config.getLogHeadSize() : 0));
            int totalBlockHeight = contentHeight + this.nichat_config.getLogPadding() * 2;
            int totalBlockWidth = headSpace + widestLine + this.nichat_config.getLogPadding() * 2;

            int blockBottomY = cursorY;
            int blockTopY = blockBottomY - totalBlockHeight;

            if (blockTopY > logBottomY || blockBottomY < logTopY) {
                cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
                continue;
            }

            int blockLeftX = 2;
            int backgroundColor = this.nichat_mc.options.getBackgroundColor(this.nichat_config.getLogBackgroundOpacity());
            context.fill(blockLeftX, blockTopY, blockLeftX + totalBlockWidth, blockBottomY, backgroundColor);

            int contentTopY = blockTopY + this.nichat_config.getLogPadding();
            int textLeftX;

            if (drawHead) {
                UUID senderId = senderProfile.id();
                int headX = blockLeftX + this.nichat_config.getLogPadding();
                int headY = contentTopY + (contentHeight - this.nichat_config.getLogHeadSize()) / 2;
                PlayerInfo playerListEntry = this.nichat_mc.getConnection() != null
                        ? this.nichat_mc.getConnection().getPlayerInfo(senderId)
                        : null;
                var skinTextures = playerListEntry != null
                        ? playerListEntry.getSkin()
                        : DefaultPlayerSkin.get(senderId);

                PlayerFaceRenderer.draw(context, skinTextures, headX, headY, this.nichat_config.getLogHeadSize());
                textLeftX = headX + this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing();
            } else {
                textLeftX = blockLeftX + (totalBlockWidth - widestLine) / 2;
            }

            int textOffsetY = (contentHeight - textBlockHeight) / 2;
            int lineY = contentTopY + textOffsetY;
            for (FormattedCharSequence line : lines) {
                context.drawString(font, line, textLeftX, lineY, 0xFFFFFFFF);
                lineY += font.lineHeight;
            }

            cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
        }

        context.disableScissor();

        Style style = nichat_getStyleAt(mouseX, mouseY);
        if (style != null && style.getHoverEvent() != null) {
            context.renderComponentHoverEffect(font, style, mouseX, mouseY);
        }
    }

    @Unique
    private void nichat_recalculateTotalContentHeight() {
        if (this.width == 0) return;
        Font font = this.nichat_mc.font;
        int maxTextWidth = (int)(this.width * this.nichat_config.getLogWidthScale());
        double calculatedHeight = 0.0;

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        if (!messages.isEmpty()) {
            for (DisplayMessage msg : messages) {
                List<FormattedCharSequence> lines = font.split(msg.getContent(), maxTextWidth);
                int textBlockHeight = lines.size() * font.lineHeight;
                boolean drawHead = !msg.getSenderProfile().equals(NiChatClient.getSYSTEM_PROFILE());
                int contentHeight = Math.max(textBlockHeight, (drawHead ? this.nichat_config.getLogHeadSize() : 0));
                calculatedHeight += contentHeight + this.nichat_config.getLogPadding() * 2 + this.nichat_config.getLogPaddingBetweenMessages();
            }
            calculatedHeight -= this.nichat_config.getLogPaddingBetweenMessages();
        }
        this.totalContentHeight = calculatedHeight;
    }

    @Unique
    private @Nullable Style nichat_getStyleAt(double mouseX, double mouseY) {
        if (this.input == null) return null;
        if (mouseY < 20 || mouseY > this.input.getY() - this.nichat_config.getLogChatLogInputPadding()) return null;

        Font font = this.nichat_mc.font;
        int maxTextWidth = (int)(this.width * this.nichat_config.getLogWidthScale());
        int logBottomY = this.input.getY() - this.nichat_config.getLogChatLogInputPadding();
        int cursorY = (int)(logBottomY + this.scrollOffset);

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            DisplayMessage messageToRender = messages.get(i);
            boolean drawHead = !messageToRender.getSenderProfile().equals(NiChatClient.getSYSTEM_PROFILE());
            List<FormattedCharSequence> lines = font.split(messageToRender.getContent(), maxTextWidth);

            int widestLine = lines.stream().mapToInt(font::width).max().orElse(0);
            int headSpace = drawHead ? (this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing()) : 0;
            int textBlockHeight = lines.size() * font.lineHeight;
            int contentHeight = Math.max(textBlockHeight, (drawHead ? this.nichat_config.getLogHeadSize() : 0));
            int totalBlockHeight = contentHeight + this.nichat_config.getLogPadding() * 2;
            int totalBlockWidth = headSpace + widestLine + this.nichat_config.getLogPadding() * 2;

            int blockBottomY = cursorY;
            int blockTopY = blockBottomY - totalBlockHeight;

            if (mouseY >= blockTopY && mouseY < blockBottomY) {
                int blockLeftX = 2;
                int textBlockLeftX;

                if (drawHead) {
                    textBlockLeftX = blockLeftX + this.nichat_config.getLogPadding() + headSpace;
                } else {
                    textBlockLeftX = blockLeftX + (totalBlockWidth - widestLine) / 2;
                }

                if (mouseX >= textBlockLeftX && mouseX < textBlockLeftX + widestLine) {
                    int contentTopY = blockTopY + this.nichat_config.getLogPadding();
                    int textOffsetY = (contentHeight - textBlockHeight) / 2;
                    int lineIndex = (int) ((mouseY - (contentTopY + textOffsetY)) / font.lineHeight);

                    if (lineIndex >= 0 && lineIndex < lines.size()) {
                        FormattedCharSequence line = lines.get(lineIndex);
                        int relativeMouseX = (int) (mouseX - textBlockLeftX);

                        final Style[] foundStyle = {null};
                        final int[] currentX = {0};

                        line.accept((index, s, codePoint) -> {
                            if (foundStyle[0] != null) return false;

                            int charWidth = font.width(FormattedCharSequence.forward(String.valueOf((char)codePoint), Style.EMPTY));
                            if (currentX[0] <= relativeMouseX && relativeMouseX < currentX[0] + charWidth) {
                                foundStyle[0] = s;
                                return false;
                            }
                            currentX[0] += charWidth;
                            return true;
                        });

                        return foundStyle[0];
                    }
                }
            }
            cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
        }
        return null;
    }
}