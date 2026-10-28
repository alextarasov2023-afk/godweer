package pub.godweer.rotation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public final class RayTraceUtils {

    private RayTraceUtils() {}


    public static Vec3 getLookVec(float yaw, float pitch) {
        float f = pitch * ((float) Math.PI / 180F);
        float f1 = -yaw * ((float) Math.PI / 180F);
        float cosYaw = Mth.cos(f1);
        float sinYaw = Mth.sin(f1);
        float cosPitch = Mth.cos(f);
        float sinPitch = Mth.sin(f);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public static boolean isLookingAt(Entity target, double maxRange, float yaw, float pitch) {
        return isLookingAt(target, maxRange, yaw, pitch, true);
    }

    public static boolean isLookingAt(Entity target, double maxRange, float yaw, float pitch, boolean checkWalls) {
        if (mc.player == null || mc.level == null || target == null) return false;

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 reachVec = eyes.add(getLookVec(yaw, pitch).scale(maxRange));
        // Используем точный ванильный хитбокс (target.getPickRadius() в 26.3 == 0.0F, без +0.05 раздутия)
        AABB box = target.getBoundingBox().inflate(target.getPickRadius());
        Optional<Vec3> hit = box.clip(eyes, reachVec);
        if (!box.contains(eyes) && hit.isEmpty()) return false;
        Vec3 hitPoint = box.contains(eyes) ? eyes : hit.get();
        if (eyes.distanceToSqr(hitPoint) > maxRange * maxRange) return false;
        if (!checkWalls) return true;
        // Стены релевантны только до точки попадания в хитбокс — клипаем
        // короткий луч eyes -> hitPoint, а не весь reach (в 2+ раза дешевле).
        if (eyes.equals(hitPoint)) return true;
        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                eyes, hitPoint,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return blockHit.getType() == HitResult.Type.MISS;
    }


    public static boolean canSeePoint(Vec3 from, Vec3 to) {
        if (mc.level == null) return false;
        HitResult hit = mc.level.clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Проверяет, что луч (по текущему yaw/pitch) проходит рядом с указанной точкой.
     * Используется для целей, по которым мы целимся в предсказанную позицию
     * (например, летящий на элитре игрок): хитбокс сущности в этот момент
     * может находиться совсем в другом месте, поэтому проверяем сам луч.
     */
    public static boolean isLookingAtPoint(Entity target, Vec3 point, double maxRange, float yaw, float pitch, boolean checkWalls) {
        if (mc.player == null || mc.level == null || target == null || point == null) return false;

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 dir = getLookVec(yaw, pitch);
        Vec3 toPoint = point.subtract(eyes);

        double forward = toPoint.dot(dir);
        if (forward < 0.0D) return false;
        if (forward > maxRange + 0.5D) return false;

        double perpendicular = toPoint.subtract(dir.scale(forward)).length();
        if (perpendicular > 0.5D) return false;

        if (!checkWalls) return true;

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                eyes, point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return blockHit.getType() == HitResult.Type.MISS
                || eyes.distanceToSqr(point) < eyes.distanceToSqr(blockHit.getLocation());
    }

    public static double getClosestDistanceSqr(Vec3 eyePos, AABB box) {
        double closestX = Mth.clamp(eyePos.x, box.minX, box.maxX);
        double closestY = Mth.clamp(eyePos.y, box.minY, box.maxY);
        double closestZ = Mth.clamp(eyePos.z, box.minZ, box.maxZ);
        double dx = eyePos.x - closestX;
        double dy = eyePos.y - closestY;
        double dz = eyePos.z - closestZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
