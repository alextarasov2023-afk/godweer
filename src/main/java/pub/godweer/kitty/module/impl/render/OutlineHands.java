package pub.godweer.kitty.module.impl.render;

import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.world.module.FireHandsRenderer;
import pub.godweer.auxiliary.render.world.module.HandFireSettings;
import pub.godweer.auxiliary.render.world.module.HandGlowSettings;
import pub.godweer.auxiliary.render.world.module.OutlineHandsRenderer;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Outline Hands — единый модуль-комбайн для эффектов рук:
 * стили Glow (переносит GlowHands), Fire (переносит FireHands)
 * и обычная плоская обводка (Outline).
 * Рендер живёт в OutlineHandsRenderer, здесь — только настройки.
 */
public final class OutlineHands extends Module implements HandGlowSettings, HandFireSettings {

    public static final String STYLE_OUTLINE = "Outline";
    public static final String STYLE_GLOW = "Glow";
    public static final String STYLE_FIRE = "Fire";

    // ===== FireHands color mode constants (same indices as FireHands) =====
    public static final String ITEM = "Item";
    public static final String CLIENT = "Client";

    public static OutlineHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting style = mode("Стиль", STYLE_OUTLINE, STYLE_OUTLINE, STYLE_GLOW, STYLE_FIRE);
    private final CheckBox bothHands = checkbox("Both Hands", true);

    // ----- Outline (plain ring) -----
    private final HeaderSetting outlineHdr = header("Outline").visible(this::usesOutline);
    private final ColorSetting outlineColor = color("Outline: Color", ColorUtil.rgba(180, 120, 255, 255)).visible(this::usesOutline);
    private final SliderSetting outlineWidth = slider("Outline: Ширина", 3.0f, 1.0f, 10.0f, 0.5f).visible(this::usesOutline);
    private final SliderSetting outlineIntensity = slider("Outline: Яркость", 1.2f, 0.1f, 4.0f, 0.1f).visible(this::usesOutline);

    // ----- Glow (same defaults as GlowHands) -----
    private final HeaderSetting glowHdr = header("Glow").visible(this::usesGlow);
    private final SliderSetting glowRadius = slider("Glow: Radius", 10.0f, 2.0f, 40.0f, 1.0f).visible(this::usesGlow);
    private final SliderSetting glowGap = slider("Glow: Gap", 3.0f, 0.0f, 8.0f, 0.5f).visible(this::usesGlow);
    private final SliderSetting glowSharpness = slider("Glow: Sharpness", 0.6f, 0.0f, 1.0f, 0.05f).visible(this::usesGlow);
    private final SliderSetting glowIntensity = slider("Glow: Intensity", 1.4f, 0.1f, 4.0f, 0.1f).visible(this::usesGlow);
    private final SliderSetting glowPulse = slider("Glow: Pulse Speed", 1.5f, 0.0f, 8.0f, 0.1f).visible(this::usesGlow);
    private final ColorSetting glowColor = color("Glow: Color", ColorUtil.rgba(80, 160, 255, 255)).visible(this::usesGlow);

    // ----- Fire (same defaults as FireHands) -----
    private final HeaderSetting fireHdr = header("Fire").visible(this::usesFire);
    private final ModeSetting fireColorMode = mode("Fire: Color", CLIENT, ITEM, CLIENT).visible(this::usesFire);
    private final ColorSetting fireClientA = color("Fire: Client A", 0xFF8A2BE2).visible(() -> usesFire() && fireColorMode.is(CLIENT));
    private final ColorSetting fireClientB = color("Fire: Client B", 0xFF00C8FF).visible(() -> usesFire() && fireColorMode.is(CLIENT));
    private final SliderSetting fireStrength = slider("Fire: Strength", 1.0f, 0.1f, 2.0f, 0.05f).visible(this::usesFire);
    private final SliderSetting fireBrightness = slider("Fire: Brightness", 1.2f, 0.1f, 3.0f, 0.05f).visible(this::usesFire);
    private final SliderSetting fireRise = slider("Fire: Rise", 1.5f, 0.0f, 6.0f, 0.1f).visible(this::usesFire);
    private final SliderSetting fireWobble = slider("Fire: Wobble", 0.6f, 0.0f, 3.0f, 0.05f).visible(this::usesFire);
    private final SliderSetting fireTurbulence = slider("Fire: Turbulence", 0.8f, 0.0f, 3.0f, 0.05f).visible(this::usesFire);
    private final SliderSetting fireTrail = slider("Fire: Trail Length", 0.6f, 0.05f, 3.0f, 0.05f).visible(this::usesFire);
    private final SliderSetting fireAuraWidth = slider("Fire: Aura Width", 4.5f, 0.5f, 16.0f, 0.5f).visible(this::usesFire);
    private final SliderSetting fireSpread = slider("Fire: Fire Spread", 1.5f, 0.0f, 6.0f, 0.1f).visible(this::usesFire);
    private final SliderSetting firePulse = slider("Fire: Pulse Speed", 2.0f, 0.1f, 20.0f, 0.1f).visible(() -> usesFire() && fireColorMode.is(CLIENT));
    private final CheckBox fireOnlyItems = checkbox("Fire: Only Items", true).visible(this::usesFire);

