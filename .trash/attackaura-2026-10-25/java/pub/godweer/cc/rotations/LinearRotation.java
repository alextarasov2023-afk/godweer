package pub.godweer.cc.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.AuraRotation;
import pub.godweer.cc.RotationHandler;

/**
 * Create by daun kvass
 */
public final class LinearRotation implements AuraRotation {
    @Override
    public String getName() {
        return "Linear";
    }

    @Override
    public void tick(LocalPlayer player, LivingEntity target, Vec3 targetEyePos, boolean attackLikely) {
        if (target == null) return;
        Vec3 delta = targetEyePos.subtract(player.getEyePosition());
        float rawYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float rawPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        float currentYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
        float currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();

        float deltaYaw = Mth.wrapDegrees(rawYaw - currentYaw);
        float deltaPitch = rawPitch - currentPitch;

        float speed = 55.0f;
        float stepYaw = Mth.clamp(deltaYaw, -speed, speed);
        float stepPitch = Mth.clamp(deltaPitch, -speed, speed);

        RotationHandler.setRotation(currentYaw + stepYaw, currentPitch + stepPitch);
    }
}