package pub.godweer.cc;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */

@Getter
@Setter
public class Angle {

    private float yaw;
    private float pitch;

    public Angle(float yaw, float pitch) {
        this.yaw = Float.isNaN(yaw) ? 0.0f : normalizeYaw(yaw);
        this.pitch = Float.isNaN(pitch) ? 0.0f : normalizePitch(pitch);
    }

    public static Angle fromPlayer() {
        if (mc.player == null) return new Angle(0, 0);
        return new Angle(mc.player.getYRot(), mc.player.getXRot());
    }

    public static Angle fromVec(Vec3 targetVec) {
        if (mc.player == null) return new Angle(0, 0);
        return fromVec(mc.player.getEyePosition(), targetVec);
    }

    public static Angle fromVec(Vec3 from, Vec3 to) {
        double diffX = to.x - from.x;
        double diffY = to.y - from.y;
        double diffZ = to.z - from.z;
        double dist = Math.hypot(diffX, diffZ);

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist)));

        return new Angle(yaw, pitch);
    }

    public static float normalizeYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }

    public static float normalizePitch(float pitch) {
        return Mth.clamp(pitch, -89.9F, 89.9F);
    }

    public Angle applyGCD() {
        if (mc.options == null) return this;

        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        double gcd = sens * sens * sens * 1.2;

        float deltaYaw = this.yaw - (float) (this.yaw % gcd);
        float deltaPitch = this.pitch - (float) (this.pitch % gcd);

        return new Angle(deltaYaw, deltaPitch);
    }

    public Angle copy() {
        return new Angle(this.yaw, this.pitch);
    }
}