    private final OutlineHandsRenderer renderer = new OutlineHandsRenderer();

    public OutlineHands() {
        super("OutlineHands", "Обводка/свечение/огонь рук в одном модуле", Category.RENDER);
        INSTANCE = this;
    }

    public OutlineHandsRenderer getRenderer() {
        return this.renderer;
    }

    @Override
    protected void onEnable() {
        this.renderer.resetTrails(); // безопасный старт огненного шлейфа
    }

    @Override
    protected void onDisable() {
        this.renderer.release();
    }

    public boolean usesOutline() {
        return this.style.is(STYLE_OUTLINE);
    }

    public boolean isGlowStyle() {
        return this.style.is(STYLE_GLOW);
    }

    public boolean isFireStyle() {
        return this.style.is(STYLE_FIRE);
    }

    private boolean usesGlow() {
        return this.style.is(STYLE_GLOW);
    }

    private boolean usesFire() {
        return this.style.is(STYLE_FIRE);
    }

    // ===== HandGlowSettings (для GlowHandsRenderer) =====

    @Override
    public float getRadius() {
        return this.glowRadius.getValue().floatValue();
    }

    @Override
    public float getGap() {
        return this.glowGap.getValue().floatValue();
    }

    @Override
    public float getSharpness() {
        return this.glowSharpness.getValue().floatValue();
    }

    @Override
    public float getIntensity() {
        return this.glowIntensity.getValue().floatValue();
    }

    @Override
    public float getPulseSpeed() {
        return this.glowPulse.getValue().floatValue();
    }

    @Override
    public int getColor() {
        return this.glowColor.getValue();
    }

    @Override
    public boolean bothHands() {
        return this.bothHands.getValue();
    }

    // ===== HandFireSettings (для FireHandsRenderer) =====

    @Override
    public int getModeIndex() {
        return this.fireColorMode.is(ITEM) ? FireHandsRenderer.MODE_ITEM : FireHandsRenderer.MODE_CLIENT;
    }

    @Override
    public int getCustomColor() {
        return this.fireClientA.getValue();
    }

    @Override
    public int getClientColorA() {
        return this.fireClientA.getValue();
    }

    @Override
    public int getClientColorB() {
        return this.fireClientB.getValue();
    }

    @Override
    public float getRainbowPeriod() {
        return 4.0f;
    }

    @Override
    public float getStrength() {
        return this.fireStrength.getValue().floatValue();
    }

    @Override
    public float getBrightness() {
        return this.fireBrightness.getValue().floatValue();
    }

    @Override
    public float getRise() {
        return this.fireRise.getValue().floatValue();
    }

    @Override
    public float getWobble() {
        return this.fireWobble.getValue().floatValue();
    }

    @Override
    public float getTurbulence() {
        return this.fireTurbulence.getValue().floatValue();
    }

    @Override
    public float getTrailLength() {
        return this.fireTrail.getValue().floatValue();
    }

    @Override
    public float getAuraWidth() {
        return this.fireAuraWidth.getValue().floatValue();
    }

    @Override
    public float getFireSpread() {
        return this.fireSpread.getValue().floatValue();
    }

    @Override
    public boolean onlyWithItems() {
        return this.fireOnlyItems.getValue();
    }

    // ===== Outline (plain ring) =====

    public int getOutlineColor() {
        return this.outlineColor.getValue();
    }

    public float getOutlineWidth() {
        return this.outlineWidth.getValue().floatValue();
    }

    public float getOutlineIntensity() {
        return this.outlineIntensity.getValue().floatValue();
    }
}
