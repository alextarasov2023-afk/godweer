package pub.godweer.mixin.render;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.module.impl.render.Rain;
import pub.godweer.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    // В 26.3 LevelRenderer.addWeatherPass удалён; погода теперь рисуется через WeatherEffectRenderer.
    @Inject(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
    private void onRender(WeatherRenderState state, RenderPass pass, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isWeatherDisabled()) {
            ci.cancel();
            return;
        }
        Rain rain = Client.INSTANCE.moduleManager.getRain();
        if (rain != null && rain.isState()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOit(Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderOit(OitStage stage, WeatherRenderState state, RenderPass pass, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isWeatherDisabled()) {
            ci.cancel();
            return;
        }
        Rain rain = Client.INSTANCE.moduleManager.getRain();
        if (rain != null && rain.isState()) {
            ci.cancel();
        }
    }
}
