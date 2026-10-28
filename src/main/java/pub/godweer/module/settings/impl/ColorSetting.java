package pub.godweer.module.settings.impl;

import pub.godweer.module.settings.Setting;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.render.ColorRenderer;

/**
 * Create by daun kvass
 */
public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, int defaultColor) {
        super(name, defaultColor);
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new ColorRenderer(this);
    }
}