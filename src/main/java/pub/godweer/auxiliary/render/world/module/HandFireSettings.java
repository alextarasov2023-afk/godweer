package pub.godweer.auxiliary.render.world.module;

/**
 * Настройки огненных рук, независимые от конкретного модуля.
 * Реализуется FireHands и OutlineHands — FireHandsRenderer читает их одинаково.
 */
public interface HandFireSettings {
    int getModeIndex();
    int getCustomColor();
    int getClientColorA();
    int getClientColorB();
    float getPulseSpeed();
    float getRainbowPeriod();
    float getStrength();
    float getBrightness();
    float getRise();
    float getWobble();
    float getTurbulence();
    float getTrailLength();
    float getAuraWidth();
    float getFireSpread();
    boolean onlyWithItems();
}
