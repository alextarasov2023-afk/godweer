package pub.godweer.kitty.module.settings.impl;

import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.SettingRenderer;
import pub.godweer.kitty.module.settings.render.HeaderRenderer;

/**
 * Create by daun kvass
 */
public class HeaderSetting extends Setting<String> {

    public HeaderSetting(String name) {
        super(name, name);
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new HeaderRenderer(this);
    }
}