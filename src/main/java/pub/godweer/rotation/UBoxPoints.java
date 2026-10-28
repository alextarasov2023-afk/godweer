package pub.godweer.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public final class UBoxPoints {

    public record TargetPoint(Vec3 point, boolean isVisible) {}

    // Последняя найденная видимая точка прицеливания: переиспользуем, пока она
    // видна, вместо скана всех 20 точек (и прыжков ротации между ними) каждый тик.
    private static Entity lastPointTarget;
    private static Vec3 lastVisiblePoint;

    public static TargetPoint getBestPoint(Entity target) {
        if (mc.player == null || target == null) return null;

        Vec3 eyes = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();

        double centerX = box.minX + (box.maxX - box.minX) * 0.5;
        double centerZ = box.minZ + (box.maxZ - box.minZ) * 0.5;
        double height = box.maxY - box.minY;

        Vec3 stableChest = new Vec3(centerX, box.minY + height * 0.65, centerZ);
        if (RayTraceUtils.canSeePoint(eyes, stableChest)) {
            lastPointTarget = target;
            lastVisiblePoint = stableChest;
            return new TargetPoint(stableChest, true);
        }

        if (target == lastPointTarget && lastVisiblePoint != null
                && RayTraceUtils.canSeePoint(eyes, lastVisiblePoint)) {
            return new TargetPoint(lastVisiblePoint, true);
        }
        lastVisiblePoint = null;

        List<Vec3> points = calculatePoints(box);
        float currentYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();
        float currentPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : mc.player.getXRot();

        // Сортируем по углу к текущему прицелу: первый видимый — это и есть
        // лучший по углу, но рейкастов делаем ровно столько, сколько нужно.
        points.sort(java.util.Comparator.comparingDouble(p -> getAngleDifference(eyes, p, currentYaw, currentPitch)));
        for (Vec3 point : points) {
            if (RayTraceUtils.canSeePoint(eyes, point)) {
                lastPointTarget = target;
                lastVisiblePoint = point;
                return new TargetPoint(point, true);
            }
        }

        Vec3 wallPoint = getClosestBoxPoint(eyes, box);
        return new TargetPoint(wallPoint, false);
    }

    public static List<Vec3> calculatePoints(AABB box) {
        List<Vec3> points = new ArrayList<>(20);

        double centerX = box.minX + (box.maxX - box.minX) * 0.5;
        double centerZ = box.minZ + (box.maxZ - box.minZ) * 0.5;
        double height = box.maxY - box.minY;
        double inset = 0.08;

        points.add(new Vec3(centerX, box.maxY - 0.15, centerZ));
        points.add(new Vec3(centerX, box.minY + height * 0.75, centerZ));
        points.add(new Vec3(centerX, box.minY + height * 0.50, centerZ));
        points.add(new Vec3(centerX, box.minY + height * 0.25, centerZ));
        points.add(new Vec3(centerX, box.minY + 0.10, centerZ));

        double footY = box.minY + 0.12;
        double legY = box.minY + 0.25;

        points.add(new Vec3(box.minX + inset, footY, box.minZ + inset));
        points.add(new Vec3(box.maxX - inset, footY, box.minZ + inset));
        points.add(new Vec3(box.minX + inset, footY, box.maxZ - inset));
        points.add(new Vec3(box.maxX - inset, footY, box.maxZ - inset));

        points.add(new Vec3(box.minX + inset, legY, centerZ));
        points.add(new Vec3(box.maxX - inset, legY, centerZ));
        points.add(new Vec3(centerX, legY, box.minZ + inset));
        points.add(new Vec3(centerX, legY, box.maxZ - inset));

        double midY = box.minY + height * 0.55;
        points.add(new Vec3(box.minX + inset, midY, box.minZ + inset));
        points.add(new Vec3(box.maxX - inset, midY, box.minZ + inset));
        points.add(new Vec3(box.minX + inset, midY, box.maxZ - inset));
        points.add(new Vec3(box.maxX - inset, midY, box.maxZ - inset));

        return points;
    }

    public static Vec3 getClosestBoxPoint(Vec3 from, AABB box) {
        double x = Mth.clamp(from.x, box.minX, box.maxX);
        double y = Mth.clamp(from.y, box.minY, box.maxY);
        double z = Mth.clamp(from.z, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private static double getAngleDifference(Vec3 from, Vec3 to, float yaw, float pitch) {
        double diffX = to.x - from.x;
        double diffY = to.y - from.y;
        double diffZ = to.z - from.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        float deltaYaw = Math.abs(Mth.wrapDegrees(targetYaw - yaw));
        float deltaPitch = Math.abs(targetPitch - pitch);

        return Math.hypot(deltaYaw, deltaPitch);
    }
}
