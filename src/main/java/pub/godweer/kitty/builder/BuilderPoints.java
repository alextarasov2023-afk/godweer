package pub.godweer.kitty.builder;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.cc.UBoxPoints.TargetPoint;

import java.util.List;

public final class BuilderPoints {

    public static TargetPoint getBestPoint(Player player, LivingEntity target) {
        if (player == null || target == null) return null;

        var profile = RotationBuilderManager.INSTANCE.getActiveProfile();
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();

        boolean isCrit = player.fallDistance > 0.0F && !player.onGround() && !player.isInWater();

        double bestRelY = isCrit ? profile.critRelY : profile.groundRelY;
        Vec3 primaryPoint = getAbsolutePoint(box, profile.relX, bestRelY, profile.relZ);

        if (RayTraceUtils.canSeePoint(eye, primaryPoint)) {
            return new TargetPoint(primaryPoint, true);
        }

        List<double[]> clusters = profile.learnedClusters;
        if (clusters != null && !clusters.isEmpty()) {
            for (double[] point : clusters) {
                Vec3 clusterPoint = getAbsolutePoint(box, point[0], point[1], point[2]);
                if (RayTraceUtils.canSeePoint(eye, clusterPoint)) {
                    return new TargetPoint(clusterPoint, true);
                }
            }
        }

        double clampedX = Math.clamp(eye.x, box.minX, box.maxX);
        double clampedY = Math.clamp(eye.y, box.minY, box.maxY);
        double clampedZ = Math.clamp(eye.z, box.minZ, box.maxZ);
        Vec3 fallbackPoint = new Vec3(clampedX, clampedY, clampedZ);

        return new TargetPoint(fallbackPoint, RayTraceUtils.canSeePoint(eye, fallbackPoint));
    }

    private static Vec3 getAbsolutePoint(AABB box, double rx, double ry, double rz) {
        return new Vec3(
                box.minX + (box.maxX - box.minX) * rx,
                box.minY + (box.maxY - box.minY) * ry,
                box.minZ + (box.maxZ - box.minZ) * rz
        );
    }
}