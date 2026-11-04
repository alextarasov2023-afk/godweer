package pub.godweer.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.module.impl.misc.FreeCam;

/**
 * Create by daun kvass
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow private Vec3 position;
    @Shadow private float yRot;
    @Shadow private float xRot;

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void onAlignWithEntityHead(float partialTick, CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        Entity entity = camera.entity();
        if (entity == null) return;

        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            FreeCam.INSTANCE.updateMovement();
        }

        RotationHandler.applyRenderInterpolation();
        RotationHandler.syncFreeLook(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
    }

    @WrapOperation(
            method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F")
    )
    private float wrapGetViewYRot(Entity entity, float partialTick, Operation<Float> original) {
        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            return FreeCam.INSTANCE.yaw;
        }
        return original.call(entity, partialTick);
    }

    @WrapOperation(
            method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F")
    )
    private float wrapGetViewXRot(Entity entity, float partialTick, Operation<Float> original) {
        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            return FreeCam.INSTANCE.pitch;
        }
        return original.call(entity, partialTick);
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntityTail(float partialTick, CallbackInfo ci) {
        FreeCam freeCam = FreeCam.INSTANCE;
        if (freeCam == null) return;

        if (freeCam.isEnabled()) {
            double interpX = Mth.lerp((double) partialTick, freeCam.prevPosX, freeCam.posX);
            double interpY = Mth.lerp((double) partialTick, freeCam.prevPosY, freeCam.posY);
            double interpZ = Mth.lerp((double) partialTick, freeCam.prevPosZ, freeCam.posZ);

            setPosition(interpX, interpY, interpZ);
            setRotation(freeCam.yaw, freeCam.pitch);
            return;
        }

        // Камера вернулась к игроку: гасим признак только что выключенной FreeCam.
        freeCam.justDisabled = false;
    }
}
