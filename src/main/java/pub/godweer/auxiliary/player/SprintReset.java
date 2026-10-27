package pub.godweer.auxiliary.player;

import net.minecraft.world.entity.player.Input;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.mixin.accessor.ILocalPlayer;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public class SprintReset {

    private static boolean resetRequested = false;
    private static boolean shouldRecoverSprint = false;

    public static void handleInput(PlayerInputEvent event, boolean shouldReset) {
        if (mc.player == null) return;

        if (shouldReset && !mc.player.isInWater()) {
            Input current = event.getKeyPresses();
            if (current != null) {
                event.setKeyPresses(new Input(
                        false,
                        current.backward(),
                        current.left(),
                        current.right(),
                        current.jump(),
                        current.shift(),
                        false
                ));
                mc.player.setSprinting(false);
                resetRequested = true;
                shouldRecoverSprint = true;
                return;
            }
        }

        if (shouldRecoverSprint) {
            Input current = event.getKeyPresses();
            if (current != null) {
                event.setKeyPresses(new Input(
                        true,
                        current.backward(),
                        current.left(),
                        current.right(),
                        current.jump(),
                        current.shift(),
                        true
                ));
            }
            mc.player.setSprinting(true);
            shouldRecoverSprint = false;
            resetRequested = false;
        }
    }

    public static boolean isAttackBlocked(String mode) {
        if (!mode.equalsIgnoreCase("Legit")) return false;
        if (mc.player == null) return false;

        if (resetRequested || !mc.player.isSprinting()) {
            return false;
        }

        return !mc.player.isInWater();
    }
    public static boolean isServerSprinting() {
        if (mc.player == null) return false;
        try {
            return ((ILocalPlayer) mc.player).serverSprintState();
        } catch (Exception e) {
            return mc.player.isSprinting();
        }
    }
    public static void reset() {
        resetRequested = false;
        shouldRecoverSprint = false;
    }
}