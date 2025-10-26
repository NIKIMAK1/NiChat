package com.nichat.mixin.client;

import com.mojang.authlib.GameProfile;
import com.nichat.DisplayMessage;
import com.nichat.NiChatClient;
import com.nichat.config.NiChatConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow @Final @Mutable protected TextFieldWidget chatField;
    @Shadow protected ChatInputSuggestor chatInputSuggestor;

    @Unique private double scrollOffset = 0.0;
    @Unique private double totalContentHeight = 0.0;
    @Unique private final NiChatConfig.ChatLogSettings nichat_config = NiChatClient.getConfig().getChatLog();
    @Unique private final MinecraftClient nichat_mc = MinecraftClient.getInstance();

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.scrollOffset = 0.0;
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V"
            )
    )
    private void redirectChatHudRender(
            ChatHud instance,
            DrawContext context,
            int tickCounter,
            int mouseX,
            int mouseY,
            boolean focused
    ) {
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
        if (this.chatInputSuggestor != null && this.chatInputSuggestor.mouseScrolled(verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }

        nichat_recalculateTotalContentHeight();
        int logBottomY = this.chatField.getY() - this.nichat_config.getLogChatLogInputPadding();
        double maxScroll = Math.max(0.0, this.totalContentHeight - logBottomY);
        double scrollAmount = verticalAmount * this.nichat_mc.textRenderer.fontHeight * this.nichat_config.getLogScrollSpeed();
        this.scrollOffset = Math.max(0.0, Math.min(this.scrollOffset - scrollAmount, maxScroll));
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean button, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();

        Style style = nichat_getStyleAt(mouseX, mouseY);
        if (style != null && this.handleTextClick(style)) {
            cir.setReturnValue(true);
        }
    }


    @Unique
    private void nichat_renderCustomChatHistory(DrawContext context, int mouseX, int mouseY) {
        nichat_recalculateTotalContentHeight();

        TextRenderer font = this.nichat_mc.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int maxTextWidth = (int)(screenWidth * this.nichat_config.getLogWidthScale());
        int logBottomY = this.chatField.getY() - this.nichat_config.getLogChatLogInputPadding();
        int logTopY = 0;

        context.enableScissor(0, logTopY, screenWidth, logBottomY);

        int cursorY = (int)(logBottomY + this.scrollOffset);

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            DisplayMessage messageToRender = messages.get(i);
            GameProfile senderProfile = messageToRender.getSenderProfile();
            boolean drawHead = !senderProfile.equals(NiChatClient.getSYSTEM_PROFILE());
            List<OrderedText> lines = font.wrapLines(messageToRender.getContent(), maxTextWidth);

            int widestLine = lines.stream().mapToInt(font::getWidth).max().orElse(0);
            int headSpace = drawHead ? (this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing()) : 0;
            int textBlockHeight = lines.size() * font.fontHeight;
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
            int backgroundColor = this.nichat_mc.options.getTextBackgroundColor(this.nichat_config.getLogBackgroundOpacity());
            context.fill(blockLeftX, blockTopY, blockLeftX + totalBlockWidth, blockBottomY, backgroundColor);

            int contentTopY = blockTopY + this.nichat_config.getLogPadding();
            int textLeftX;

            if (drawHead) {
                UUID senderId = senderProfile.id();
                int headX = blockLeftX + this.nichat_config.getLogPadding();
                int headY = contentTopY + (contentHeight - this.nichat_config.getLogHeadSize()) / 2;
                PlayerListEntry playerListEntry = this.nichat_mc.getNetworkHandler() != null
                        ? this.nichat_mc.getNetworkHandler().getPlayerListEntry(senderId)
                        : null;
                var skinTextures = playerListEntry != null
                        ? playerListEntry.getSkinTextures()
                        : DefaultSkinHelper.getSkinTextures(senderId);

                PlayerSkinDrawer.draw(context, skinTextures, headX, headY, this.nichat_config.getLogHeadSize());
                textLeftX = headX + this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing();
            } else {
                textLeftX = blockLeftX + (totalBlockWidth - widestLine) / 2;
            }

            int textOffsetY = (contentHeight - textBlockHeight) / 2;
            int lineY = contentTopY + textOffsetY;
            for (OrderedText line : lines) {
                context.drawTextWithShadow(font, line, textLeftX, lineY, 0xFFFFFFFF);
                lineY += font.fontHeight;
            }

            cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
        }

        context.disableScissor();

        Style style = nichat_getStyleAt(mouseX, mouseY);
        if (style != null && style.getHoverEvent() != null) {
            context.drawHoverEvent(font, style, mouseX, mouseY);
        }
    }

    @Unique
    private void nichat_recalculateTotalContentHeight() {
        TextRenderer font = this.nichat_mc.textRenderer;
        int maxTextWidth = (int)(this.width * this.nichat_config.getLogWidthScale());
        double calculatedHeight = 0.0;

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        if (!messages.isEmpty()) {
            for (DisplayMessage msg : messages) {
                List<OrderedText> lines = font.wrapLines(msg.getContent(), maxTextWidth);
                int textBlockHeight = lines.size() * font.fontHeight;
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
        if (mouseY < 20 || mouseY > this.chatField.getY() - this.nichat_config.getLogChatLogInputPadding()) return null;

        TextRenderer font = this.nichat_mc.textRenderer;
        int maxTextWidth = (int)(this.width * this.nichat_config.getLogWidthScale());
        int logBottomY = this.chatField.getY() - this.nichat_config.getLogChatLogInputPadding();
        int cursorY = (int)(logBottomY + this.scrollOffset);

        List<DisplayMessage> messages = NiChatClient.getAllMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            DisplayMessage messageToRender = messages.get(i);
            boolean drawHead = !messageToRender.getSenderProfile().equals(NiChatClient.getSYSTEM_PROFILE());
            List<OrderedText> lines = font.wrapLines(messageToRender.getContent(), maxTextWidth);

            int widestLine = lines.stream().mapToInt(font::getWidth).max().orElse(0);
            int headSpace = drawHead ? (this.nichat_config.getLogHeadSize() + this.nichat_config.getLogHeadTextSpacing()) : 0;
            int textBlockHeight = lines.size() * font.fontHeight;
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
                    int lineIndex = (int) ((mouseY - (contentTopY + textOffsetY)) / font.fontHeight);

                    if (lineIndex >= 0 && lineIndex < lines.size()) {
                        OrderedText line = lines.get(lineIndex);
                        int relativeMouseX = (int) (mouseX - textBlockLeftX);
                        return font.getTextHandler().getStyleAt(line, relativeMouseX);
                    }
                }
            }
            cursorY = blockTopY - this.nichat_config.getLogPaddingBetweenMessages();
        }
        return null;
    }
}