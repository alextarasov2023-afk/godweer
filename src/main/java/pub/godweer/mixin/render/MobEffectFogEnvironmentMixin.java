package pub.godweer.mixin.render;

import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {
    @Shadow
    public abstract Holder<MobEffect> getMobEffect();

    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void onIsApplicable(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Holder<MobEffect> effect = this.getMobEffect();
        if (Removals.INSTANCE != null && Removals.INSTANCE.isBadEffectsDisabled()) {
            if (effect.equals(MobEffects.DARKNESS) || effect.equals(MobEffects.BLINDNESS)) {
                cir.setReturnValue(false);
            }
        }
    }
}