package pub.godweer.enjoy.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.auxiliary.render.renders.MenuBacks;
import pub.godweer.auxiliary.render.pipline.Pipelines;

import java.util.List;
import java.util.function.Supplier;

/**
 * Create by daun kvass
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow
    @Final
    private List<?> draws;

    @Unique
    private final IntList godweer$glassSplits = new IntArrayList();
    @Unique
    private boolean godweer$previousElementWasGlass;

    @Inject(method = "prepare", at = @At("HEAD"))
    private void godweer$resetGlassSplits(CallbackInfo ci) {
        this.godweer$glassSplits.clear();
        this.godweer$previousElementWasGlass = false;
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"))
    private void godweer$markGlassRuns(GuiElementRenderState elementState, CallbackInfo ci) {
        boolean glass = godweer$isGlassPipeline(elementState.pipeline());
        if (glass && !this.godweer$previousElementWasGlass) {
            this.godweer$glassSplits.add(this.draws.size());
        }
        this.godweer$previousElementWasGlass = glass;
    }

    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;II)V"
            )
    )
    private void godweer$captureBackdropBeforeGlassRuns(
            GuiRenderer instance,
            Supplier<String> label,
            RenderTarget mainRenderTarget,
            GpuBufferSlice dynamicTransforms,
            int startIndex,
            int endIndex,
            Operation<Void> original
    ) {
        int cursor = startIndex;
        for (int i = 0; i < this.godweer$glassSplits.size(); i++) {
            int split = this.godweer$glassSplits.getInt(i);
            if (split < cursor || split >= endIndex) {
                continue;
            }
            if (split > cursor) {
                original.call(instance, label, mainRenderTarget, dynamicTransforms, cursor, split);
            }

            MenuBacks.captureNow(mainRenderTarget);
            cursor = split;
        }
        if (cursor < endIndex) {
            original.call(instance, label, mainRenderTarget, dynamicTransforms, cursor, endIndex);
        }
    }

    @Unique
    private static boolean godweer$isGlassPipeline(RenderPipeline pipeline) {
        return pipeline == Pipelines.BLUR_RECT;
    }
}