package pub.godweer.mixin.render;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.module.impl.render.BlockHighlight;
import pub.godweer.module.impl.render.PopEffect;
import pub.godweer.util.render.world.RendererWorldProvider;
import pub.godweer.util.render.world.MiasmWorlds;

/**
 * Create by daun kvass
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    @Unique
    private CameraRenderState popEffect$cameraRenderState;
    @Inject(method = "render", at = @At("TAIL"))
    private void renderWorldEffects(GraphicsResourceAllocator graphicsResourceAllocator, boolean b1, CameraRenderState cameraRenderState, GpuBufferSlice fogParameters, Vector4f skyColor, boolean b2, boolean b3, CallbackInfo ci) {
        // В 26.3 у LevelRenderer.render больше нет DeltaTracker/проекционной матрицы;
        // партиал-тик берётся из Minecraft.getDeltaTracker().
        MiasmWorlds.render(new RendererWorldProvider(this.levelRenderState, cameraRenderState,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void renderPopChams(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        PopEffect popEffect = Client.INSTANCE.moduleManager.getModule(PopEffect.class);
        if (popEffect != null && popEffect.isEnabled() && this.popEffect$cameraRenderState != null) {
            popEffect.getRenderer().submitChams(poseStack, submitNodeCollector, this.popEffect$cameraRenderState);
        }
    }
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void replaceVanillaBlockOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelRenderState, CallbackInfo ci) {
        BlockHighlight blockOutline = Client.INSTANCE.moduleManager.getModule(BlockHighlight.class);
        if (blockOutline != null && blockOutline.enables()) {
            ci.cancel();
        }
    }

}