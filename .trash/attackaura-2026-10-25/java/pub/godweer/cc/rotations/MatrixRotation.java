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
public final class MatrixRotation implements AuraRotation {

    private Vec3 currentOffset = new Vec3(0.0, -0.2, 0.0);
    private int lastTargetId = -1;
    private boolean lastAttackState = false;

    @Override
    public String getName() {
        return "Matrix";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
        if (target == null) {
            lastTargetId = -1;
            return;
        }

        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            currentOffset = new Vec3(0.0, -0.25, 0.0);
        }

        if (attackLikely && !lastAttackState) {
            double offsetX = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
            double offsetY = ThreadLocalRandom.current().nextDouble(-0.85, 0.05);
            double offsetZ = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);

            currentOffset = new Vec3(offsetX, offsetY, offsetZ);
        }
        lastAttackState = attackLikely;

        Vec3 aimPos = targetEyePos.add(currentOffset);

        Vec3 delta = aimPos.subtract(player.getEyePosition());
        float rawYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float rawPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        float currentYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
        float currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();

        float deltaYaw = Mth.wrapDegrees(rawYaw - currentYaw);
        float deltaPitch = rawPitch - currentPitch;

        float speed = (float) ThreadLocalRandom.current().nextDouble(45.0, 75.0);

        float noiseYaw = (float) ThreadLocalRandom.current().nextDouble(-1.35, 1.35);
        float noisePitch = (float) ThreadLocalRandom.current().nextDouble(-0.95, 0.95);

        float stepYaw = Mth.clamp(deltaYaw * 0.65f + noiseYaw, -speed, speed);
        float stepPitch = Mth.clamp(deltaPitch * 0.65f + noisePitch, -speed, speed);

        float finalYaw = currentYaw + stepYaw;
        float finalPitch = Mth.clamp(currentPitch + stepPitch, -90.0F, 90.0F);

        RotationHandler.setRotation(finalYaw, finalPitch);
    }
}