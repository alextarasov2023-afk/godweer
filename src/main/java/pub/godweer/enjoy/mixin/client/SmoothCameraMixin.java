package pub.godweer.enjoy.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.cc.SmoothCamState;

/**
 * SmoothCamera — инерционная камера.
 * Сглаживает поворот (yaw/pitch) и позицию (X/Y/Z) камеры
 * методом «догоняющего значения». Работает только от 3-го лица.
 *
 * Управление из вашего клиента:
 *   SmoothCamState.setEnabled(true/false);
 *   SmoothCamState.setSmooth(0.01f .. 0.3f);
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class SmoothCameraMixin {

    // ======================== СОСТОЯНИЕ ========================

    @Unique private static boolean initialized = false;
    @Unique private static float curYaw, curPitch;
    @Unique private static double curX, curY, curZ;
    @Unique private static long lastFrameNanos = 0L;
    @Unique private static Entity lastEntity = null;

    /** Если камера отстала больше чем на это расстояние (телепорт, респавн) — мгновенный сброс. */
    @Unique private static final double SNAP_DISTANCE_SQR = 64.0 * 64.0;

    @Unique
    private static void resetState() {
        initialized = false;
        lastFrameNanos = 0L;
        lastEntity = null;
    }

    // ======================== SHADOW ========================

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void move(float zoom, float dy, float dx);
    @Shadow private float getMaxZoom(float maxZoom) {
        throw new AssertionError();
    }
    @Shadow private boolean detached;

    // ======================== ЛОГИКА ========================

    // В 26.3 метод setup() разобран в alignWithEntity(float) — инжект в TAIL
    // соответствует старому «TAIL setup» (там же rotation/position/move).
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void smoothcamera$onSetup(float partialTick, CallbackInfo ci) {
        if (!SmoothCamState.isEnabled()) {
            resetState();
            return;
        }

        Entity entity = ((Camera) (Object) this).entity();
        if (entity == null) {
            resetState();
            return;
        }

        // 5. Не работает от 1-го лица
        if (!this.detached) {
            resetState();
            return;
        }

        // Цель: куда игрок реально смотрит и где реально его глаза
        float targetYaw = entity.getViewYRot(partialTick);
        float targetPitch = entity.getViewXRot(partialTick);
        Vec3 targetEye = entity.getEyePosition(partialTick);

        long now = System.nanoTime();

        // 4. Безопасный старт: первый кадр — телепорт сглаженной точки в текущее положение
        boolean needSnap = !initialized
                || entity != lastEntity
                || distSqr(curX, curY, curZ, targetEye) > SNAP_DISTANCE_SQR;

        if (needSnap) {
            curYaw = targetYaw;
            curPitch = targetPitch;
            curX = targetEye.x;
            curY = targetEye.y;
            curZ = targetEye.z;
            lastFrameNanos = now;
            lastEntity = entity;
            initialized = true;
        } else {
            // Коэффициент догона, независимый от FPS (Smooth рассчитан на 60 FPS)
            double dtSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
            lastFrameNanos = now;
            dtSeconds = Mth.clamp(dtSeconds, 0.0, 0.25);
            float alpha = (float) (1.0 - Math.pow(1.0 - SmoothCamState.getSmooth(), dtSeconds * 60.0));

            // А. Сглаживание поворота — кратчайший путь с учётом 360°
            float yawDelta = Mth.wrapDegrees(targetYaw - curYaw);
            curYaw += yawDelta * alpha;
            curYaw = Mth.wrapDegrees(curYaw);

            float pitchDelta = targetPitch - curPitch;
            curPitch += pitchDelta * alpha;
            curPitch = Mth.clamp(curPitch, -90.0f, 90.0f);

            // Б. Сглаживание позиции по X / Y / Z
            curX += (targetEye.x - curX) * alpha;
            curY += (targetEye.y - curY) * alpha;
            curZ += (targetEye.z - curZ) * alpha;
        }

        // Подмена кадра: перезаписываем то, что ванильная камера уже выставила
        boolean mirror = Minecraft.getInstance().options.getCameraType().isMirrored();
        if (mirror) {
            this.setRotation(curYaw + 180.0f, -curPitch);
        } else {
            this.setRotation(curYaw, curPitch);
        }
        this.setPosition(new Vec3(curX, curY, curZ));

        // Вид от 3-го лица: заново отводим камеру назад с проверкой стен
        float distance = 4.0f;
        if (entity instanceof LivingEntity living) {
            distance *= living.getScale();
        }
        this.move(-this.getMaxZoom(distance), 0.0f, 0.0f);
    }

    @Unique
    private static double distSqr(double x, double y, double z, Vec3 v) {
        double dx = v.x - x, dy = v.y - y, dz = v.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
