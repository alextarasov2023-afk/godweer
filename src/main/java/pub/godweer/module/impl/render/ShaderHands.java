package pub.godweer.module.impl.render;

import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.world.ShaderHandsRenderer;

/**
 * Шейдерная заливка рук от первого лица: маска рук из разницы глубины
 * заливается выбранным эффектом (сияние, туманность, голографическая плёнка
 * и т.д.). Сами эффекты — post-шейдеры в assets/godweer/shaders/post/hands.
 */
public final class ShaderHands extends Module {

    public static final String AURORA = "Aurora";
    public static final String NEBULA = "Nebula";
    public static final String HOLO = "Holo";
    public static final String CAUSTICS = "Caustics";
    public static final String EMBER = "Ember";
    public static final String FROST = "Frost";

    public static ShaderHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting style = mode("Style", AURORA, AURORA, NEBULA, HOLO, CAUSTICS, EMBER, FROST);
    private final SliderSetting speed = slider("Speed", 1.4f, 0.1f, 5.0f, 0.1f);
    private final SliderSetting opacity = slider("Opacity", 0.9f, 0.0f, 1.0f, 0.02f);
    private final CheckBox bothHands = checkbox("Both Hands", true);

    private final ShaderHandsRenderer renderer = new ShaderHandsRenderer();

    public ShaderHands() {
        super("ShaderHands", "Красивая заливка рук: сияние, туманность, голограмма…", Category.RENDER);
        INSTANCE = this;
    }

    public ShaderHandsRenderer getRenderer() {
        return this.renderer;
    }

    public String getStyle() {
        return style.getValue();
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public float getOpacity() {
        return opacity.getValue().floatValue();
    }

    public int getColor() {
        return Theme.getAccentColor();
    }

    public boolean bothHands() {
        return bothHands.getValue();
    }
}
