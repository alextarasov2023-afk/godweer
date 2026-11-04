package pub.godweer.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.rotation.SmoothCamState;
import pub.godweer.module.impl.misc.FreeCam;

/**
 * SmoothCamera — инерция всей камеры.
 *
 * Берём готовый кадр ванили (position/yRot/xRot в TAIL alignWithEntity) и догоняем его
 * собственным состоянием: поворот отдельно, тело камеры отдельно. Поэтому зум, отскок от
 * стен, attribute CAMERA_DISTANCE, транспорт и зеркальный режим остаются ванильными —
 * мы не пересчитываем смещение сами.
 *
 * Смена лица (1-е ↔ 2-е ↔ 3-е) раньше была мгновенной: цель прыгала на 4 блока и на 180°
 * по yaw. Теперь это отдельный переход фиксированной длительности с smoothstep: камера
 * физически переезжает из старой точки в новую, а yaw доворачивается по кратчайшей.
 *
 * Управление из клиента: SmoothCamState.setEnabled/setSmooth/setPosition/setMaxLag/setTransition.
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class SmoothCameraMixin {

    @Unique private static boolean followerReady = false;
    @Unique private static float curYaw, curPitch;
    @Unique private static double curX, curY, curZ;
    @Unique private static long lastFrameNanos = 0L;
    @Unique private static Entity lastEntity = null;
    @Unique private static CameraType lastCameraType = null;

    @Unique private static boolean transitioning = false;
    @Unique private static float transitionProgress = 0.0F;
    @Unique private static double startDX, startDY, startDZ;
    @Unique private static float startDYaw, startDPitch;

    /** Телепорт, смена измерения, респаун: дальше этого расстояния догонять бессмысленно. */
    @Unique private static final double SNAP_DISTANCE_SQR = 64.0 * 64.0;

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow private Vec3 position;
    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow private boolean detached;

    @Unique
    private static void smoothcamera$reset() {
        followerReady = false;
        lastFrameNanos = 0L;
        lastEntity = null;
        lastCameraType = null;
        transitioning = false;
        transitionProgress = 0.0F;
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void smoothcamera$onAlignTail(float partialTick, CallbackInfo ci) {
        if (!SmoothCamState.isEnabled()) {
            smoothcamera$reset();
            return;
        }

        Camera self = (Camera) (Object) this;
        Entity entity = self.entity();
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || mc.level == null) {
            smoothcamera$reset();
            return;
        }

        // FreeCam сам ведёт камеру каждый кадр — наш догон превратил бы
        // его управление в «ватное», поэтому отдаём ему кадр целиком.
        FreeCam freeCam = FreeCam.INSTANCE;
        boolean otherDriver = freeCam != null && freeCam.isEnabled();

        double targetX = this.position.x;
        double targetY = this.position.y;
        double targetZ = this.position.z;
        float targetYaw = this.yRot;
        float targetPitch = this.xRot;

        long now = System.nanoTime();
        double dt = Mth.clamp((now - lastFrameNanos) / 1_000_000_000.0, 0.0, 0.25);
        lastFrameNanos = now;

        CameraType type = mc.options.getCameraType();

        // Первый кадр после включения/смены сущности/телепорта — принимаем кадр ванили как есть.
        if (!followerReady || entity != lastEntity || distSqr(curX, curY, curZ, targetX, targetY, targetZ) > SNAP_DISTANCE_SQR) {
            smoothcamera$adopt(targetX, targetY, targetZ, targetYaw, targetPitch);
            lastEntity = entity;
            lastCameraType = type;
            transitioning = false;
            followerReady = true;
            return;
        }

        if (otherDriver) {
            smoothcamera$adopt(targetX, targetY, targetZ, targetYaw, targetPitch);
            lastCameraType = type;
            transitioning = false;
            return;
        }

        // Смена лица: запоминаем, где мы были относительно новой цели, и прокладываем переезд.
        if (lastCameraType != null && type != lastCameraType && SmoothCamState.getTransition() > 0.001F) {
            startDX = curX - targetX;
            startDY = curY - targetY;
            startDZ = curZ - targetZ;
            startDYaw = Mth.wrapDegrees(curYaw - targetYaw);
            startDPitch = curPitch - targetPitch;
            transitioning = true;
            transitionProgress = 0.0F;
        }
        lastCameraType = type;

        if (transitioning) {
            float duration = SmoothCamState.getTransition();
            // duration может стать 0 прямо во время перехода (ползунок) — тогда просто дозавершаем,
            // иначе dt/0 даёт NaN и камера зависает в невалидной точке.
            float step = duration > 0.001F ? (float) (dt / duration) : 1.0F;
            transitionProgress = Mth.clamp(transitionProgress + step, 0.0F, 1.0F);
            float ease = smoothstep(transitionProgress);
            float left = 1.0F - ease;

            double x = targetX + startDX * left;
            double y = targetY + startDY * left;
            double z = targetZ + startDZ * left;
            float yaw = Mth.wrapDegrees(targetYaw + startDYaw * left);
            float pitch = Mth.clamp(targetPitch + startDPitch * left, -90.0F, 90.0F);

            setPosition(new Vec3(x, y, z));
            setRotation(yaw, pitch);
            smoothcamera$adopt(x, y, z, yaw, pitch);

            if (transitionProgress >= 1.0F) transitioning = false;
            return;
        }

        // Обычный догон: в 1-м лице кадр обязан совпадать с игроком, иначе прицел
        // разъедется с тем, что реально под перекрестием.
        float rotAlpha = this.detached ? SmoothCamState.follow(SmoothCamState.getSmooth(), dt) : 1.0F;
        float posAlpha = this.detached ? SmoothCamState.follow(SmoothCamState.getPosition(), dt) : 1.0F;

        float yaw = Mth.wrapDegrees(curYaw + Mth.wrapDegrees(targetYaw - curYaw) * rotAlpha);
        float pitch = Mth.clamp(curPitch + (targetPitch - curPitch) * rotAlpha, -90.0F, 90.0F);

        double x = curX + (targetX - curX) * posAlpha;
        double y = curY + (targetY - curY) * posAlpha;
        double z = curZ + (targetZ - curZ) * posAlpha;

        // Потолок отставания: без него на резком развороте камера улетает за спину и
        // начинает проходить сквозь стены (пересчёт клиппинга ваниль делает по своему углу).
        double lag = SmoothCamState.getMaxLag();
        if (lag > 0.0F) {
            double dx = x - targetX;
            double dy = y - targetY;
            double dz = z - targetZ;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > lag) {
                double scale = lag / length;
                x = targetX + dx * scale;
                y = targetY + dy * scale;
                z = targetZ + dz * scale;
            }
        }

        setPosition(new Vec3(x, y, z));
        setRotation(yaw, pitch);
        smoothcamera$adopt(x, y, z, yaw, pitch);
    }

    @Unique
    private static void smoothcamera$adopt(double x, double y, double z, float yaw, float pitch) {
        curX = x;
        curY = y;
        curZ = z;
        curYaw = yaw;
        curPitch = pitch;
    }

    /** 3t² − 2t³: старт и финиш без рывка скорости. */
    @Unique
    private static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    @Unique
    private static double distSqr(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        return dx * dx + dy * dy + dz * dz;
    }
}
