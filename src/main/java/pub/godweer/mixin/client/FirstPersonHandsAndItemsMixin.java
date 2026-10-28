package pub.godweer.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Руки от первого лица всегда берут угол того кадра, который игрок реально видит.
 *
 * В 26.2 эти редиректы жили в FirstPersonHandsAndItemsRenderer.submitHandsWithItems,
 * в 26.3 extractRenderState (FirstPersonHandsAndItems) пересчитал viewXRot/viewYRot.
 * Раньше при активной ротации ауры руки отдавались «свободному» углу RotationHandler —
 * накопителю мышиных дельт без подинтерполяции и без инерции камеры. Камера при этом
 * едет по своему сглаженному углу, поэтому руки плыли и дёргались на каждом повороте.
 * Камера — единственный источник угла для кадра; расхождение с ней всегда выглядит багом.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"))
    private float useCameraPitchForHands(LocalPlayer player, float partialTick) {
        return Minecraft.getInstance().gameRenderer.mainCamera().xRot();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"))
    private float useCameraYawForHands(LocalPlayer player, float partialTick) {
        return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
    }
}
