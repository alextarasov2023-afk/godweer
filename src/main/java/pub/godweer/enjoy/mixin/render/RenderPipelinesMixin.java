package pub.godweer.enjoy.mixin.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.auxiliary.render.pipline.Pipelines;
import pub.godweer.auxiliary.render.world.particles.ParticleSpawn;
import pub.godweer.auxiliary.render.pipline.PiplinePost;

/**
 * Create by daun kvass
 */
@Mixin(RenderPipelines.class)
public abstract class RenderPipelinesMixin {
    @Shadow
    private static RenderPipeline register(RenderPipeline renderPipeline) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void registerPipline(CallbackInfo ci) {
        register(Pipelines.RECT);
        register(Pipelines.TEXT);
        register(Pipelines.TEXTURE);
        register(Pipelines.BLUR_RECT);
        register(Pipelines.GUI_BLUR_DOWN);
        register(Pipelines.CIRCLE_ARC);
        register(Pipelines.GUI_BLUR_UP);
        register(ParticleSpawn.pipeline());
        register(PiplinePost.HANDS_MASK);
        register(PiplinePost.HAND_LAVA);
        register(PiplinePost.HAND_FROST);
        register(PiplinePost.HAND_GALAXY);
        register(PiplinePost.HAND_EMBER);
        register(PiplinePost.HAND_SLIME);
        register(PiplinePost.HAND_SMOKE);
        register(PiplinePost.WORLD_SKY_AURORA);
        register(PiplinePost.WORLD_SKY_CRIMSON);
        register(PiplinePost.WORLD_SKY_OCEAN);
        register(PiplinePost.WORLD_SKY_SUNSET);
        register(PiplinePost.WORLD_SKY_STORM);
        register(PiplinePost.WORLD_SKY_EMERALD);
        register(PiplinePost.WORLD_SKY_SOLAR);
        register(PiplinePost.WORLD_SKY_VOID);
        register(PiplinePost.HANDS_GLOW_MASK);
        register(PiplinePost.HANDS_GLOW_BLUR);
        register(PiplinePost.HANDS_GLOW_COMPOSITE);
        PiplinePost.CHAMS_ENTITY.values().forEach(passes -> {
            register(passes.normal());
            register(passes.through());
        });
        register(PiplinePost.BLOCK_HIGHLIGHT_CAUSTICS);
        register(PiplinePost.BLOCK_HIGHLIGHT_CAUSTICS_THROUGH);
        register(PiplinePost.BLOCK_HIGHLIGHT_GLOSSY);
        register(PiplinePost.BLOCK_HIGHLIGHT_GLOSSY_THROUGH);
        register(PiplinePost.BLOCK_HIGHLIGHT_DEEP_SPACE);
        register(PiplinePost.BLOCK_HIGHLIGHT_DEEP_SPACE_THROUGH);
        register(PiplinePost.WORLD_SKY_STARRY_SKY_SPACE);
        register(PiplinePost.WORLD_SKY_CLOUDS_NEBULA);
        register(PiplinePost.WORLD_SKY_CLOUDS_PLASMA);
        register(PiplinePost.WORLD_SKY_STARRY_SKY);
        register(PiplinePost.WORLD_SKY_CLOUDS_CAUSTIC);
        register(PiplinePost.WORLD_SKY_CAUSTIC);
        register(PiplinePost.WORLD_VOLUMETRIC_FOG);
        register(PiplinePost.WORLD_LIGHTNING);
        register(PiplinePost.WORLD_SKY_NEBULA);
        register(PiplinePost.WORLD_SKY_PLASMA);
        register(PiplinePost.WORLD_SATURATION);
        register(PiplinePost.WORLD_PUDDLES);
    }
}
