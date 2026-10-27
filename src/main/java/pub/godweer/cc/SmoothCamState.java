package pub.godweer.cc;

import net.minecraft.util.Mth;

/**
 * Управление SmoothCamera.
 * Mixin-класс нельзя грузить напрямую (IllegalClassLoadError),
 * поэтому состояние и настройки живут здесь, в обычном классе.
 *
 * Управление из вашего клиента:
 *   SmoothCamState.setEnabled(true/false);
 *   SmoothCamState.setSmooth(0.01f .. 0.3f);
 */
public final class SmoothCamState {

    // ======================== НАСТРОЙКИ МОДУЛЯ ========================

    private static volatile boolean enabled = false;
    private static volatile float smooth = 0.1f; // «Smooth»: 0.01 .. 0.3

    private static final float SMOOTH_MIN = 0.01f;
    private static final float SMOOTH_MAX = 0.30f;

    public static float getSmoothMin() { return SMOOTH_MIN; }
    public static float getSmoothMax() { return SMOOTH_MAX; }

    private SmoothCamState() {}

    public static void setEnabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            // Безопасный старт/выключение подхватывает сам миксин:
            // пока enabled=false он сбрасывает состояние, при повторном
            // включении первый кадр делается snap'ом к текущему положению.
        }
    }

    public static boolean isEnabled() { return enabled; }

    public static void toggle() { setEnabled(!enabled); }

    public static void setSmooth(float value) {
        smooth = Mth.clamp(value, SMOOTH_MIN, SMOOTH_MAX);
    }

    public static float getSmooth() { return smooth; }
}
