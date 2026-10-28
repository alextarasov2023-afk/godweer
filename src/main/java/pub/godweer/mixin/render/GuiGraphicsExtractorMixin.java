package pub.godweer.mixin.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {

    @Inject(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V", at = @At("HEAD"), cancellable = true)
    private void onInnerBlit(RenderPipeline pipeline, Identifier location, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        if (location == null || Removals.INSTANCE == null || !Removals.INSTANCE.isEnabled()) {
            return;
        }

        String path = location.getPath();

        if (path.contains("vignette")) {
            if (Removals.INSTANCE.isOverlayDisabled("Vignette") || Removals.INSTANCE.isBadEffectsDisabled()) {
                ci.cancel();
            }
        }

        if (path.contains("portal")) {
            if (Removals.INSTANCE.isOverlayDisabled("Portal") || Removals.INSTANCE.isBadEffectsDisabled()) {
                ci.cancel();
            }
        }

        if (path.contains("fire") || path.contains("flame")) {
            if (Removals.INSTANCE.isOverlayDisabled("Fire")) {
                ci.cancel();
            }
        }

        if (path.contains("pumpkinblur")) {
            if (Removals.INSTANCE.isOverlayDisabled("Block")) {
                ci.cancel();
            }
        }
    }
}