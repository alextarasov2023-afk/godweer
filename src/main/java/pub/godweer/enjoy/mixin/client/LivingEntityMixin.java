package pub.godweer.enjoy.mixin.client;

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
import pub.godweer.cc.RotationHandler;
import pub.godweer.enjoy.event.EventManager;
import pub.godweer.enjoy.event.list.GlidingVelocityEvent;
import pub.godweer.enjoy.event.list.JumpEvent;
import pub.godweer.kitty.module.impl.movement.WaterSpeed;
import pub.godweer.kitty.module.impl.render.PopEffect;
import pub.godweer.kitty.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
    private void onGetEffectBlendFactor(Holder<MobEffect> effect, float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this == Minecraft.getInstance().player && Removals.INSTANCE != null && Removals.INSTANCE.isBadEffectsDisabled()) {
            cir.setReturnValue(0.0F);
        }
    }
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
    @Inject(method = "die", at = @At("HEAD"))
    private void onEntityDie(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        PopEffect popEffect = pub.godweer.Client.INSTANCE.moduleManager.getModule(PopEffect.class);
        if (popEffect != null && popEffect.isEnabled()) {
            popEffect.onEntityKill(entity);
        }
    }
    @ModifyVariable(method = "setSprinting", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean setSprintingHook(boolean sprinting) {
        Minecraft mc = Minecraft.getInstance();

        if (WaterSpeed.INSTANCE != null && WaterSpeed.INSTANCE.isEnabled()) {
            if (mc.player != null && mc.level != null && (Object) this == mc.player) {
                BlockPos blockPos = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
                if (mc.player.isInWater() || mc.level.getBlockState(blockPos).getBlock() instanceof LiquidBlock) {
                    return true;
                }
            }
        }

        return sprinting;
    }
}