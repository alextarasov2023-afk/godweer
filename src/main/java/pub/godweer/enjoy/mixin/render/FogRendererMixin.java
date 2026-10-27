package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.kitty.module.impl.render.Ambience;

/**
 * Create by daun kvass
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void onSetupFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (Ambience.INSTANCE != null && Ambience.INSTANCE.isCustomFogEnabled()) {
            FogData fog = cir.getReturnValue();

            float start = Ambience.INSTANCE.getFogStart();
            float end = Math.max(start + 1.0F, Ambience.INSTANCE.getFogEnd());

            fog.environmentalStart = start;
            fog.environmentalEnd = end;
            fog.renderDistanceStart = start;
            fog.renderDistanceEnd = end;

            int color = Ambience.INSTANCE.getFogColorRGB();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            float a = ((color >> 24) & 0xFF) / 255.0F;
            if (a == 0.0F) a = 1.0F;

            fog.color.set(r, g, b, a);
        }
    }
}