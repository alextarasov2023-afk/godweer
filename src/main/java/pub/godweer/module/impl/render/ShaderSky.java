package pub.godweer.module.impl.render;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Animated shader sky: fills the sky with a moving aurora-veil shader.
 */
public class ShaderSky extends Module {
    public static ShaderSky INSTANCE;

    public static final String STYLE_AURORA = "Aurora Veil";
    public static final String STYLE_CRIMSON = "Crimson Nebula";
    public static final String STYLE_OCEAN = "Ocean Abyss";
    public static final String STYLE_SUNSET = "Sunset Bloom";
    public static final String STYLE_STORM = "Storm Veil";
    public static final String STYLE_EMERALD = "Emerald Flow";
    public static final String STYLE_SOLAR = "Solar Winds";
    public static final String STYLE_VOID = "Void Pulse";

    private final HeaderSetting skyHeader = header("Shader Sky");
    private final ModeSetting style = mode("Style", STYLE_AURORA,
            STYLE_AURORA, STYLE_CRIMSON, STYLE_OCEAN, STYLE_SUNSET, STYLE_STORM, STYLE_EMERALD,
            STYLE_SOLAR, STYLE_VOID);
    private final SliderSetting speed = slider("Speed", 4.0f, 0.1f, 8.0f, 0.1f);
    private final SliderSetting intensity = slider("Intensity", 3.0f, 0.1f, 5.0f, 0.1f);
    private final SliderSetting aurora = slider("Aurora", 1.0f, 0.0f, 1.5f, 0.05f);
    private final SliderSetting stars = slider("Stars", 1.0f, 0.0f, 1.5f, 0.05f);
    public final CheckBox themeColor = checkbox("Color From Theme", true);
    public final ColorSetting color1 = color("Color1", ColorUtil.rgb(30, 140, 255)).visible(() -> !themeColor.getValue());
    public final ColorSetting color2 = color("Color2", ColorUtil.rgb(0, 255, 205)).visible(() -> !themeColor.getValue());

    public ShaderSky() {
        super("ShaderSky", "Анимированный шейдер неба", Category.RENDER);
        INSTANCE = this;
    }

    public String getStyle() {
        return style.getValue();
    }

    public int getColor1() {
        return themeColor.getValue() ? Theme.getAccentColor() : color1.getValue();
    }

    public int getColor2() {
        if (!themeColor.getValue()) {
            return color2.getValue();
        }
        // Вторичный цвет — сдвиг оттенка акцента: с двумя одинаковыми цветами
        // любой скин-шейдер схлопывается в одно плоское пятно.
        float[] hsv = ColorUtil.toHsv(Theme.getAccentColor());
        return ColorUtil.fromHsv((hsv[0] + 0.14F) % 1.0F, Math.min(1.0F, hsv[1] + 0.12F), Math.max(0.45F, hsv[2]), 255);
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public float getIntensity() {
        return intensity.getValue().floatValue();
    }

    public float getAurora() {
        return aurora.getValue().floatValue();
    }

    public float getStars() {
        return stars.getValue().floatValue();
    }
}
