package pub.godweer.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pub.godweer.module.impl.render.XrayState;

/**
 * Скрытие мира для Xray на этапе сборки секции.
 *
 * lambda$compile$0 и lambda$compile$1 — оба пути записи квадов блока: для
 * скрытых блоков putBlockBakedQuad не вызывается, поэтому в меш не попадает ни
 * один их квад и мир вокруг становится прозрачным. Жидкости FluidRenderer пишет
 * мимо BufferBuilder (через FluidRenderer$Output), поэтому их вызов гасится
 * отдельно. cull=false при активном xray — иначе грани руды, упёртые в камень,
 * срезаются и замурованные руды не видны; isSolidRender в compile кормит только
 * VisGraph, его гасим, чтобы закрытые секции не выкидывались из видимости.
 *
 * Create by daun kvass
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {

    @ModifyExpressionValue(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z"))
    private boolean godweer$xrayOcclusion(boolean original) {
        return XrayState.isActive() ? false : original;
    }

    @ModifyArg(method = "compile",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;<init>(ZZLnet/minecraft/client/color/block/BlockColors;)V"),
            index = 1)
    private boolean godweer$xrayNoCull(boolean original) {
        return XrayState.isActive() ? false : original;
    }

    @WrapOperation(method = {"lambda$compile$0", "lambda$compile$1"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;putBlockBakedQuad(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void godweer$xraySkipHidden(BufferBuilder builder, float x, float y, float z, BakedQuad quad,
                                        QuadInstance instance, Operation<Void> original) {
        if (XrayState.hideCurrent()) return;
        original.call(builder, x, y, z, quad, instance);
    }

    @WrapOperation(method = "compile",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
    private void godweer$xrayHideFluids(FluidRenderer renderer, BlockAndTintGetter level, BlockPos pos,
                                        FluidRenderer.Output output, BlockState state, FluidState fluid,
                                        Operation<Void> original) {
        if (XrayState.isActive()) return;
        original.call(renderer, level, pos, output, state, fluid);
    }
}
