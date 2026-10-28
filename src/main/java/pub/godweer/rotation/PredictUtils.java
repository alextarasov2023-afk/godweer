package pub.godweer.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public class PredictUtils {

    public enum Type {
        DEFAULT,
        LIMIT
    }

    public static Vec3 predictElytra(LivingEntity entity, float minusTicks, Type type) {
        if (!(entity instanceof Player player) || mc.player == null) return entity.position();

        Vec3 basePos = new Vec3(
                Mth.lerp(1.0F, player.xo, player.getX()),
                Mth.lerp(1.0F, player.yo, player.getY()),
                Mth.lerp(1.0F, player.zo, player.getZ())
        );

        Vec3 velocity = new Vec3(
                player.getX() - player.xo,
                player.getY() - player.yo,
                player.getZ() - player.zo
        );

        float ticks = (float) (mc.player.getEyePosition().distanceTo(entity.getBoundingBox().getCenter()) * minusTicks) + 3.0F;

        if (type == Type.LIMIT) {
            ticks = Mth.clamp(ticks, 0.0F, 2.7F);
        }

        float deltaYaw = Mth.wrapDegrees(player.getYRot() - player.yRotO);
        float deltaPitch = Mth.wrapDegrees(player.getXRot() - player.xRotO);

        Vec3 predictedPos = basePos;
        int intTicks = (int) ticks;
        float frac = ticks - intTicks;

        for (int i = 0; i < intTicks; i++) {
            double speed = velocity.length();
            if (speed > 0.001) {
                double velYaw = Math.atan2(-velocity.x, velocity.z);
                double velPitch = -Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z));

                velYaw += Math.toRadians(deltaYaw);
                velPitch += Math.toRadians(deltaPitch);

                velPitch = Mth.clamp(velPitch, -Math.PI / 2.0, Math.PI / 2.0);

                velocity = new Vec3(
                        -Math.sin(velYaw) * Math.cos(velPitch) * speed,
                        -Math.sin(velPitch) * speed,
                        Math.cos(velYaw) * Math.cos(velPitch) * speed
                );
            }
            predictedPos = predictedPos.add(velocity);
        }

        if (frac > 0) {
            double speed = velocity.length();
            if (speed > 0.001) {
                double velYaw = Math.atan2(-velocity.x, velocity.z);
                double velPitch = -Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z));

                velYaw += Math.toRadians(deltaYaw * frac);
                velPitch += Math.toRadians(deltaPitch * frac);

                velPitch = Mth.clamp(velPitch, -Math.PI / 2.0, Math.PI / 2.0);

                velocity = new Vec3(
                        -Math.sin(velYaw) * Math.cos(velPitch) * speed,
                        -Math.sin(velPitch) * speed,
                        Math.cos(velYaw) * Math.cos(velPitch) * speed
                );
            }
            predictedPos = predictedPos.add(velocity.scale(frac));
        }

        return predictedPos;
    }

    public static Vec3 realPredict(LivingEntity entity, Type type) {
        if (mc.player == null) return entity.position();
        Vec3 predict = predictElytra(entity, 0, type);
        float minusTicks = Mth.clamp((float) mc.player.getEyePosition().distanceTo(predict) / 100.0F, 0.0F, 12.0F);
        return predictElytra(entity, minusTicks, type);
    }
}