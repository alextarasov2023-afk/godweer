package pub.godweer.event.list;

import lombok.*;
import lombok.experimental.FieldDefaults;
import net.minecraft.world.entity.player.Player;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JumpEvent extends Event {
    Player player;
}
