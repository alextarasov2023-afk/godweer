package pub.godweer.kitty.module.impl.player;

import lombok.Getter;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.enjoy.mixin.accessor.LivingEntityAccessor;
import pub.godweer.enjoy.mixin.accessor.MinecraftAccessor;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;

/**
 * NoDelay — снимает две ванильные задержки:
 * LivingEntity.noJumpDelay (1-2 тика после прыжка/приземления прыжок не принимается)
 * и Minecraft.rightClickDelay (правая кнопка не срабатывает несколько тиков подряд —
 * именно она режет скорость ставки блоков и использования предметов).
 */
@Getter
public class NoDelay extends Module {
    public static NoDelay INSTANCE;

    private final CheckBox jumpDelay = checkbox("Jump Delay", true);
    private final CheckBox placeDelay = checkbox("Place Delay", true);
    // Задержку правой кнопки глушим всегда: она только режет скорость кликов, а
    // сами действия ваниль и так выполняет по нажатию. Опцию включаем, если нужно
    // оставить троттлинг, когда кнопку не держим (еда/щит/ведро без лишнего спама).
    private final CheckBox onlyWhileUse = checkbox("Only While Use", false);

    public NoDelay() {
        super("NoDelay", "Без задержки прыжка и ставки блоков", Category.PLAYER);
        INSTANCE = this;
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        // GameTickEvent = HEAD of Minecraft.tick(), а именно там ваниль и делает
        // "if (rightClickDelay > 0) rightClickDelay--" + consumeClick по keyUse —
        // обнуляем до этого блока, поэтому клик принимается каждый тик (20/с).
        clearPlaceDelay();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (player() == null) return;

        // PRE: noJumpDelay списывается и проверяется в aiStep этого же тика,
        // поэтому обнуляем до обработки движения — прыжок принимается сразу.
        if (!isEnabled() || event.getPhase() != PlayerTickEvent.Phase.PRE || !jumpDelay.getValue()) return;
        ((LivingEntityAccessor) player()).setNoJumpDelay(0);
    }

    private void clearPlaceDelay() {
        if (!isEnabled() || !placeDelay.getValue() || mc == null || mc.options == null) return;
        if (onlyWhileUse.getValue() && !mc.options.keyUse.isDown()) return;
        ((MinecraftAccessor) mc).setRightClickDelay(0);
    }
}
