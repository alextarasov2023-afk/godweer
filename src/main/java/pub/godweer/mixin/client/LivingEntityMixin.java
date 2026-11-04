package pub.godweer.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.event.EventManager;
import pub.godweer.event.list.GlidingVelocityEvent;
import pub.godweer.event.list.JumpEvent;

/**
 * Create by daun kvass
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void onPlayerJump(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            JumpEvent event = new JumpEvent(player);
            EventManager.call(event);
            if (event.isCancelled()) ci.cancel();
        }
    }

    @Redirect(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float redirectJumpYaw(LivingEntity entity) {
        if (entity == Minecraft.getInstance().player && RotationHandler.isActive()) {
            return RotationHandler.getServerYaw();
        }
        return entity.getYRot();
    }
    @Inject(method = "travel", at = @At("HEAD"))
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity == Minecraft.getInstance().player && entity.isFallFlying()) {Vec3 currentVelocity = entity.getDeltaMovement().multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432);
            GlidingVelocityEvent event = new GlidingVelocityEvent(currentVelocity);
            EventManager.call(event);
            if (event.getVelocity() != currentVelocity) {entity.setDeltaMovement(event.getVelocity());}
        }
    }
}