package pub.godweer.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import pub.godweer.IMinecraft;
import pub.godweer.rotation.Angle;

/**
 * Create by daun kvass
 */
@UtilityClass
public class SensUtility implements IMinecraft {
    public float fixFPSrotation(float rot, float oldRot) {
        return correctRotation(MathUtil.lerp(rot, oldRot, (float) (360d / Math.max(Minecraft.getInstance().getFps(), 5))));
    }

    public double getSensitivity(double rot) {
        double d2 = getGCD() * 0.6F + 0.2F;
        double d3 = d2 * d2 * d2;
        double d4 = d3 * 8.0;
        return getDeltaMouse(rot) * getGCDValue();
    }

    public float correctRotation(float rot) {
        float gcd = (float) getGCDValue();
        return Math.round(rot / gcd) * gcd;
    }

    public double getGCD() {

         //   return 0.012975693;
            return mc.options.sensitivity().get();
    }

    public double getGCDValue() {
        double d4 = getGCD() * 0.6D + 0.2D;
        return d4 * d4 * d4 * 8.0D * 0.15;
    }

    public double getDeltaMouse(double delta) {
        return Math.ceil(delta / getGCDValue());
    }

    public Angle applySensitivityPatch(Angle current, Angle previousRotation) {
        double sens = getGCD();
        double d2 = sens * 0.6F + 0.2F;
        double gcd = (d2 * d2 * d2) * 8.0;

        double prevYaw = previousRotation.getYaw();
        double prevPitch = previousRotation.getPitch();

        double currentYaw = current.getYaw();
        double currentPitch = current.getPitch();

        double yaw = (Math.ceil(((currentYaw - prevYaw) / gcd) / 0.15F) * gcd) * 0.15F;
        double pitch = (Math.ceil(((currentPitch - prevPitch) / gcd) / 0.15F) * gcd) * 0.15F;

        return new Angle((float) (prevYaw + yaw), (float) (prevPitch + pitch));
    }
}
