package pub.godweer.kitty.module.impl.render;

import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Volumetric rain with simulated drops.
 *
 * <p>Drops live in absolute world coordinates: the spawn volume follows the
 * camera, but a falling drop never moves with it and never stays behind when
 * the player leaves. Impacts spawn ground rings, rebound sparks and drifting
 * mist; an optional storm timer flashes the whole scene.
 */
public final class Rain extends Module {

    private final HeaderSetting presetHeader = header("Осадки");

    public final ModeSetting preset = mode("Пресет", "Дождь", "Морось", "Дождь", "Ливень", "Шторм");

    private final HeaderSetting dropsHeader = header("Капли");

    public final SliderSetting density = slider("Плотность", 1.0F, 0.1F, 3.0F, 0.05F);
    public final SliderSetting radius = slider("Радиус", 26.0F, 8.0F, 64.0F, 1.0F);
    public final SliderSetting altitude = slider("Высота", 16.0F, 6.0F, 40.0F, 1.0F);
    public final SliderSetting dropSize = slider("Размер капель", 1.0F, 0.3F, 2.5F, 0.05F);
    public final SliderSetting fallSpeed = slider("Скорость падения", 1.0F, 0.3F, 2.5F, 0.05F);
    public final SliderSetting wind = slider("Ветер", 1.0F, 0.0F, 3.0F, 0.05F);
    public final SliderSetting opacity = slider("Прозрачность", 0.8F, 0.1F, 1.5F, 0.05F);
    public final ModeSetting colorMode = mode("Цвет", "Реалистичный", "Реалистичный", "Тема", "Свой");
    public final ColorSetting customColor = color("Цвет капель", 0xFFBFD8E6)
            .visible(() -> colorMode.is("Свой"));

    private final HeaderSetting fxHeader = header("Эффекты");

    public final CheckBox splashes = checkbox("Брызги и кольца", true);
    public final CheckBox droplets = checkbox("Осколки брызг", true)
            .visible(splashes::getValue);
    public final CheckBox mist = checkbox("Дымка", true);
    public final CheckBox storm = checkbox("Гроза", false);
    public final CheckBox skyCheck = checkbox("Не под крышей", true);
    public final CheckBox onlyWhenRaining = checkbox("Только в дождь", false);

    public Rain() {
        super("Rain", "Объёмный дождь с физикой капель", Category.RENDER);
    }
}
