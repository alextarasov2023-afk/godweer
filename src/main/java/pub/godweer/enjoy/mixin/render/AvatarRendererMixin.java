package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.Client;
import pub.godweer.kitty.module.impl.render.NameTags;

/**
 * Create by daun kvass
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void hideVanillaNameTag(Avatar avatar, double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
        NameTags module = Client.INSTANCE.moduleManager.getNameTags();
        if (module != null && module.isState()) {
            cir.setReturnValue(false);
        }
    }
}