package pub.godweer.event.list;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public class GlidingVelocityEvent extends Event {
    private Vec3 velocity;

    public GlidingVelocityEvent(Vec3 velocity) {
        this.velocity = velocity;
    }

    public GlidingVelocityEvent set(Vec3 velocity) {
        this.velocity = velocity;
        return this;
    }
}