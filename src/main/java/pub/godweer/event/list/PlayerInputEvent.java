package pub.godweer.event.list;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public final class PlayerInputEvent extends Event {
    private final KeyboardInput keyboardInput;
    private Vec2 moveVector;
    private Input keyPresses;

    public PlayerInputEvent(KeyboardInput keyboardInput, Input keyPresses, Vec2 moveVector) {
        this.keyboardInput = keyboardInput;
        this.keyPresses = keyPresses;
        this.moveVector = moveVector;
    }

    public void setSprinting(boolean sprinting) {
        if (this.keyPresses != null) {
            this.keyPresses = new Input(
                    this.keyPresses.forward(),
                    this.keyPresses.backward(),
                    this.keyPresses.left(),
                    this.keyPresses.right(),
                    this.keyPresses.jump(),
                    this.keyPresses.shift(),
                    sprinting
            );
        }
    }
}