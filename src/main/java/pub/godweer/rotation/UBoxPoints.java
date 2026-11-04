package pub.godweer.rotation;

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

    // Выбранная точка хранится СВОИМ ИНДЕКСОМ в списке calculatePoints(), а не абсолютными
    // координатами: каждый тик она заново привязывается к текущему хитбоксу. Абсолютная
    // точка липнет к месту, где цель была в прошлом тике, и на любой стрейфе прицел
    // остаётся смотреть в пустоту рядом с игроком.
    private static Entity lastPointTarget;
    private static int lastPointIndex = -1;
    // Липкость нужна против прыжков между двумя одинаковыми точками, а не против самого
    // выбора: раз в этот срок всё равно пересобираем, чтобы точка успевала за телом.
    private static final int RESCAN_TICKS = 8;
    private static int rescanCounter = 0;

    // Точка дальше радиуса удара ауре почти бесполезна и держит прицел на дальнем краю
    // хитбокса. Штраф в «градусах» делает её выбором только когда ближе не видно ничего.
    private static final double OUT_OF_REACH_PENALTY = 60.0D;

    /**
     * @param reachLimit радиус удара: точки дальше него получают штраф по ключу
     *                   (0 или меньше — дистанцию не учитывать).
     */
    public static TargetPoint getBestPoint(Entity target, double reachLimit) {
        if (mc.player == null || target == null) return null;

        Vec3 eyes = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        List<Vec3> points = calculatePoints(box);

        if (target == lastPointTarget && lastPointIndex >= 0 && lastPointIndex < points.size()
                && rescanCounter-- > 0) {
            Vec3 sticky = points.get(lastPointIndex);
            if (RayTraceUtils.canSeePoint(eyes, sticky)) return new TargetPoint(sticky, true);
        }
        rescanCounter = RESCAN_TICKS;
        lastPointTarget = null;
        lastPointIndex = -1;

        float yaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();
        float pitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : mc.player.getXRot();

        int best = -1;
        double bestKey = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            if (!RayTraceUtils.canSeePoint(eyes, point)) continue;
            double key = getAngleDifference(eyes, point, yaw, pitch);
            if (reachLimit > 0.0D && eyes.distanceTo(point) > reachLimit) key += OUT_OF_REACH_PENALTY;
            if (key < bestKey) {
                bestKey = key;
                best = i;
            }
        }

        if (best >= 0) {
            lastPointTarget = target;
            lastPointIndex = best;
            return new TargetPoint(points.get(best), true);
        }
        return new TargetPoint(getClosestBoxPoint(eyes, box), false);
    }

    /**
     * Есть ли на силуэте точка, до которой проходит луч. Ранний выход: по открытой
     * цели это один-два рейкаста, полный обход — только когда цель из-за угла.
     */
    public static boolean hasVisiblePoint(Entity target, Vec3 eyes) {
        if (mc.level == null || target == null) return false;
        for (Vec3 point : calculatePoints(target.getBoundingBox())) {
            if (RayTraceUtils.canSeePoint(eyes, point)) return true;
        }
        return false;
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
