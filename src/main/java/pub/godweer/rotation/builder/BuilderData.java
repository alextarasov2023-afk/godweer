package pub.godweer.rotation.builder;

import com.google.gson.annotations.SerializedName;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BuilderData {

    public record AimSample(
            @SerializedName("time") long timestamp,
            @SerializedName("dist") double distance,
            @SerializedName("errorYaw") float errorYaw,
            @SerializedName("errorPitch") float errorPitch,
            @SerializedName("velYaw") float velYaw,
            @SerializedName("velPitch") float velPitch,
            @SerializedName("accelYaw") float accelYaw,
            @SerializedName("accelPitch") float accelPitch,
            @SerializedName("boxX") double boxRelX,
            @SerializedName("boxY") double boxRelY,
            @SerializedName("boxZ") double boxRelZ,
            @SerializedName("isAttack") boolean isAttack,
            @SerializedName("isCrit") boolean isCrit,
            @SerializedName("inAir") boolean inAir,
            @SerializedName("targetMoving") boolean targetMoving
    ) {}

    public static Vec3 getHitboxRelativePoint(Vec3 eyePos, Vec3 lookVec, LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 end = eyePos.add(lookVec.scale(6.0));
        var clip = box.clip(eyePos, end);
        Vec3 point = clip.orElse(getClosestPointOnAABB(eyePos, box));

        double relX = (box.maxX - box.minX > 0.001) ? (point.x - box.minX) / (box.maxX - box.minX) : 0.5;
        double relY = (box.maxY - box.minY > 0.001) ? (point.y - box.minY) / (box.maxY - box.minY) : 0.5;
        double relZ = (box.maxZ - box.minZ > 0.001) ? (point.z - box.minZ) / (box.maxZ - box.minZ) : 0.5;

        return new Vec3(
                Math.clamp(relX, 0.0, 1.0),
                Math.clamp(relY, 0.0, 1.0),
                Math.clamp(relZ, 0.0, 1.0)
        );
    }

    private static Vec3 getClosestPointOnAABB(Vec3 from, AABB box) {
        return new Vec3(
                Math.clamp(from.x, box.minX, box.maxX),
                Math.clamp(from.y, box.minY, box.maxY),
                Math.clamp(from.z, box.minZ, box.maxZ)
        );
    }
}