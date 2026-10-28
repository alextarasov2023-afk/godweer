package pub.godweer.module;

import lombok.Getter;
import lombok.Setter;
import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.event.Event;
import pub.godweer.event.EventListener;
import pub.godweer.hud.Notifications;
import pub.godweer.module.settings.*;
import pub.godweer.module.settings.impl.*;
import pub.godweer.util.math.Animation;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public abstract class Module implements EventListener<Event>, IMinecraft {
    private final String name;
    private final String description;
    private final Category category;
    private final BindSetting bind;
    private boolean state;

    private boolean expanded;
    private boolean binding;

    private final Animation expandAnim = new Animation(0.0F, 0.16F);
    private final Animation bindAnim = new Animation(0.0F, 0.14F);
    @Getter private final Animation posXAnim = new Animation(0.0F, 0.22F);
    @Getter private final Animation posYAnim = new Animation(0.0F, 0.22F);
    private boolean posInitialized = false;

    public void updatePosition(float targetX, float targetY) {
        if (!posInitialized) {
            posXAnim.setValue(targetX);
            posYAnim.setValue(targetY);
            posInitialized = true;
        }
        posXAnim.setTarget(targetX);
        posYAnim.setTarget(targetY);
        posXAnim.update();
        posYAnim.update();
    }

    public void resetPositionInit() {
        this.posInitialized = false;
    }
    private long shakeStartTime = -1L;

    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<SettingRenderer<?>> settingRenderers = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this(name, description, category, BindSetting.UNBOUND);
    }

    public Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.bind = new BindSetting("Keybind", defaultKey);
    }

    public boolean isEnabled() {
        return this.state;
    }

    public void setEnabled(boolean enabled) {
        setState(enabled);
    }

    public void toggle() {
        setState(!this.state);
    }

    public void setState(boolean state) {
        if (this.state != state) {
            this.state = state;
            if (state) {
                Client.getInstance().getEventManager().register(this);
                onEnable();
            } else {
                Client.getInstance().getEventManager().unregister(this);
                onDisable();
            }
            Notifications.moduleToggle(this.name, state);
        }
    }

    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }

    public void triggerShake() {
        if (System.currentTimeMillis() - shakeStartTime > 400L) {
            this.shakeStartTime = System.currentTimeMillis();
        }
    }

    public float getShakeOffset() {
        if (shakeStartTime <= 0L) return 0.0F;
        long elapsed = System.currentTimeMillis() - shakeStartTime;
        if (elapsed > 350L) {
            shakeStartTime = -1L;
            return 0.0F;
        }
        float progress = 1.0F - (elapsed / 350.0F);
        return (float) (Math.sin(elapsed * 0.06D) * 3.0F * progress);
    }

    protected CheckBox checkbox(String name, boolean defaultValue) {
        return register(new CheckBox(name, defaultValue));
    }

    protected SliderSetting slider(String name, float defaultValue, float min, float max, float step) {
        return register(new SliderSetting(name, defaultValue, min, max, step));
    }

    protected ModeSetting mode(String name, String defaultMode, String... modes) {
        return register(new ModeSetting(name, defaultMode, modes));
    }

    protected MultiModeSetting multiMode(String name, List<String> defaultActive, String... modes) {
        return register(new MultiModeSetting(name, defaultActive, modes));
    }

    protected MultiModeSetting multiMode(String name, String... modes) {
        return register(new MultiModeSetting(name, new ArrayList<>(), modes));
    }

    protected BindSetting bind(String name, int defaultKey) {
        return register(new BindSetting(name, defaultKey));
    }
    protected ButtonSetting button(String name, Runnable action) {
        return register(new ButtonSetting(name, action));
    }


    protected HeaderSetting header(String name){
        return register(new HeaderSetting(name));
    }

    protected ColorSetting color(String name, int defaultColor) {
        return register(new ColorSetting(name, defaultColor));
    }

    public <T extends Setting<?>> T register(T setting) {
        this.settings.add(setting);
        this.settingRenderers.add(setting.createRenderer());
        return setting;
    }

    public void addSettings(Setting<?>... customSettings) {
        for (Setting<?> s : customSettings) register(s);
    }

    protected void onEnable() {}
    protected void onDisable() {}
    @Override
    public void onEvent(Event event) {}
}