package pub.godweer.enjoy.event.list;

import lombok.Getter;
import lombok.Setter;
import pub.godweer.enjoy.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public class EventSprint extends Event {
    private boolean sprinting;

    public EventSprint(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public EventSprint set(boolean sprinting) {
        this.setCancelled(false);
        this.sprinting = sprinting;
        return this;
    }
}