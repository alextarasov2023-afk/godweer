package pub.godweer.kitty.module.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public abstract class Setting<T> {
    private final String name;
    private T value;
    private Supplier<Boolean> visible = () -> true;

    public Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visible(Supplier<Boolean> condition) {
        this.visible = condition != null ? condition : () -> true;
        return (S) this;
    }

    public boolean isVisible() {
        try {
            return visible == null || visible.get();
        } catch (Exception e) {
            return true;
        }
    }

    public abstract SettingRenderer<?> createRenderer();
}