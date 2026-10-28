package pub.godweer.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.module.impl.render.Ambience;

/**
 * Create by daun kvass
 */
// В 26.3 день/время хранятся в системе часов; getTotalTicks(Holder) у ClientClockManager удалён,
// время выдаёт вложенный ClientClockInstance.totalTicks().
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public class ClientClockManagerMixin {

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void onGetTotalTicks(CallbackInfoReturnable<Long> cir) {
        if (Ambience.INSTANCE != null && Ambience.INSTANCE.isCustomTimeEnabled()) {
            cir.setReturnValue(Ambience.INSTANCE.getCustomDayTime());
        }
    }
}
