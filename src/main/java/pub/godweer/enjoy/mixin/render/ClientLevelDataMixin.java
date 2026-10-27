package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.kitty.module.impl.render.Ambience;

/**
 * Create by daun kvass
 */
@Mixin(ClientLevel.ClientLevelData.class)
public class ClientLevelDataMixin {

    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true)
    private void onGetGameTime(CallbackInfoReturnable<Long> cir) {
        if (Ambience.INSTANCE != null && Ambience.INSTANCE.isCustomTimeEnabled()) {
            cir.setReturnValue(Ambience.INSTANCE.getCustomDayTime());
        }
    }
}