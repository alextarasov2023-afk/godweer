package pub.godweer.enjoy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pub.godweer.cc.RotationHandler;

/**
 * Create by daun kvass
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {

    /**
     * В 26.2 эти ауры-редиректы жили в FirstPersonHandsAndItemsRenderer.submitHandsWithItems,
     * а в 26.3 extractRenderState (FirstPersonHandsAndItems) пересчитал viewXRot/viewYRot.
     * Руки всегда смотрят по камере, а не по (возможно подменённой вью-ротации) игроку;
     * при активной ротации ауры — по свободной ротации RotationHandler.
     */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"))
    private float useFreeLookPitchForHands(LocalPlayer player, float partialTick) {
        if (RotationHandler.isActive()) {
            return RotationHandler.getFreePitch();
        }
        return Minecraft.getInstance().gameRenderer.mainCamera().xRot();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"))
    private float useCameraYawForHands(LocalPlayer player, float partialTick) {
        if (RotationHandler.isActive()) {
            return RotationHandler.getFreeYaw();
        }
        return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
    }
}
