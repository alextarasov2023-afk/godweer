package pub.godweer.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.event.EventManager;
import pub.godweer.event.list.EventTravel;

/**
 * Create by daun kvass
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    protected static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
        return Vec3.ZERO;
    }

    @Redirect(
            method = "moveRelative",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 moveFix(Vec3 movementInput, float speed, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this == mc.player) {
            if (RotationHandler.isActive()) {
                return getInputVector(movementInput, speed, RotationHandler.getServerYaw());
            }
        }
        return getInputVector(movementInput, speed, yaw);
    }

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void onGetViewVector(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();

        if (self == mc.player && RotationHandler.isActive()) {
            float pitch = RotationHandler.getServerPitch();
            float yaw = RotationHandler.getServerYaw();

            float f = pitch * ((float) Math.PI / 180F);
            float g = -yaw * ((float) Math.PI / 180F);
            float h = Mth.cos(g);
            float i = Mth.sin(g);
            float j = Mth.cos(f);
            float k = Mth.sin(f);

            cir.setReturnValue(new Vec3(i * j, -k, h * j));
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void onMove(MoverType moverType, Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer) {
            EventTravel event = new EventTravel();
            EventManager.call(event);

            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    // В 26.3 LocalPlayer больше не переопределяет getViewXRot — инжект живёт на базовом Entity.
    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    private void onLocalPlayerGetViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer && RotationHandler.isActive()) {
            cir.setReturnValue(RotationHandler.getFreePitch());
        }
    }
}
