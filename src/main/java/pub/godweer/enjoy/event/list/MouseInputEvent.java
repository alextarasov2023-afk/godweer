package pub.godweer.enjoy.event.list;

import lombok.Getter;
import pub.godweer.enjoy.event.Event;

/**
 * Create by daun kvass
 */
@Getter
public final class MouseInputEvent extends Event {
    private long window;
    private int button;
    private int action;
    private int modifiers;

    public MouseInputEvent set(long window, int button, int action, int modifiers) {
        this.setCancelled(false);
        this.window = window;
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
        return this;
    }
}