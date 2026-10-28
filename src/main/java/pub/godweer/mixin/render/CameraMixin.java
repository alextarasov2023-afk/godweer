package pub.godweer.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
import pub.godweer.module.impl.render.BetterMinecraft;
import pub.godweer.module.impl.render.FreeLook;
import pub.godweer.module.impl.render.Removals;

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
    @Shadow private boolean detached;

    private double smoothX, smoothY, smoothZ;
    private float smoothYaw, smoothPitch;
    private boolean cameraInitialized = false;

    private boolean isTransitioning = false;
    private float transitionProgress = 1.0F;
    private float currentDuration = 0.4F;

    private double startOffsetX, startOffsetY, startOffsetZ;
    private float startOffsetYaw, startOffsetPitch;

    private CameraType lastCameraType = null;
    private long lastTimeMs = System.currentTimeMillis();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(CameraRenderState cameraState, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isBadEffectsDisabled()) {
            cameraState.entityRenderState.doesMobEffectBlockSky = false;
        }
    }

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void onAlignWithEntityHead(float partialTick, CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        Entity entity = camera.entity();
        if (entity == null) return;

        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            FreeCam.INSTANCE.updateMovement();
        }

        if (FreeLook.INSTANCE != null) {
            FreeLook.INSTANCE.update();
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
        if (FreeLook.INSTANCE != null && FreeLook.INSTANCE.isActive()) {
            return FreeLook.cameraYaw;
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
        if (FreeLook.INSTANCE != null && FreeLook.INSTANCE.isActive()) {
            return FreeLook.cameraPitch;
        }
        return original.call(entity, partialTick);
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntityTail(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        float deltaTime = Math.min(0.1F, Math.max(0.001F, (now - lastTimeMs) / 1000.0F));
        lastTimeMs = now;

        FreeCam freeCam = FreeCam.INSTANCE;
        BetterMinecraft mod = BetterMinecraft.INSTANCE;

        if (freeCam != null && freeCam.isEnabled()) {
            double interpX = Mth.lerp((double) partialTick, freeCam.prevPosX, freeCam.posX);
            double interpY = Mth.lerp((double) partialTick, freeCam.prevPosY, freeCam.posY);
            double interpZ = Mth.lerp((double) partialTick, freeCam.prevPosZ, freeCam.posZ);

            setPosition(interpX, interpY, interpZ);
            setRotation(freeCam.yaw, freeCam.pitch);

            smoothX = interpX;
            smoothY = interpY;
            smoothZ = interpZ;
            smoothYaw = freeCam.yaw;
            smoothPitch = freeCam.pitch;
            isTransitioning = false;
            return;
        }

        double targetX = this.position.x;
        double targetY = this.position.y;
        double targetZ = this.position.z;
        float targetYaw = this.yRot;
        float targetPitch = this.xRot;

        if (freeCam != null && freeCam.justDisabled) {
            freeCam.justDisabled = false;

            if (mod != null && mod.isEnabled() && mod.modes.isEnabled("BossBar")) {
                startOffsetX = freeCam.lastCamX - targetX;
                startOffsetY = freeCam.lastCamY - targetY;
                startOffsetZ = freeCam.lastCamZ - targetZ;
                startOffsetYaw = Mth.wrapDegrees(freeCam.lastCamYaw - targetYaw);
                startOffsetPitch = freeCam.lastCamPitch - targetPitch;

                isTransitioning = true;
                transitionProgress = 0.0F;

                double dist = Math.sqrt(startOffsetX * startOffsetX + startOffsetY * startOffsetY + startOffsetZ * startOffsetZ);
                currentDuration = (float) Mth.clamp(0.4D + (dist * 0.035D), 0.45D, 1.25D);
            }
        }

        if (mod == null || !mod.isEnabled() || !mod.modes.isEnabled("BossBar")) {
            isTransitioning = false;
            cameraInitialized = false;
            lastCameraType = null;
            return;
        }

        CameraType currentType = mc.options.getCameraType();

        if (!cameraInitialized) {
            smoothX = targetX;
            smoothY = targetY;
            smoothZ = targetZ;
            smoothYaw = targetYaw;
            smoothPitch = targetPitch;
            cameraInitialized = true;
        }

        if (lastCameraType != null && currentType != lastCameraType && !isTransitioning) {
            startOffsetX = smoothX - targetX;
            startOffsetY = smoothY - targetY;
            startOffsetZ = smoothZ - targetZ;
            startOffsetYaw = Mth.wrapDegrees(smoothYaw - targetYaw);
            startOffsetPitch = smoothPitch - targetPitch;

            isTransitioning = true;
            transitionProgress = 0.0F;
            currentDuration = Mth.clamp(0.4F - (mod.animSpeed.getValue() / 1000.0F * 0.3F), 0.15F, 0.5F);
        }
        lastCameraType = currentType;

        boolean isKinematicInF5 = this.detached && mod.kinematicCamera.getValue();

        if (isTransitioning) {
            transitionProgress += deltaTime / currentDuration;
            if (transitionProgress >= 1.0F) {
                transitionProgress = 1.0F;
                isTransitioning = false;
            }

            float t = transitionProgress;
            float ease = (t < 0.5F) ? (4.0F * t * t * t) : (1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F);
            float remaining = 1.0F - ease;

            smoothX = targetX + startOffsetX * remaining;
            smoothY = targetY + startOffsetY * remaining;
            smoothZ = targetZ + startOffsetZ * remaining;

            smoothYaw = targetYaw + startOffsetYaw * remaining;
            smoothPitch = targetPitch + startOffsetPitch * remaining;

            setPosition(smoothX, smoothY, smoothZ);
            setRotation(smoothYaw, smoothPitch);
        } else if (isKinematicInF5) {
            float factor = 1.0F - (float) Math.exp(-6.0F * deltaTime);
            factor = Mth.clamp(factor, 0.001F, 1.0F);

            smoothX = Mth.lerp(factor, smoothX, targetX);
            smoothY = Mth.lerp(factor, smoothY, targetY);
            smoothZ = Mth.lerp(factor, smoothZ, targetZ);

            float yawDelta = Mth.wrapDegrees(targetYaw - smoothYaw);
            smoothYaw = Mth.wrapDegrees(smoothYaw + yawDelta * factor);
            smoothPitch = Mth.lerp(factor, smoothPitch, targetPitch);

            setPosition(smoothX, smoothY, smoothZ);
            setRotation(smoothYaw, smoothPitch);
        } else {
            smoothX = targetX;
            smoothY = targetY;
            smoothZ = targetZ;
            smoothYaw = targetYaw;
            smoothPitch = targetPitch;
        }
    }
}