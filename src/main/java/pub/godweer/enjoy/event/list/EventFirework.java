package pub.godweer.enjoy.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.LivingEntity;
import pub.godweer.enjoy.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
@AllArgsConstructor
public class EventFirework extends Event {
    private final LivingEntity boostedEntity;
    private float speed;
}