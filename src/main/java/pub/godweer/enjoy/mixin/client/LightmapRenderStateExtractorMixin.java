package pub.godweer.enjoy.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.kitty.module.impl.misc.FullBright;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;"), to = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;darknessEffectScale()Lnet/minecraft/client/OptionInstance;")))
    private Object dynamicFullBrightGamma(Object original) {
        return original instanceof Double gamma ? FullBright.modifyGamma(gamma) : original;
    }

    @Inject(method = "calculateDarknessScale", at = @At("HEAD"), cancellable = true)
    private void suppressDarkness(LivingEntity entity, float factor, float partialTick, CallbackInfoReturnable<Float> callback) {
        if (FullBright.shouldSuppressDarkness()) {
            callback.setReturnValue(0.0F);
        }
    }
}