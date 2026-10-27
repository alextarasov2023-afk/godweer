package pub.godweer.kitty.module.settings.impl;

import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.SettingRenderer;
import pub.godweer.kitty.module.settings.render.ButtonSettingRenderer;

/**
 * Create by daun kvass
 */
public class ButtonSetting extends Setting<Runnable> {

    public ButtonSetting(String name, Runnable action) {
        super(name, action);
    }

    public void run() {
        if (getValue() != null) {
            getValue().run();
        }
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new ButtonSettingRenderer(this);
    }
}