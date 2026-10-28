package pub.godweer.module.impl.render;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.world.FireHandsRenderer;
import pub.godweer.util.render.world.HandFireSettings;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Fire Hands — анимированное пламя, свечение и огненный шлейф
 * вокруг рук и предметов от первого лица.
 * Сам рендер в FireHandsRenderer, здесь только настройки.
 */
public final class FireHands extends Module implements HandFireSettings {

    public static final String ITEM = "Item";
    public static final String CLIENT = "Client";

    public static FireHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting colorMode = mode("Color", CLIENT, ITEM, CLIENT);
    private final ColorSetting clientA = color("Client A", 0xFF8A2BE2).visible(() -> colorMode.is(CLIENT));
    private final ColorSetting clientB = color("Client B", 0xFF00C8FF).visible(() -> colorMode.is(CLIENT));

    private final HeaderSetting flame = header("Flame");
    private final SliderSetting strength = slider("Strength", 1.0f, 0.1f, 2.0f, 0.05f);
    private final SliderSetting brightness = slider("Brightness", 1.2f, 0.1f, 3.0f, 0.05f);
    private final SliderSetting riseSpeed = slider("Rise", 1.5f, 0.0f, 6.0f, 0.1f);
    private final SliderSetting wobble = slider("Wobble", 0.6f, 0.0f, 3.0f, 0.05f);
    private final SliderSetting turbulence = slider("Turbulence", 0.8f, 0.0f, 3.0f, 0.05f);
    private final SliderSetting trailLength = slider("Trail Length", 0.6f, 0.05f, 3.0f, 0.05f);
    private final SliderSetting auraWidth = slider("Aura Width", 4.5f, 0.5f, 16.0f, 0.5f);
    private final SliderSetting fireSpread = slider("Fire Spread", 1.5f, 0.0f, 6.0f, 0.1f);

    private final HeaderSetting colorDyn = header("Color Dynamics").visible(() -> colorMode.is(CLIENT));
    private final SliderSetting pulseSpeed = slider("Pulse Speed", 2.0f, 0.1f, 20.0f, 0.1f).visible(() -> colorMode.is(CLIENT));

    private final CheckBox onlyWithItems = checkbox("Only Items", true);

    private final FireHandsRenderer renderer = new FireHandsRenderer();

    public FireHands() {
        super("FireHands", "Пламя и огненный шлейф вокруг рук", Category.RENDER);
        INSTANCE = this;
    }

    public FireHandsRenderer getRenderer() {
        return this.renderer;
    }

    @Override
    protected void onEnable() {
        this.renderer.resetTrails(); // безопасный старт: забываем историю шлейфа
    }

    @Override
    protected void onDisable() {
        this.renderer.release();
    }

    /** Индекс режима цвета для шейдера: 0 = Item, 2 = Client. */
    public int getModeIndex() {
        return colorMode.is(ITEM) ? FireHandsRenderer.MODE_ITEM : FireHandsRenderer.MODE_CLIENT;
    }

    public int getCustomColor() {
        return clientA.getValue();
    }

    public int getClientColorA() {
        return clientA.getValue();
    }

    public int getClientColorB() {
        return clientB.getValue();
    }

    public float getPulseSpeed() {
        return pulseSpeed.getValue().floatValue();
    }

    public float getRainbowPeriod() {
        return 4.0f;
    }

    public float getStrength() {
        return strength.getValue().floatValue();
    }

    public float getBrightness() {
        return brightness.getValue().floatValue();
    }

    public float getRise() {
        return riseSpeed.getValue().floatValue();
    }

    public float getWobble() {
        return wobble.getValue().floatValue();
    }

    public float getTurbulence() {
        return turbulence.getValue().floatValue();
    }

    public float getTrailLength() {
        return trailLength.getValue().floatValue();
    }

    public float getAuraWidth() {
        return auraWidth.getValue().floatValue();
    }

    public float getFireSpread() {
        return fireSpread.getValue().floatValue();
    }

    public boolean onlyWithItems() {
        return onlyWithItems.getValue();
    }
}
