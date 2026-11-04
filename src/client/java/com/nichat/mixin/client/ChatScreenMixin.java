package com.nichat.mixin.client;

import com.nichat.Animation;
import com.nichat.ChatHistoryRenderer;
import com.nichat.ChatScreenAnimation;
import com.nichat.DisplayMessage;
import com.nichat.MessageBlock;
import com.nichat.MessageLayout;
import com.nichat.MessageLayoutCache;
import com.nichat.NiChatClient;
import com.nichat.config.NiChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
    @Unique private long nichat_openTimeNano = 0L;
    @Unique private float nichat_openStartVisibility = 0.0f;
    @Unique private float nichat_openVisibility = 1.0f;
    @Unique private float nichat_openProgress = 1.0f;
    @Unique private boolean nichat_messageSent = false;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.scrollOffset = 0.0;
        if (this.nichat_openTimeNano == 0L) {
            this.nichat_openTimeNano = System.nanoTime();
            this.nichat_openStartVisibility = ChatScreenAnimation.takeCloseVisibility();
            this.nichat_openVisibility = this.nichat_openStartVisibility;
            this.nichat_openProgress = Animation.smoothStep(this.nichat_openStartVisibility);
            ChatScreenAnimation.setOpenProgress(this.nichat_openProgress);
        }
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"))
    private void onHandleChatInput(String message, boolean addToRecentChat, CallbackInfo ci) {
        this.nichat_messageSent = true;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        if (this.input == null) return;
        ChatScreenAnimation.startClosing(
                this.nichat_openVisibility,
                this.input.getY() - this.nichat_config.getLogChatLogInputPadding(),
                this.scrollOffset,
                this.nichat_messageSent ? "" : this.input.getValue()
        );
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.nichat_openVisibility = Math.min(1.0f, this.nichat_openStartVisibility + Animation.linear(
                System.nanoTime() - this.nichat_openTimeNano,
                this.nichat_config.getLogOpenAnimationMs()
        ));
        this.nichat_openProgress = Animation.smoothStep(this.nichat_openVisibility);
        ChatScreenAnimation.setOpenProgress(this.nichat_openProgress);
        ChatScreenAnimation.setFadingInput(this.input);
        ChatScreenAnimation.setInputOpacity(this.nichat_openProgress);

        context.pose().pushMatrix();
        context.pose().translate(0.0f, ChatScreenAnimation.offsetFor(this.nichat_openProgress));

        nichat_renderCustomChatHistory(context, mouseX, mouseY);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRenderEnd(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatScreenAnimation.setFadingInput(null);
        context.pose().popMatrix();
    }

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
            ),
            index = 4
    )
    private int nichat_fadeInputBackground(int color) {
        return Animation.scaleColorAlpha(color, this.nichat_openProgress);
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
        if (this.input == null) return;

        this.nichat_hoveredStyle = null;
        ChatHistoryRenderer.render(
                context,
                this.nichat_mc,
                this.input.getY() - this.nichat_config.getLogChatLogInputPadding(),
                this.scrollOffset,
                this.nichat_openProgress,
                style -> this.nichat_hoveredStyle = style
        );
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
