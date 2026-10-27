package pub.godweer.enjoy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.kitty.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onSubmitFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isEnabled() && Removals.INSTANCE.getOverlays().isEnabled("Fire")) {
            ci.cancel();
        }
    }

    // В 26.3 submitWater принимает PlayerRenderState.WaterOverlay вместо Minecraft.
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void onSubmitWater(PlayerRenderState.WaterOverlay overlay, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isEnabled() && Removals.INSTANCE.getOverlays().isEnabled("Underwater")) {
            ci.cancel();
        }
    }

    // В 26.3 submitBlockSprite стал (Identifier, 4 float, PoseStack, SubmitNodeCollector, int).
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void onSubmitBlockSprite(Identifier location, float u0, float u1, float v0, float v1, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int color, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isEnabled() && Removals.INSTANCE.getOverlays().isEnabled("Block")) {
            ci.cancel();
        }
    }

    // В 26.3 первым аргументом добавился PlayerRenderState.
    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onRenderItemActivationAnimation(PlayerRenderState playerState, PoseStack poseStack, float partialTicks, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isEnabled() && Removals.INSTANCE.getOverlays().isEnabled("Totem")) {
            ci.cancel();
        }
    }
}
