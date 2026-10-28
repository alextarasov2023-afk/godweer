package pub.godweer.event.list;

import lombok.Getter;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
public final class CharacterInputEvent extends Event {
    private long window;
    private int codePoint;

    public CharacterInputEvent set(long window, int codePoint) {
        this.setCancelled(false);
        this.window = window;
        this.codePoint = codePoint;
        return this;
    }
}