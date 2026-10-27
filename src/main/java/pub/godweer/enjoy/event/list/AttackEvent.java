package pub.godweer.enjoy.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.world.entity.Entity;
import pub.godweer.enjoy.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public final class AttackEvent extends Event {
    private Entity target;
}