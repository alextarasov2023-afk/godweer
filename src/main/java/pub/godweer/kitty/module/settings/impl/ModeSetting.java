package pub.godweer.kitty.module.settings.impl;

import lombok.Getter;
import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.render.ModeRenderer;
import pub.godweer.kitty.module.settings.SettingRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * Create by daun kvass
 */
@Getter
public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name, defaultMode);
        this.modes = Arrays.asList(modes);
    }

    public void cycle() {
        int index = modes.indexOf(getValue());
        index = (index + 1) % modes.size();
        setValue(modes.get(index));
    }


    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new ModeRenderer(this);
    }
}