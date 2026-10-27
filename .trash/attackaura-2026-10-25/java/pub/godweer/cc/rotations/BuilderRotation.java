package pub.godweer.cc.rotations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.AuraRotation;
import pub.godweer.cc.RotationHandler;
import pub.godweer.kitty.builder.RotationBuilderManager;

import java.util.concurrent.ThreadLocalRandom;

public final class BuilderRotation implements AuraRotation {
    private static final Minecraft mc = Minecraft.getInstance();

    private float smoothYawVelocity = 0.0F;
    private float smoothPitchVelocity = 0.0F;

    @Override
    public String getName() {
        return "Builder";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetAimPos, boolean attackLikely) {
        if (target == null || player == null || targetAimPos == null) {
            smoothYawVelocity = 0.0F;
            smoothPitchVelocity = 0.0F;
            return;
        }

        var profile = RotationBuilderManager.INSTANCE.getActiveProfile();
        Vec3 eyes = player.getEyePosition();
        Vec3 delta = targetAimPos.subtract(eyes);

        float rawYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float rawPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        double dist = eyes.distanceTo(targetAimPos);
        float pitchCorrection = profile.pitchBias + (float) ((dist - 3.0) * profile.pitchDistWeight);
        rawPitch = Mth.clamp(rawPitch + pitchCorrection, -89.5F, 89.5F);

        float currentYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
        float currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();

        float deltaYaw = Mth.wrapDegrees(rawYaw - currentYaw);
        float deltaPitch = rawPitch - currentPitch;
        float totalError = (float) Math.hypot(deltaYaw, deltaPitch);

        float maxSpeed;
        if (totalError <= 5.0F) {
            maxSpeed = profile.microSpeed;
        } else if (totalError <= 20.0F) {
            maxSpeed = profile.trackingSpeed;
        } else if (totalError <= 50.0F) {
            maxSpeed = profile.flickSpeed;
        } else {
            maxSpeed = profile.snapSpeed;
        }

        if (attackLikely) {
            maxSpeed *= 1.25F;
        }

        float inertia = profile.inertiaFactor;
        float stepYaw = Mth.clamp(deltaYaw, -maxSpeed, maxSpeed);
        float stepPitch = Mth.clamp(deltaPitch, -maxSpeed, maxSpeed);

        smoothYawVelocity = smoothYawVelocity + (stepYaw - smoothYawVelocity) * (1.0F - inertia);
        smoothPitchVelocity = smoothPitchVelocity + (stepPitch - smoothPitchVelocity) * (1.0F - inertia);

        if (totalError > 1.2F && profile.jitterIntensity > 0.05F) {
            smoothYawVelocity += (float) ThreadLocalRandom.current().nextDouble(-profile.jitterIntensity, profile.jitterIntensity);
            smoothPitchVelocity += (float) ThreadLocalRandom.current().nextDouble(-profile.jitterIntensity * 0.6, profile.jitterIntensity * 0.6);
        }

        float nextYaw = currentYaw + smoothYawVelocity;
        float nextPitch = Mth.clamp(currentPitch + smoothPitchVelocity, -89.5F, 89.5F);

        double sens = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double gcd = sens * sens * sens * 1.2D;
        if (gcd >= 1.0E-4D) {
            float fYaw = nextYaw - currentYaw;
            float fPitch = nextPitch - currentPitch;
            fYaw = (float) (Math.round(fYaw / gcd) * gcd);
            fPitch = (float) (Math.round(fPitch / gcd) * gcd);
            nextYaw = currentYaw + fYaw;
            nextPitch = currentPitch + fPitch;
        }

        RotationHandler.setRotation(nextYaw, nextPitch);
    }
}