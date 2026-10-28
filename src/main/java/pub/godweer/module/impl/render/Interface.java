package pub.godweer.module.impl.render;

import pub.godweer.event.EventTarget;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.hud.HudElement;
import pub.godweer.hud.HudManager;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.MultiModeSetting;

import java.util.List;

/**
 * Toggles HUD elements.
 */
public final class Interface extends Module {

    public final MultiModeSetting elements = multiMode(
            "Elements",
            List.of("Watermark", "Player", "Potions", "Staff", "Hotkeys", "Music", "Server Items"),
            "Watermark", "Player", "Potions", "Staff", "Hotkeys", "Music", "Server Items"
    );

    public Interface() {
        super("Interface", "Интерфейс", Category.RENDER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        updateHudState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        updateHudState();
    }

    @EventTarget(priority = 1000)
    public void onRender2D(Render2DEvent event) {
        updateHudState();
    }

    private void updateHudState() {
        HudManager manager = HudManager.getInstance();
        boolean moduleActive = this.isEnabled();
        for (HudElement el : manager.getElements()) {
            el.setEnabled(moduleActive && this.elements.isEnabled(el.getName()));
        }
    }
}
