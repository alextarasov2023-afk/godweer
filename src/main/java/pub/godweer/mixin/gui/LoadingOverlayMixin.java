package pub.godweer.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.util.RenderExtend;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Create by daun kvass
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Unique
    private static final Identifier PHOTO = Identifier.fromNamespaceAndPath("godweer", "images/ui/cutie.png");

    @Unique
    private long lastTime = -1L;

    @Unique
    private float animTime = 0;
    @Unique
    private static float smoothStep(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

        if (Thread.currentThread().getPriority() != Thread.MAX_PRIORITY) {
            try {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            } catch (Throwable ignored) {}
        }

        int width = this.minecraft.getWindow().getGuiScaledWidth();
        int height = this.minecraft.getWindow().getGuiScaledHeight();

        long currentTime = Util.getMillis();

        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = currentTime;
        }

        if (RenderExtend.sttime == -1L) {
            RenderExtend.sttime = currentTime;
        }

        long elapsed = currentTime - RenderExtend.sttime;
        boolean isStartup = (this.minecraft.gui.screen() == null);
        long animStartDelay = isStartup ? 1000L : 0L;

        if (this.lastTime == -1L) {
            this.lastTime = currentTime;
        }
        float deltaTime = (currentTime - this.lastTime) / 1000f;
        this.lastTime = currentTime;

        float progressDelta = Math.min(deltaTime, 0.03f);

        if (elapsed >= animStartDelay) {
            this.animTime += progressDelta;
        }

        float targetProgress;
        if (elapsed < animStartDelay) {
            targetProgress = 0;
        } else {
            if (!this.reload.isDone()) {
                float progressTime = Math.max(0.0F, (elapsed - animStartDelay) / 1000f);
                float simulated = Mth.clamp(progressTime * 0.51f, 0, 0.92f);
                targetProgress = Math.max(simulated, this.reload.getActualProgress() * 0.92f);
            } else {
                targetProgress = 1;
            }
        }

        float catchUpSpeed = !this.reload.isDone() ? 0.2f : 0.5f;
        if (this.currentProgress < targetProgress) {
            this.currentProgress = Math.min(this.currentProgress + catchUpSpeed * progressDelta, targetProgress);
        } else {
            this.currentProgress = Mth.clamp(this.currentProgress * 0.98F + targetProgress * 0.02F, 0, 1);
        }
        float fadeOutProgress = this.fadeOutStart > -1L ? (float)(currentTime - this.fadeOutStart) / 1200 : 0;

        if (fadeOutProgress >= 1) {
            this.minecraft.gui.setOverlay(null);
            RenderExtend.sttime = -1L;
            return;
        }

        if (this.reload.isDone() && this.minecraft.gui.screen() != null) {
            this.minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(guiGraphicsExtractor, mouseX, mouseY, partialTick);
        }

        float alpha = 1;
        if (this.fadeOutStart > -1L) {
            alpha = 1 - smoothStep(fadeOutProgress);
        } else if (this.fadeIn && this.fadeInStart > -1L) {
            float fadeInProgress = (float)(currentTime - this.fadeInStart) / 1000;
            alpha = smoothStep(fadeInProgress);
        }

        int alphaInt = Math.round(255 * alpha);
        if (alphaInt <= 0) return;

        int bgCol = ColorUtil.rgba(0, 0, 0, alphaInt);

        RenderExtend.enter2D(null, guiGraphicsExtractor, null);
        try {
            Render2DUtil.beginFrame();
            Render2D.drawRect(0, 0, width, height, bgCol);
            float logoSize = 140;
            float baseLogoY = (height - logoSize) / 2 - 25;
            float logoX = (width - logoSize) / 2;
            int logoColor = ColorUtil.rgba(255, 255, 255, alphaInt);
            Render2D.drawTexture(PHOTO, logoX, baseLogoY, logoSize, logoSize, logoColor);
            Render2DUtil.flush();
        } finally {
            RenderExtend.exit2D();
        }
    }
}