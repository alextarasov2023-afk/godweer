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
public final class SpookyTime implements AuraRotation {
    private double velocityYaw, velocityPitch;
    private double windYaw, windPitch;
    private double speed = 16.0, gravity = 10.0, wind = 2.0;

    @Override
    public String getName() {
        return "SpookyTime";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
        if (target == null || !target.isAlive()) {
            reset();
            RotationHandler.clear();
            return;
        }

        double currentYaw = Mth.wrapDegrees(RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot());
        double currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();

        Vec3 delta = targetEyePos.subtract(player.getEyePosition());
        double realYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F);
        double realPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        double deltaYaw = Mth.wrapDegrees(realYaw - currentYaw);
        double deltaPitch = realPitch - currentPitch;
        double targetDistance = Math.hypot(deltaYaw, deltaPitch);

        if (attackLikely || targetDistance < 0.25D) {
            RotationHandler.setRotation((float) realYaw, (float) realPitch);
            return;
        }

        this.windYaw = this.windYaw * 0.86D + ThreadLocalRandom.current().nextDouble(-wind, wind) * 0.14D;
        this.windPitch = this.windPitch * 0.86D + ThreadLocalRandom.current().nextDouble(-wind, wind) * 0.14D;

        this.velocityYaw += this.windYaw + (gravity * deltaYaw / targetDistance);
        this.velocityPitch += this.windPitch + (gravity * deltaPitch / targetDistance);

        double mag = Math.hypot(this.velocityYaw, this.velocityPitch);
        if (mag > speed) {
            this.velocityYaw = this.velocityYaw / mag * speed;
            this.velocityPitch = this.velocityPitch / mag * speed;
        }

        double nextYaw = Mth.wrapDegrees(currentYaw + this.velocityYaw);
        double nextPitch = Mth.clamp(currentPitch + this.velocityPitch, -90.0D, 90.0D);
        RotationHandler.setRotation((float) nextYaw, (float) nextPitch);
    }

    @Override
    public void reset() {
        this.velocityYaw = 0.0;
        this.velocityPitch = 0.0;
        this.windYaw = 0.0;
        this.windPitch = 0.0;
    }
}