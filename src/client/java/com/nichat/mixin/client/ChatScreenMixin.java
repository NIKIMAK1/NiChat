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
import com.nichat.translation.OutgoingTranslation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
import org.spongepowered.asm.mixin.injection.Redirect;
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
    @Unique private boolean nichat_draggingScrollbar = false;
    @Unique private double nichat_scrollbarGrabOffset = 0.0;
    @Unique private static final int NICHAT_SCROLLBAR_HIT_WIDTH = 10;
    @Unique private static final int NICHAT_SCROLLBAR_MIN_THUMB = 10;

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

    @Redirect(
            method = "handleChatInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"
            )
    )
    private void nichat_sendChat(ClientPacketListener connection, String message) {
        OutgoingTranslation.sendChat(connection, message);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        if (this.input == null) return;
        ChatScreenAnimation.startClosing(
                this.nichat_openVisibility,
                MessageLayout.logBottomY(this.input.getY(), this.height),
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
        nichat_renderScrollbar(context, mouseX, mouseY);
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
        double maxScroll = nichat_maxScroll();
        double scrollAmount = verticalAmount * this.nichat_mc.font.lineHeight * this.nichat_config.getLogScrollSpeed();
        this.scrollOffset = Math.max(0.0, Math.min(this.scrollOffset - scrollAmount, maxScroll));
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean button, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && nichat_isOverScrollbar(click.x(), click.y())) {
            double[] bar = nichat_scrollbarGeometry();
            if (bar != null) {
                double thumbTop = bar[2];
                double thumbHeight = bar[3] - bar[2];
                boolean onThumb = click.y() >= thumbTop && click.y() <= bar[3];
                this.nichat_scrollbarGrabOffset = onThumb ? click.y() - thumbTop : thumbHeight / 2.0;
                this.nichat_draggingScrollbar = true;
                nichat_dragScrollbarTo(click.y());
                cir.setReturnValue(true);
                return;
            }
        }

        if (this.nichat_hoveredStyle != null && this.nichat_handleStyleClick(this.nichat_hoveredStyle)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.nichat_draggingScrollbar) {
            nichat_dragScrollbarTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.nichat_draggingScrollbar && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.nichat_draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Unique
    private int nichat_logBottomY() {
        return MessageLayout.logBottomY(this.input.getY(), this.height);
    }

    @Unique
    private int nichat_logTopY() {
        return MessageLayout.logTopY(nichat_logBottomY());
    }

    @Unique
    private double nichat_visibleHeight() {
        return nichat_logBottomY() - nichat_logTopY();
    }

    @Unique
    private double nichat_maxScroll() {
        return Math.max(0.0, this.totalContentHeight - nichat_visibleHeight());
    }

    @Unique
    private double @Nullable [] nichat_scrollbarGeometry() {
        if (this.input == null || !this.nichat_config.getScrollbarEnabled()) return null;
        nichat_recalculateTotalContentHeight();
        double maxScroll = nichat_maxScroll();
        if (maxScroll <= 0.0) return null;

        int margin = MessageLayout.scrollbarMargin();
        double trackTop = nichat_logTopY() + margin;
        double trackBottom = nichat_logBottomY() - margin;
        double trackHeight = trackBottom - trackTop;
        if (trackHeight <= NICHAT_SCROLLBAR_MIN_THUMB) return null;

        double visibleFraction = nichat_visibleHeight() / this.totalContentHeight;
        double thumbHeight = Math.max(NICHAT_SCROLLBAR_MIN_THUMB, trackHeight * visibleFraction);
        double scrollFraction = Math.min(1.0, this.scrollOffset / maxScroll);
        double thumbBottom = trackBottom - scrollFraction * (trackHeight - thumbHeight);
        return new double[]{trackTop, trackBottom, thumbBottom - thumbHeight, thumbBottom};
    }

    @Unique
    private boolean nichat_isOverScrollbar(double mouseX, double mouseY) {
        int left = MessageLayout.scrollbarLeftX(this.width);
        int right = MessageLayout.scrollbarRightX(this.width);
        int extra = Math.max(0, (NICHAT_SCROLLBAR_HIT_WIDTH - (right - left)) / 2);
        return mouseX >= left - extra
                && mouseX <= right + extra
                && mouseY >= nichat_logTopY()
                && mouseY <= nichat_logBottomY();
    }

    @Unique
    private void nichat_dragScrollbarTo(double mouseY) {
        double[] bar = nichat_scrollbarGeometry();
        if (bar == null) return;
        double trackTop = bar[0];
        double trackBottom = bar[1];
        double thumbHeight = bar[3] - bar[2];
        double travel = trackBottom - trackTop - thumbHeight;
        if (travel <= 0.0) return;

        double thumbTop = mouseY - this.nichat_scrollbarGrabOffset;
        double fraction = (trackBottom - thumbHeight - thumbTop) / travel;
        this.scrollOffset = Math.max(0.0, Math.min(1.0, fraction)) * nichat_maxScroll();
    }

    @Unique
    private void nichat_renderScrollbar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        double[] bar = nichat_scrollbarGeometry();
        if (bar == null) return;

        boolean active = this.nichat_draggingScrollbar || nichat_isOverScrollbar(mouseX, mouseY);
        int left = MessageLayout.scrollbarLeftX(this.width);
        int right = MessageLayout.scrollbarRightX(this.width);
        float opacity = this.nichat_openProgress;

        context.fill(left, (int) bar[0], right, (int) bar[1], Animation.scaleColorAlpha(0x33FFFFFF, opacity));
        int thumbColor = active ? 0xE6FFFFFF : 0x99FFFFFF;
        context.fill(left, (int) Math.round(bar[2]), right, (int) Math.round(bar[3]), Animation.scaleColorAlpha(thumbColor, opacity));
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
                MessageLayout.logBottomY(this.input.getY(), this.height),
                this.scrollOffset,
                this.nichat_openProgress,
                style -> this.nichat_hoveredStyle = style
        );
    }

    @Unique
    private void nichat_recalculateTotalContentHeight() {
        if (this.width == 0) return;
        Font font = this.nichat_mc.font;
        int maxTextWidth = MessageLayout.logTextWidth(this.width);
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
