package pub.godweer.auxiliary.render.world.module;

/**
 * Настройки glow-обводки рук, независимые от конкретного модуля.
 * Реализуется GlowHands и OutlineHands — GlowHandsRenderer читает их одинаково.
 */
public interface HandGlowSettings {
    float getRadius();
    float getGap();
    float getSharpness();
    float getIntensity();
    float getPulseSpeed();
    int getColor();
    boolean bothHands();
}
