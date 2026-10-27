package pub.godweer.enjoy.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.ShaderSource.CachedIncludeSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import pub.godweer.auxiliary.render.ShaderFallback;

/**
 * Create by daun kvass
 * GameRenderer.preloadUiShader создаёт анонимный GameRenderer$1 (ResourceManager-backed
 * ShaderSource) и ставит его в fallback PipelineCache до загрузки ресурсов; этот менеджер
 * не содержит шейдеров мода. Оборачиваем getShader/getInclude класспас-фоллбэком.
 */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer$1")
public abstract class GameRendererShaderSourceMixin {

    @WrapMethod(method = "getShader")
    private String godweer$wrapGetShader(Identifier shaderId, ShaderType shaderType, Operation<String> original) {
        String result;
        try {
            result = original.call(shaderId, shaderType);
        } catch (Throwable ignored) {
            result = null;
        }
        if (result != null) {
            return ShaderFallback.transform(result);
        }
        return ShaderFallback.load(shaderId, shaderType);
    }

    @WrapMethod(method = "getInclude")
    private CachedIncludeSource godweer$wrapGetInclude(Identifier includeId, Operation<CachedIncludeSource> original) {
        CachedIncludeSource result;
        try {
            result = original.call(includeId);
        } catch (Throwable ignored) {
            result = null;
        }
        if (result != null) {
            return result;
        }
        String text = ShaderFallback.loadIncludeFallback(includeId);
        return text == null ? null : CachedIncludeSource.create(includeId, text);
    }
}

