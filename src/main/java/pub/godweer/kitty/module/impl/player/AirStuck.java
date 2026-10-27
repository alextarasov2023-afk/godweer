package pub.godweer.kitty.module.impl.player;

import net.minecraft.client.player.LocalPlayer;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.EventTravel;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Замораживает падение: пока висим в воздухе, собственное движение клиента
 * отменяется, поэтому блоки ставятся под себя и перлу можно кинуть с места.
 *
 * Раньше модуль ещё отменял исходящий ServerboundMovePlayerPacket. Это прямой
 * путь к флагу: сервер ждёт пакет движения каждый тик, а без него считает свою
 * физику дальше — при разморозке клиент «телепортируется» обратно, и это читается
 * как BadPackets + Moved Too Quickly. Теперь пакеты никогда не отменяются:
 * позиция просто не меняется, и сервер видит то же, что и клиент.
 *
 * Второе, за что цепляют duration-проверки (float/air): вечное зависание на одной
 * точке. Поэтому заморозка «дышит» — держит не дольше Max Hold тиков, затем
 * Release тиков падает и снова держит. Накопленную скорость падение тоже не
 * копит: вертикальная скорость обнуляется каждый тик заморозки, иначе резкая
 * остановка после разгона читается как невозможное торможение в воздухе.
 *
 * Max Hold/Release — это ручка компромисса: больше hold — сильней виснет, но
 * длиннее пауза в воздухе, которую уже видно в float-проверке.
 */
public class AirStuck extends Module {

    private final SliderSetting hold = slider("Max Hold", 6.0f, 1.0f, 30.0f, 1.0f);
    private final SliderSetting release = slider("Release", 4.0f, 1.0f, 40.0f, 1.0f);

    private boolean stuck = false;
    private int heldTicks;
    private int freeTicks;

    public AirStuck() {
        super("AirStuck", "Замораживает тебя как глыбу", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        stuck = false;
        heldTicks = 0;
        freeTicks = 0;
    }

    /**
     * На земле отменённый ход = «стоишь на месте» вместо шага, на лестнице и в
     * креатив-полёте игроку падать некуда — там заморозка только мешает.
     */
    @EventTarget
    public void onTick(PlayerTickEvent event) {
        LocalPlayer player = player();
        if (event.getPhase() != PlayerTickEvent.Phase.PRE || player == null) {
            reset();
            return;
        }

        boolean airborne = !player.onGround() && !player.onClimbable() && !player.getAbilities().flying;
        if (!airborne) {
            reset();
            return;
        }

        if (stuck) {
            if (++heldTicks >= hold.getValue().intValue()) {
                stuck = false;
                heldTicks = 0;
                freeTicks = 0;
                return;
            }
            // Позиция не обновляется, а fallDistance и скорость копятся: на разморозке
            // прилетел бы урон и рывок, которого сервер не ждёт.
            player.fallDistance = 0.0F;
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
        } else if (++freeTicks >= release.getValue().intValue()) {
            stuck = true;
            heldTicks = 0;
            freeTicks = 0;
        }
    }

    @EventTarget
    public void onTravel(EventTravel event) {
        if (stuck) {
            event.cancel();
        }
    }

    private void reset() {
        stuck = false;
        heldTicks = 0;
        freeTicks = 0;
    }
}
