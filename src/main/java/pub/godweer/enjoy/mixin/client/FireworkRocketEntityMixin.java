package pub.godweer.enjoy.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import pub.godweer.Client;
import pub.godweer.enjoy.event.list.EventFirework;

/**
 * Create by daun kvass
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Shadow
    private LivingEntity attachedToEntity;

    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 1.5D))
    private double replaceSpeed(double original) {
        EventFirework event = new EventFirework(this.attachedToEntity, (float) original);
        Client.getInstance().getEventManager().call(event);
        return event.getSpeed();
    }
}