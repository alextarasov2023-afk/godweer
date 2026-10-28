package pub.godweer.module.settings.impl;

import pub.godweer.module.settings.Setting;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.render.HeaderRenderer;

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