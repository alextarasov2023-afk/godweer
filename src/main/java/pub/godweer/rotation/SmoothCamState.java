package pub.godweer.rotation;

import net.minecraft.util.Mth;

/**
 * Управление SmoothCamera.
 * Mixin-класс нельзя грузить напрямую (IllegalClassLoadError),
 * поэтому состояние и настройки живут здесь, в обычном классе.
 *
 * Три независимых регулятора:
 *   smooth     — догон поворота (yaw/pitch);
 *   position   — догон самого тела камеры (позиции);
 *   transition — длительность переезда между 1/2/3 лицом.
 */
public final class SmoothCamState {

    private static volatile boolean enabled = false;

    private static volatile float smooth = 0.10f;      // поворот
    private static volatile float position = 0.12f;    // позиция
    private static volatile float maxLag = 2.5f;       // на сколько блоков камера может отстать
    private static volatile float transition = 0.40f;  // секунды на смену лица

    private static final float SMOOTH_MIN = 0.01f;
    private static final float SMOOTH_MAX = 0.30f;
    private static final float POSITION_MIN = 0.01f;
    private static final float POSITION_MAX = 0.50f;
    private static final float LAG_MIN = 0.0f;
    private static final float LAG_MAX = 6.0f;
    private static final float TRANSITION_MIN = 0.0f;
    private static final float TRANSITION_MAX = 1.20f;

    public static float getSmoothMin() { return SMOOTH_MIN; }
    public static float getSmoothMax() { return SMOOTH_MAX; }
    public static float getPositionMin() { return POSITION_MIN; }
    public static float getPositionMax() { return POSITION_MAX; }
    public static float getLagMin() { return LAG_MIN; }
    public static float getLagMax() { return LAG_MAX; }
    public static float getTransitionMin() { return TRANSITION_MIN; }
    public static float getTransitionMax() { return TRANSITION_MAX; }

    private SmoothCamState() {}

    public static void setEnabled(boolean value) {
        // Сброс состояния подхватывает сам миксин: пока enabled=false он каждый кадр
        // чистит follower, поэтому повторное включение начинается с точного кадра.
        enabled = value;
    }

    public static boolean isEnabled() { return enabled; }

    public static void toggle() { setEnabled(!enabled); }

    public static void setSmooth(float value) {
        smooth = Mth.clamp(value, SMOOTH_MIN, SMOOTH_MAX);
    }

    public static float getSmooth() { return smooth; }

    public static void setPosition(float value) {
        position = Mth.clamp(value, POSITION_MIN, POSITION_MAX);
    }

    public static float getPosition() { return position; }

    public static void setMaxLag(float value) {
        maxLag = Mth.clamp(value, LAG_MIN, LAG_MAX);
    }

    public static float getMaxLag() { return maxLag; }

    public static void setTransition(float value) {
        transition = Mth.clamp(value, TRANSITION_MIN, TRANSITION_MAX);
    }

    public static float getTransition() { return transition; }

    /** FPS-независимый коэффициент догона: значение рассчитано на 60 FPS. */
    public static float follow(float alpha, double dtSeconds) {
        return (float) (1.0 - Math.pow(1.0 - alpha, dtSeconds * 60.0));
    }
}
