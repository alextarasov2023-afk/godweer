package pub.godweer.module.impl.movement;

import net.minecraft.world.effect.MobEffects;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;

/**
 * Create by daun kvass
 */
public class Sprint extends Module {

    public Sprint() {
        super("Sprint", "Само за тебя Стантит", Category.MOVEMENT);
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!inGame() || player() == null) return;
        // Только PRE: не дёргаем setSprinting дважды за тик
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        boolean hasForward = player().input != null && player().input.keyPresses != null
                ? player().input.keyPresses.forward()
                : mc.options.keyUp.isDown();
        // Не форсим спринт, толчась в стену: нулевое перемещение со спринтом — флаг
        boolean pushingIntoWall = player().horizontalCollision && hasForward;
        boolean canSprint = hasForward
                && !pushingIntoWall
                && !player().isShiftKeyDown()
                && !player().hasEffect(MobEffects.BLINDNESS)
                && player().getFoodData().getFoodLevel() > 6;

        if (canSprint) {
            player().setSprinting(true);
        }
    }
}
