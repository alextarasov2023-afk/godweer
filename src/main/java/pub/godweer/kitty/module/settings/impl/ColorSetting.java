package pub.godweer.kitty.module.settings.impl;

import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.SettingRenderer;
import pub.godweer.kitty.module.settings.render.ColorRenderer;

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