package pub.godweer.cc.rotations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.AuraRotation;
import pub.godweer.cc.RotationHandler;

import java.util.concurrent.ThreadLocalRandom;

public final class SmoothRotation implements AuraRotation {
    private static final Minecraft mc = Minecraft.getInstance();

    private float smoothYawSpeed = 0.0F;
    private float smoothPitchSpeed = 0.0F;

    @Override
    public String getName() {
        return "Smooth";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
        if (target == null || player == null || targetEyePos == null) {
            reset();
            return;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 delta = targetEyePos.subtract(eyes);

        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        float currentYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
        float currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();

        float diffYaw = Mth.wrapDegrees(targetYaw - currentYaw);
        float diffPitch = Mth.clamp(targetPitch, -89.5F, 89.5F) - currentPitch;

        float factorYaw = attackLikely
                ? ThreadLocalRandom.current().nextFloat(0.55F, 0.72F)
                : ThreadLocalRandom.current().nextFloat(0.32F, 0.45F);
        float factorPitch = attackLikely
                ? ThreadLocalRandom.current().nextFloat(0.50F, 0.66F)
                : ThreadLocalRandom.current().nextFloat(0.26F, 0.38F);

        float maxYawStep = attackLikely
                ? ThreadLocalRandom.current().nextFloat(50.0F, 68.0F)
                : ThreadLocalRandom.current().nextFloat(28.0F, 40.0F);
        float maxPitchStep = attackLikely
                ? ThreadLocalRandom.current().nextFloat(30.0F, 44.0F)
                : ThreadLocalRandom.current().nextFloat(18.0F, 26.0F);

        float desiredYawStep = Mth.clamp(diffYaw * factorYaw, -maxYawStep, maxYawStep);
        float desiredPitchStep = Mth.clamp(diffPitch * factorPitch, -maxPitchStep, maxPitchStep);

        smoothYawSpeed = Mth.lerp(0.65F, smoothYawSpeed, desiredYawStep);
        smoothPitchSpeed = Mth.lerp(0.65F, smoothPitchSpeed, desiredPitchStep);

        if (Math.abs(diffYaw) > 1.5F) {
            smoothYawSpeed += ThreadLocalRandom.current().nextFloat(-0.35F, 0.35F);
        }
        if (Math.abs(diffPitch) > 1.5F) {
            smoothPitchSpeed += ThreadLocalRandom.current().nextFloat(-0.25F, 0.25F);
        }

        float nextYaw = currentYaw + smoothYawSpeed;
        float nextPitch = Mth.clamp(currentPitch + smoothPitchSpeed, -89.5F, 89.5F);

        RotationHandler.setRotation(nextYaw, nextPitch);
    }

    @Override
    public void reset() {
        smoothYawSpeed = 0.0F;
        smoothPitchSpeed = 0.0F;
    }
}
