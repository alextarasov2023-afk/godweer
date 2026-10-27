package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.kitty.module.impl.render.Ambience;
import pub.godweer.kitty.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void onGetRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isWeatherDisabled()) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void onGetThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isWeatherDisabled()) {
            cir.setReturnValue(0.0F);
        }
    }
}