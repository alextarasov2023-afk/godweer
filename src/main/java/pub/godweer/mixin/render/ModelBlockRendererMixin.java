package pub.godweer.mixin.render;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.module.impl.render.XrayState;

/**
 * Отдаёт в XrayState блок, чьи квады сейчас пишутся в секцию: решение «прятать
 * квад или нет» принимается в SectionCompiler-лямбдах, которые знают только
 * текущий квад (ThreadLocal снимается на RETURN).
 *
 * Create by daun kvass
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {

    // Первый параметр — ровно BlockQuadOutput: Sponge 0.8.7 сверяет дескриптор
    // @Inject-коллбэка строго, Object не проходит (APPLY-краш 2026-10-28).
    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void godweer$beginBlock(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                                    BlockPos pos, BlockState state, BlockStateModel model, long seed, CallbackInfo ci) {
        XrayState.beginBlock(state);
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void godweer$endBlock(CallbackInfo ci) {
        XrayState.endBlock();
    }
}
