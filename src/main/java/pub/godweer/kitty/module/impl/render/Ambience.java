package pub.godweer.kitty.module.impl.render;

import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;

/**
 * Create by daun kvass
 */
public class Ambience extends Module {
    public static final String SKY_STARRY_SKY = "Starry Sky";
    public static final String SKY_NEBULA = "Nebula";
    public static final String SKY_PLASMA = "Plasma";
    public static final String SKY_CAUSTIC = "Caustic";
    public static Ambience INSTANCE;

    private final HeaderSetting fogHeader = header("Custom Fog");
    private final CheckBox customFog = checkbox("Fog", true);
    private final SliderSetting fogStart = slider("Distance start", 5.0f, 0.0f, 100.0f, 1.0f).visible(customFog::getValue);
    private final SliderSetting fogEnd = slider("Distance end", 40.0f, 5.0f, 256.0f, 1.0f).visible(customFog::getValue);
    private final CheckBox themeColor = checkbox("Color Fog from Theme", false).visible(customFog::getValue);
    private final ColorSetting fogColor = color("Color Fog", 0xFF8A55FF).visible(() -> customFog.getValue() && !themeColor.getValue());

    private final HeaderSetting volFogHeader = header("Volume Fog");
    public final CheckBox useVolumetricFog = checkbox("Volume Fog", false);
    public final CheckBox volFogTheme = checkbox("Color Volume from Theme", true).visible(useVolumetricFog::getValue);
    public final ColorSetting volFogColor = color("Color Fog", ColorUtil.rgba(255, 100, 180, 255)).visible(() -> useVolumetricFog.getValue() && !volFogTheme.getValue());
    public final SliderSetting volFogDensity = slider("Density", 0.8f, 0.1f, 1.0f, 0.05f).visible(useVolumetricFog::getValue);
    public final SliderSetting volFogThickness = slider("Thickness", 0.6f, 0.0f, 1.0f, 0.05f).visible(useVolumetricFog::getValue);
    public final SliderSetting volFogHeight = slider("Height", 15.0f, 2.0f, 60.0f, 1.0f).visible(useVolumetricFog::getValue);

    private final HeaderSetting lightningHeader = header("Lightning");
    public final CheckBox lightning = checkbox("Lightning", true);
    public final CheckBox lightningTheme = checkbox("Color Lightning from Theme", false).visible(lightning::getValue);
    public final ColorSetting lightningColor = color("Color", ColorUtil.rgba(210, 235, 255, 255)).visible(() -> lightning.getValue() && !lightningTheme.getValue());
    public final SliderSetting lightningFrequency = slider("Frequency", 7.0f, 1.5f, 30.0f, 0.5f).visible(lightning::getValue);
    public final SliderSetting lightningIntensity = slider("Intensity Lightning", 1.8f, 0.2f, 4.0f, 0.1f).visible(lightning::getValue);

    private final HeaderSetting shadrsk = header("Shader Sky");
    public final CheckBox skys = checkbox("Shader Sky", false);
    public final ModeSetting custsky = mode("Sky", SKY_NEBULA, SKY_STARRY_SKY, SKY_NEBULA, SKY_PLASMA, SKY_CAUSTIC).visible(skys::getValue);
    public final CheckBox thusm = checkbox("Color Sky from Theme",true).visible(skys::getValue);
    public final ColorSetting skyColor1 = color("Color1", ColorUtil.rgb(30, 140, 255)).visible(()->skys.getValue()&&!thusm.getValue());
    public final ColorSetting skyColor2 = color("Color2", ColorUtil.rgb(0, 255, 230)).visible(()->skys.getValue()&&!thusm.getValue());
    public final SliderSetting skySpeed = slider("Speed", 4, 0.1f, 8, 0.1f).visible(skys::getValue);
    public final SliderSetting skyIntensity = slider("Intensity Sky", 3, 0.1f, 5, 0.1f).visible(skys::getValue);

    private final HeaderSetting saturatin = header("Saturation");
    public final CheckBox sta = checkbox("Saturation", false);
    public final SliderSetting sts = slider("Intensity satur", 1, -1, 2, 0.05f).visible(sta::getValue);

    private final HeaderSetting pidl = header("Puddles");
    public final CheckBox puddles = checkbox("Puddles", true);
    public final SliderSetting puddleCoverage = slider("Puddle Coverage", 0.65F, 0.1F, 1.0F, 0.05F).visible(puddles::getValue);
    public final SliderSetting puddleWaveSpeed = slider("Wave Speed", 1.2F, 0.1F, 3.0F, 0.1F).visible(puddles::getValue);
    public final SliderSetting puddleWaveStrength = slider("Wave Ripples", 0.6F, 0.0F, 2.0F, 0.05F).visible(puddles::getValue);
    public final SliderSetting puddleReflect = slider("Reflectivity", 0.9F, 0.1F, 1.0F, 0.05F).visible(puddles::getValue);
    public final SliderSetting puddleScale = slider("Puddle Scale", 0.25F, 0.05F, 0.8F, 0.05F).visible(puddles::getValue);
    public final CheckBox rainRipples = checkbox("Круги от дождя", true).visible(puddles::getValue);
    private final HeaderSetting timeHeader = header("Time Change");
    private final CheckBox customTime = checkbox("Time Change", true);
    private final ModeSetting timeMode = mode("Time", "Day", "Dawn", "Day", "Evening", "Night", "Custom").visible(customTime::getValue);
    private final SliderSetting timeSlider = slider("Custom Time", 6000.0f, 0.0f, 24000.0f, 500.0f)
            .visible(() -> customTime.getValue() && timeMode.getValue().equalsIgnoreCase("Custom"));

    public Ambience() {
        super("Ambience", "Так же Прекрассно как и песни animegirl88/sekairotten/отравленный", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isCustomFogEnabled() {
        return isState() && customFog.getValue();
    }

    public float getFogStart() {
        return fogStart.getValue();
    }

    public float getFogEnd() {
        return fogEnd.getValue();
    }

    public int getFogColorRGB() {
        if (themeColor.getValue()) {
            return Theme.getAccentColor();
        }
        return fogColor.getValue();
    }

    public boolean usesVolumetricFog() {
        return isState() && this.useVolumetricFog.getValue();
    }

    public int getVolumetricFogColor() {
        if (this.volFogTheme.getValue()) {
            return Theme.getAccentColor();
        }
        return this.volFogColor.getValue();
    }

    public boolean usesLightning() {
        return isState() && this.lightning.getValue();
    }

    public int getLightningColorRGB() {
        if (this.lightningTheme.getValue()) {
            return Theme.getAccentColor();
        }
        return this.lightningColor.getValue();
    }


    public boolean usesSky() {
        return isState() && this.skys.getValue() ;
    }

    public boolean usesSaturation() {
        return isState() && this.sta.getValue() && Math.abs(this.sts.getValue()) > 1.0E-4D;
    }

    public boolean usesPuddles() {
        return isState() && this.puddles.getValue();
    }

    public boolean usesRainRipples() {
        return this.rainRipples.getValue();
    }

    public boolean isCustomTimeEnabled() {
        return isState() && customTime.getValue();
    }

    public long getCustomDayTime() {
        return switch (timeMode.getValue().toLowerCase()) {
            case "dawn" -> 23000L;
            case "day" -> 6000L;
            case "evening" -> 13000L;
            case "night" -> 18000L;
            case "custom" -> timeSlider.getValue().longValue();
            default -> 6000L;
        };
    }
}