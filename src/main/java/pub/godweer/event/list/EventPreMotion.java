package pub.godweer.event.list;

import lombok.Getter;
import lombok.Setter;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public class EventPreMotion extends Event {
    private double posX;
    private double posY;
    private double posZ;
    private float yaw;
    private float pitch;
    private boolean onGround;
    private boolean horizontalCollision;

    public EventPreMotion set(double posX, double posY, double posZ, float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
        this.setCancelled(false);
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        return this;
    }
}