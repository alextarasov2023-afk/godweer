package pub.godweer.mixin.render;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pub.godweer.util.render.ShaderFallback;

/**
 * Create by daun kvass
 */
@Mixin(ShaderManager.class)
public class ShaderManagerMixin {

    // В 26.3 источники шейдеров проходят через ShaderManager.compilePipelines
    // (а не через GlDevice/VulkanDevice.getOrCompileShader, как в 26.2).
    @ModifyVariable(method = "compilePipelines", at = @At("HEAD"), argsOnly = true)
    private static ShaderSource godweer$wrapShaderSource(ShaderSource source) {
        return ShaderFallback.wrap(source);
    }
}
