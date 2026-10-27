package pub.godweer.kitty.module.impl.movement;

import net.minecraft.world.entity.player.Input;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.ModeSetting;

/**
 * Suppresses the forward movement flag so the server never observes it.
 * Backward, sideways, jump, sneak and sprint inputs are left untouched.
 *
 * Create by daun kvass
 */
public class NoForward extends Module {

    public static NoForward INSTANCE;

    private final ModeSetting mode = mode("Mode", "Server", "Server", "Both");

    public NoForward() {
        super("NoForward", "Сервер не видит движение вперёд", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    /** Server side suppression is always applied while the module is enabled. */
    public static boolean serverSuppressed() {
        return isActive();
    }

    /** Local (client side) suppression is applied in "Both" mode. */
    public boolean clientSuppressed() {
        return isActive() && mode.is("Both");
    }

    /**
     * Returns an equivalent input where only the forward flag is cleared.
     * All other flags (backward, left, right, jump, shift, sprint) are preserved.
     */
    public static Input stripForward(Input input) {
        if (input == null || !input.forward()) return input;
        boolean sprint = input.sprint();
        return new Input(
                false,
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                input.shift(),
                sprint
        );
    }

    /** forward=false и sprint=false, остальные флаги без изменений (тихий сброс спринта ауры). */
    public static Input stripForwardAndSprint(Input input) {
        if (input == null) return null;
        return new Input(
                false,
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                input.shift(),
                false
        );
    }
}
