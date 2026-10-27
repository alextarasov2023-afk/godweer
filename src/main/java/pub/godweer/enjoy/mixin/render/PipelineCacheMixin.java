package pub.godweer.enjoy.mixin.render;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pub.godweer.auxiliary.render.ShaderFallback;

/**
 * Create by daun kvass
 * Все PipelineCache'и (fallback из preloadUiShader, кэш reload-фазы, кэши
 * пересборок при входе в мир) получают обёртку с 26.3-трансформером шейдеров
 * и classpath-фоллбэком для godweer-шейдеров.
 */
@Mixin(PipelineCache.class)
public class PipelineCacheMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static ShaderSource godweer$wrapShaderSource(ShaderSource source) {
        return ShaderFallback.wrap(source);
    }
}
