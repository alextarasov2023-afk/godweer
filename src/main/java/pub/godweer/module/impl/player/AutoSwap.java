package pub.godweer.module.impl.player;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.KeyboardInputEvent;
import pub.godweer.event.list.MouseInputEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.impl.combat.AutoTotem;
import pub.godweer.module.settings.impl.BindSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.ui.clickgui.PanelRefractions;
import pub.godweer.util.player.MoveBlockUtility;

import java.util.List;

/**
 * Свап руки 1 и руки 2 по бинду: предмет из второй руки переезжает в первую,
 * как ванильная F, но со сбросом спринта — ровно как это делает ServerHelper.
 *
 * Сброс спринта локальный: спринт гасится через setSprinting(false) в тик перед
 * транзакцией, свой STOP_SPRINTING не шлём — ванильный LocalPlayer.sendChanges()
 * отправляет его сам и заодно сверяет локальный флаг с серверным, чего ручные
 * пакеты ломают. MoveBlockUtility.blockSprint не даёт спринту вернуться, пока
 * идёт обмен слотов.
 *
 * Список «Для 1 руки» — белый список предметов, которыми вообще имеет смысл
 * обмениваться: яблоко, тотем, щит, заряд ветра, голова игрока и талисман
 * (зачарованный тотем). Свап проходит, если разрешён хотя бы один из двух
 * предметов: иначе нечем было бы обменивать руки, а обычный тотем и талисман
 * различаются зачарованием.
 */
public final class AutoSwap extends Module {

    /** Кнопка свапа с offhand в контейнерном клике (то же, что жмёт ванильная F). */
    private static final int OFFHAND_BUTTON = 40;

    private static final String APPLE = "Яблоко";
    private static final String TOTEM = "Тотем";
    private static final String SHIELD = "Щит";
    private static final String WIND_CHARGE = "Заряд ветра";
    private static final String PLAYER_HEAD = "Голова игрока";
    private static final String TALISMAN = "Талисман";

    public final BindSetting swapKey = bind("Свап", InputConstants.KEY_R);
    public final MultiModeSetting handItems = multiMode("Для 1 руки",
            List.of(APPLE, TOTEM, SHIELD, WIND_CHARGE, PLAYER_HEAD, TALISMAN),
            APPLE, TOTEM, SHIELD, WIND_CHARGE, PLAYER_HEAD, TALISMAN);

    private enum State {
        IDLE,
        STOP_SPRINT,
        EXECUTE
    }

    private State state = State.IDLE;

    public AutoSwap() {
        super("AutoSwap", "Свап руки 1 и руки 2 по бинду", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        MoveBlockUtility.unblock(this);
    }

    /**
     * Клавишу не отменяем (как в ServerHelper): у keyPress HEAD-инъекция, и отмена
     * сняла бы всю ванильную обработку нажатия. Мышь гасим — иначе кнопка свапа
     * одновременно ударила бы или начала использование предмета.
     */
    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (event.getAction() != InputConstants.PRESS) return;
        if (swapKey.matches(event.getKey())) {
            queueSwap();
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (event.getAction() != InputConstants.PRESS) return;
        if (swapKey.matchesMouse(event.getButton()) && queueSwap()) {
            event.cancel();
        }
    }

    private boolean queueSwap() {
        if (state != State.IDLE || AutoTotem.isSwapping()) return false;
        if (!inGame() || player() == null || gameMode() == null) return false;
        if (screen() != null || PanelRefractions.isOpen()) return false;

        LocalPlayer player = player();
        ItemStack main = player.getInventory().getItem(player.getInventory().getSelectedSlot());
        ItemStack off = player.getOffhandItem();
        if (main.isEmpty() && off.isEmpty()) return false;

        if (!isAllowed(main) && !isAllowed(off)) return false;

        state = State.STOP_SPRINT;
        return true;
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || gameMode() == null || AutoTotem.isSwapping()) {
            reset();
            return;
        }

        LocalPlayer player = player();

        switch (state) {
            case STOP_SPRINT -> {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
                if (player.onGround()) {
                    player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
                }

                MoveBlockUtility.freeze(this, 6);
                MoveBlockUtility.blockSprint(this, 8);

                state = State.EXECUTE;
            }

            case EXECUTE -> {
                executeSwap(player);
                state = State.IDLE;
            }

            case IDLE -> {
            }
        }
    }

    /**
     * Один клик по выбранному слоту хотбара с кнопкой offhand: он же обновляет
     * клиентский InventoryMenu, поэтому рука не «зависает» с картинкой старого
     * предмета до синхронизации от сервера.
     */
    private void executeSwap(LocalPlayer player) {
        int slot = InventoryMenu.USE_ROW_SLOT_START + player.getInventory().getSelectedSlot();
        gameMode().handleContainerInput(player.inventoryMenu.containerId, slot,
                OFFHAND_BUTTON, ContainerInput.SWAP, player);
    }

    /** Разрешён ли предмет белым списком «Для 1 руки». */
    private boolean isAllowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (stack.is(Items.APPLE) || stack.is(Items.GOLDEN_APPLE)) {
            return handItems.isEnabled(APPLE);
        }
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return stack.isEnchanted() ? handItems.isEnabled(TALISMAN) : handItems.isEnabled(TOTEM);
        }
        if (stack.is(Items.SHIELD)) {
            return handItems.isEnabled(SHIELD);
        }
        if (stack.is(Items.WIND_CHARGE)) {
            return handItems.isEnabled(WIND_CHARGE);
        }
        if (stack.is(Items.PLAYER_HEAD)) {
            return handItems.isEnabled(PLAYER_HEAD);
        }
        return false;
    }
}
