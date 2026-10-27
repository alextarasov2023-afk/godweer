package pub.godweer.auxiliary.player;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.RotationHandler;
import pub.godweer.enjoy.event.list.PlayerInputEvent;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */

public final class MoveUtility {

    /**
     * Free (Silent Fix): игрок свободно двигается относительно своей камеры (freeYaw),
     * при этом клавиши и вектор движения пересчитываются под серверный угол ротации.
     */
    public static void fixMovement(PlayerInputEvent event, float targetYaw) {
        if (mc.player == null) return;

        Vec2 moveVec = event.getMoveVector();
        float forward = moveVec.y;
        float strafe = moveVec.x;

        if (forward == 0.0F && strafe == 0.0F) return;

        double angle = Mth.wrapDegrees((float) Math.toDegrees(direction(targetYaw, forward, strafe)));
        float serverYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();

        float closestForward = 0.0F, closestStrafe = 0.0F;
        double closestDiff = Double.MAX_VALUE;

        for (float predF = -1.0F; predF <= 1.0F; predF += 1.0F) {
            for (float predS = -1.0F; predS <= 1.0F; predS += 1.0F) {
                if (predS == 0.0F && predF == 0.0F) continue;
                double predAngle = Mth.wrapDegrees((float) Math.toDegrees(direction(serverYaw, predF, predS)));
                double diff = Math.abs(Mth.wrapDegrees((float) (angle - predAngle)));
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestForward = predF;
                    closestStrafe = predS;
                }
            }
        }

        event.setMoveVector(new Vec2(closestStrafe, closestForward));
        Input in = event.getKeyPresses();
        if (in != null) {
            event.setKeyPresses(new Input(
                    closestForward > 0.0F, closestForward < 0.0F,
                    closestStrafe > 0.0F, closestStrafe < 0.0F,
                    in.jump(), in.shift(), in.sprint()
            ));
        }
    }

    /**
     * Focus (Silent Fix с вжиманием в таргет):
     * Игрок с silent fix всё время пытается вжиматься прямо в цель.
     */
    public static void fixMovementFocus(PlayerInputEvent event, Entity target) {
        if (mc.player == null || target == null) return;

        Vec3 playerPos = mc.player.position();
        Vec3 targetPos = target.position();
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        float yawToTarget = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;

        pressIntoTarget(event, yawToTarget);
    }

    public static void fixMovementFocus(PlayerInputEvent event, float targetYaw) {
        pressIntoTarget(event, targetYaw);
    }

    /**
     * Focus-вжимание с Silent Fix: вычисляет оптимальную комбинацию клавиш (W/A/S/D)
     * относительно серверного угла ротации, чтобы вектор движения всегда вжимал игрока в таргет.
     */
    public static void pressIntoTarget(PlayerInputEvent event, float yawToTarget) {
        if (mc.player == null) return;
        float serverYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();
        double desiredAngle = Mth.wrapDegrees((float) Math.toDegrees(direction(yawToTarget, 1.0D, 0.0D)));

        float closestForward = 1.0F, closestStrafe = 0.0F;
        double closestDiff = Double.MAX_VALUE;

        for (float predF = -1.0F; predF <= 1.0F; predF += 1.0F) {
            for (float predS = -1.0F; predS <= 1.0F; predS += 1.0F) {
                if (predS == 0.0F && predF == 0.0F) continue;
                double predAngle = Mth.wrapDegrees((float) Math.toDegrees(direction(serverYaw, predF, predS)));
                double diff = Math.abs(Mth.wrapDegrees((float) (desiredAngle - predAngle)));
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestForward = predF;
                    closestStrafe = predS;
                }
            }
        }

        Input in = event.getKeyPresses();
        boolean jump = in != null && in.jump();
        boolean shift = in != null && in.shift();
        boolean sprint = (in != null && in.sprint()) || closestForward > 0.0F;

        event.setMoveVector(new Vec2(closestStrafe, closestForward));
        event.setKeyPresses(new Input(
                closestForward > 0.0F, closestForward < 0.0F,
                closestStrafe > 0.0F, closestStrafe < 0.0F,
                jump, shift, sprint
        ));
    }

    private static double direction(float yaw, double forward, double strafe) {
        if (forward < 0.0D) yaw += 180.0F;
        float f = 1.0F;
        if (forward < 0.0D) f = -0.5F;
        else if (forward > 0.0D) f = 0.5F;

        if (strafe > 0.0D) yaw -= 90.0F * f;
        if (strafe < 0.0D) yaw += 90.0F * f;
        return Math.toRadians(yaw);
    }
}
