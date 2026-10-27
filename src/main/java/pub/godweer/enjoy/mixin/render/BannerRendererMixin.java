package pub.godweer.enjoy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.kitty.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(BannerRenderer.class)
public class BannerRendererMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void onSubmitBanner(BannerRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isBannersDisabled()) {
            ci.cancel();
        }
    }
}