package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.kitty.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void onCreateParticle(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.shouldCancelParticle(options)) {
            cir.setReturnValue(null);
        }
    }
}