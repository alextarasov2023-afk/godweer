package pub.godweer.kitty.module.settings.impl;

import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.render.CheckBoxRenderer;
import pub.godweer.kitty.module.settings.SettingRenderer;

/**
 * Create by daun kvass
 */
public class CheckBox extends Setting<Boolean> {
    public CheckBox(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public boolean get() {
        return getValue();
    }

    public void toggle() {
        setValue(!getValue());
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new CheckBoxRenderer(this);
    }
}