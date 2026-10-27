package pub.godweer.kitty.module.settings.impl;

import lombok.Getter;
import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.render.MultiModeRenderer;
import pub.godweer.kitty.module.settings.SettingRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Create by daun kvass
 */
@Getter
public class MultiModeSetting extends Setting<List<String>> {
    private final List<String> modes;

    public MultiModeSetting(String name, List<String> defaultActive, String... modes) {
        super(name, new ArrayList<>(defaultActive));
        this.modes = Arrays.asList(modes);
    }


    public boolean isEnabled(String mode) {
        return getValue().contains(mode);
    }

    public void toggle(String mode) {
        if (getValue().contains(mode)) {
            getValue().remove(mode);
        } else {
            getValue().add(mode);
        }
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new MultiModeRenderer(this);
    }
}