package pub.godweer.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
@AllArgsConstructor
public class LookEvent extends Event {
    private double cursorDeltaX;
    private double cursorDeltaY;
}