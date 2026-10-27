package pub.godweer.cc.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.AuraRotation;
import pub.godweer.cc.RotationHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Create by daun kvass
 */
public final class FuntimeRotation implements AuraRotation {

    private float phase = 0.0f;

    @Override
    public String getName() {
        return "Funtime";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
        if (target == null) return;

        Vec3 baseAimPos = targetEyePos.subtract(0.0, 0.28, 0.0);

        Vec3 delta = baseAimPos.subtract(player.getEyePosition());
        float rawYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float rawPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        float phaseSpeed = ThreadLocalRandom.current().nextFloat(0.37f, 0.49f);
        phase += phaseSpeed;

        float infinityYawScale = (float) ThreadLocalRandom.current().nextDouble(8.0, 11.5);
        float infinityPitchScale = (float) ThreadLocalRandom.current().nextDouble(4.5, 6.0);

        float noiseYaw = (float) ThreadLocalRandom.current().nextDouble(-0.8, 0.8);
        float noisePitch = (float) ThreadLocalRandom.current().nextDouble(-0.5, 0.5);

        float figureEightYaw = (float) (Math.sin(phase) * infinityYawScale) + noiseYaw;
        float figureEightPitch = (float) (Math.sin(phase * 2.0f) * infinityPitchScale) + noisePitch;

        rawYaw += figureEightYaw;
        rawPitch += figureEightPitch;

        float currentYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
        float currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();

        float deltaYaw = Mth.wrapDegrees(rawYaw - currentYaw);
        float deltaPitch = rawPitch - currentPitch;

        float turnSpeed = (float) ThreadLocalRandom.current().nextDouble(75.0, 110.0);

        float stepYaw = Mth.clamp(deltaYaw * 0.85f, -turnSpeed, turnSpeed);
        float stepPitch = Mth.clamp(deltaPitch * 0.85f, -turnSpeed, turnSpeed);

        float finalYaw = currentYaw + stepYaw;
        float finalPitch = Mth.clamp(currentPitch + stepPitch, -89.5F, 89.5F);

        RotationHandler.setRotation(finalYaw, finalPitch);
    }
}