package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.Client;
import pub.godweer.kitty.module.impl.render.NameTags;

/**
 * Create by daun kvass
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void hideEntityNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        NameTags module = Client.INSTANCE.moduleManager.getNameTags();
        if (module != null && module.isState() && entity instanceof Player) {
            cir.setReturnValue(null);
        }
    }

}