package ez.falseclient.system.features.render;

import ez.falseclient.system.api.Category;
import ez.falseclient.system.api.Function;
import ez.falseclient.system.api.NewFunction;
import ez.falseclient.system.settings.impl.BooleanSetting;
import ez.falseclient.system.settings.impl.ColorSetting;
import ez.falseclient.system.settings.impl.ModeSetting;
import ez.falseclient.system.settings.impl.NumberSetting;
import ez.falseclient.utils.render.pipeline.FireHandsPipeline;

import java.awt.Color;

/**
 * FireHands — анимированное пламя, свечение и огненный шлейф
 * вокруг рук и предметов от первого лица.
 * Сам рендер в FireHandsPipeline, здесь только настройки.
 */
@NewFunction(name = "FireHands", desc = "Пламя и огненный шлейф вокруг рук", category = Category.RENDER)
public class FireHands extends Function {
    public static FireHands Instance;

    private static final String ITEM = "Item";
    private static final String CLIENT = "Client";

    private final ModeSetting colorMode = new ModeSetting("Цвет", CLIENT, ITEM, CLIENT);
    private final ColorSetting clientA = new ColorSetting("Цвет A", new Color(138, 43, 226));
    private final ColorSetting clientB = new ColorSetting("Цвет B", new Color(0, 200, 255));
    private final NumberSetting strength = new NumberSetting("Сила", 1.0, 0.1, 2.0, 0.05);
    private final NumberSetting brightness = new NumberSetting("Яркость", 1.2, 0.1, 3.0, 0.05);
    private final NumberSetting rise = new NumberSetting("Подъём", 1.5, 0.0, 6.0, 0.1);
    private final NumberSetting wobble = new NumberSetting("Колебания", 0.6, 0.0, 3.0, 0.05);
    private final NumberSetting turbulence = new NumberSetting("Турбулентность", 0.8, 0.0, 3.0, 0.05);
    private final NumberSetting trailLength = new NumberSetting("Длина шлейфа", 0.6, 0.05, 3.0, 0.05);
    private final NumberSetting auraWidth = new NumberSetting("Ширина ауры", 4.5, 0.5, 16.0, 0.5);
    private final NumberSetting fireSpread = new NumberSetting("Распространение", 1.5, 0.0, 6.0, 0.1);
    private final NumberSetting pulseSpeed = new NumberSetting("Скорость пульсации", 2.0, 0.1, 20.0, 0.1);
    private final BooleanSetting onlyWithItems = new BooleanSetting("Только с предметом", true);

    public FireHands() {
        Instance = this;
        addSettings(colorMode, clientA, clientB, strength, brightness, rise, wobble, turbulence,
                trailLength, auraWidth, fireSpread, pulseSpeed, onlyWithItems);
        colorMode.runnable(this::updateVisibility);
        updateVisibility();
    }

    @Override
    public void onEnable() {
        FireHandsPipeline.resetTrails(); // безопасный старт: забываем историю шлейфа
    }

    @Override
    public void onDisable() {
        FireHandsPipeline.resetTrails();
    }

    /** Индекс режима цвета для шейдера: 0 = Item, 1 = Client. */
    public int getModeIndex() {
        return colorMode.isEnabled(ITEM) ? 0 : 1;
    }

    public int getCustomColor() {
        return clientA.getColor().getRGB();
    }

    public int getClientColorA() {
        return clientA.getColor().getRGB();
    }

    public int getClientColorB() {
        return clientB.getColor().getRGB();
    }

    public float getPulseSpeed() {
        return (float) pulseSpeed.getValue();
    }

    public float getRainbowPeriod() {
        return 4.0f;
    }

    public float getStrength() {
        return (float) strength.getValue();
    }

    public float getBrightness() {
        return (float) brightness.getValue();
    }

    public float getRise() {
        return (float) rise.getValue();
    }

    public float getWobble() {
        return (float) wobble.getValue();
    }

    public float getTurbulence() {
        return (float) turbulence.getValue();
    }

    public float getTrailLength() {
        return (float) trailLength.getValue();
    }

    public float getAuraWidth() {
        return (float) auraWidth.getValue();
    }

    public float getFireSpread() {
        return (float) fireSpread.getValue();
    }

    public boolean onlyWithItems() {
        return onlyWithItems.isEnabled();
    }

    private void updateVisibility() {
        boolean client = colorMode.isEnabled(CLIENT);
        clientA.setVisible(client);
        clientB.setVisible(client);
        pulseSpeed.setVisible(client);
    }
}
