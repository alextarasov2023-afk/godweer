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
import pub.godweer.hud.Notifications;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.BindSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.ui.clickgui.PanelRefractions;
import pub.godweer.util.player.MoveBlockUtility;

import java.util.Optional;

/**
 * Свап руки 1 и руки 2 по бинду: предметы между руками меняются местами,
 * как ванильная F, но со сбросом спринта — ровно как это делает ServerHelper.
 *
 * Сброс спринта локальный: спринт гасится через setSprinting(false) в тик перед
 * транзакцией, свой STOP_SPRINTING не шлём — ванильный LocalPlayer.sendChanges()
 * отправляет его сам и заодно сверяет локальный флаг с серверным, чего ручные
 * пакеты ломают. MoveBlockUtility.blockSprint не даёт спринту вернуться, пока
 * идёт обмен слотов.
 *
 * «Для 1 руки» и «Для 2 руки» — по одному режиму на руку: «Любое» пропускает
 * любой предмет из списка, конкретный режим требует именно его (яблоко, тотем,
 * щит, заряд ветра, голова игрока, талисман — зачарованный тотем). Свап
 * проходит, когда каждая непустая рука удовлетворяет своему режиму.
 */
public final class AutoSwap extends Module {

    /** Кнопка свапа с offhand в контейнерном клике (то же, что жмёт ванильная F). */
    private static final int OFFHAND_BUTTON = 40;

    private static final String ANY = "Любое";
    private static final String APPLE = "Яблоко";
    private static final String TOTEM = "Тотем";
    private static final String SHIELD = "Щит";
    private static final String WIND_CHARGE = "Заряд ветра";
    private static final String PLAYER_HEAD = "Голова игрока";
    private static final String TALISMAN = "Талисман";

    private static AutoSwap instance;

    public final BindSetting swapKey = bind("Свап", InputConstants.KEY_R);
    public final ModeSetting mainHandItems = mode("Для 1 руки", ANY,
            ANY, APPLE, TOTEM, SHIELD, WIND_CHARGE, PLAYER_HEAD, TALISMAN);
    public final ModeSetting offHandItems = mode("Для 2 руки", ANY,
            ANY, APPLE, TOTEM, SHIELD, WIND_CHARGE, PLAYER_HEAD, TALISMAN);

    private enum State {
        IDLE,
        STOP_SPRINT,
        EXECUTE
    }

    private State state = State.IDLE;
    /** Слот, проверенный при нажатии: за тик до обмена колесо могло уехать. */
    private int swapSlot = -1;

    public AutoSwap() {
        super("AutoSwap", "Свап руки 1 и руки 2 по бинду", Category.PLAYER);
        instance = this;
    }

    /** Занят ли обмен слотов (ServerHelper и другие транзакции ждут этот флаг). */
    public static boolean isSwapping() {
        return instance != null && instance.isEnabled() && instance.state != State.IDLE;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        swapSlot = -1;
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
        if (state != State.IDLE) {
            Notifications.push("Свап ещё выполняется");
            return false;
        }
        if (!inGame() || player() == null || gameMode() == null) return false;
        if (screen() != null || PanelRefractions.isOpen()) return false;

        LocalPlayer player = player();
        int slot = player.getInventory().getSelectedSlot();
        ItemStack main = player.getInventory().getItem(slot);
        ItemStack off = player.getOffhandItem();
        if (main.isEmpty() && off.isEmpty()) {
            Notifications.push("Свап: обе руки пусты");
            return false;
        }

        if (!handMatches(main, mainHandItems)) {
            Notifications.push("Свап: " + itemName(main) + " — рука 1 настроена на «" + mainHandItems.getValue() + "»");
            return false;
        }
        if (!handMatches(off, offHandItems)) {
            Notifications.push("Свап: " + itemName(off) + " — рука 2 настроена на «" + offHandItems.getValue() + "»");
            return false;
        }

        swapSlot = slot;
        state = State.STOP_SPRINT;
        return true;
    }

    private static String itemName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || gameMode() == null) {
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
                swapSlot = -1;
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
        int slot = swapSlot >= 0 ? swapSlot : player.getInventory().getSelectedSlot();
        int menuSlot = InventoryMenu.USE_ROW_SLOT_START + slot;
        gameMode().handleContainerInput(player.inventoryMenu.containerId, menuSlot,
                OFFHAND_BUTTON, ContainerInput.SWAP, player);
    }

    /** Рука согласна: пустая рука всегда согласна, занятая — по выбранному режиму. */
    private boolean handMatches(ItemStack stack, ModeSetting allowed) {
        if (stack == null || stack.isEmpty()) return true;
        Optional<String> type = classify(stack);
        return type.filter(value -> ANY.equals(allowed.getValue()) || allowed.getValue().equals(value)).isPresent();
    }

    /** Категория предмета из настроек, empty — предмет не из списка. */
    private Optional<String> classify(ItemStack stack) {
        if (stack.is(Items.APPLE) || stack.is(Items.GOLDEN_APPLE)) {
            return Optional.of(APPLE);
        }
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return Optional.of(stack.isEnchanted() ? TALISMAN : TOTEM);
        }
        if (stack.is(Items.SHIELD)) {
            return Optional.of(SHIELD);
        }
        if (stack.is(Items.WIND_CHARGE)) {
            return Optional.of(WIND_CHARGE);
        }
        if (stack.is(Items.PLAYER_HEAD)) {
            return Optional.of(PLAYER_HEAD);
        }
        return Optional.empty();
    }
}
