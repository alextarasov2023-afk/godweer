package pub.godweer.kitty.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.KeyboardInputEvent;
import pub.godweer.enjoy.event.list.MouseInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.display.mainmenu.PanelRefractions;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.impl.combat.AutoTotem;
import pub.godweer.kitty.module.settings.impl.BindSetting;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.auxiliary.player.InventorySwaps;
import pub.godweer.auxiliary.player.InventoryUtil;
import pub.godweer.auxiliary.player.MoveBlockUtility;

/**
 * Хелпер для элитр.
 * Бинды: использование фейерверка и свап элитра/нагрудника.
 * Режимы Packet/Normal. Галочка «Брать из инвентаря» — легит-путь через невидимый инвентарь.
 */
public final class ElytraHelper extends Module {

    private static final int CHESTPLATE_SLOT = 6;
    /** Контейнер-слоты хотбара в InventoryMenu (36..44). */
    private static final int HOTBAR_CONTAINER_START = 36;
    private static final int HOTBAR_SIZE = 9;

    public final BindSetting fireworkKey = bind("Исп. фейерверка", InputConstants.KEY_V);
    public final BindSetting elytraKey = bind("Свап элитры", InputConstants.KEY_B);
    public final ModeSetting fireworkMode = mode("Режим фейерверка", "Packet", "Packet", "Normal");
    public final ModeSetting elytraMode = mode("Режим элитры", "Packet", "Packet", "Normal");
    public final CheckBox takeFromInventory = checkbox("Брать из инвентаря", true);

    private enum TaskType {
        NONE,
        FIREWORK_INV,   // невидимый инвентарь: взять фейерверк, юзануть, вернуть
        ELYTRA_INV,     // невидимый инвентарь: надеть элитру/нагрудник
        ELYTRA_PACKET,  // пакетный свап брони (в т.ч. из хотбара)
        ELYTRA_HOTBAR   // хотбар: переключиться, надеть юзом, вернуть слот
    }

    private enum State {
        IDLE,
        STOP_SPRINT,
        EXECUTE,
        COOLDOWN
    }

    private State state = State.IDLE;
    private TaskType currentTask = TaskType.NONE;
    private boolean fireworkQueued = false;
    private boolean elytraQueued = false;
    private int cooldownTicks = 0;
    private boolean wasSprinting = false;

    public ElytraHelper() {
        super("ElytraHelper", "Быстрый свап элитры и запуск фейерверков", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        currentTask = TaskType.NONE;
        fireworkQueued = false;
        elytraQueued = false;
        cooldownTicks = 0;
        wasSprinting = false;
        MoveBlockUtility.unblock(this);
        // если выключили посреди «призрачного» инвентаря — закрываем его
        if (mc.gui != null && mc.gui.screen() instanceof GhostInventoryScreen) {
            mc.gui.setScreen(null);
        }
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (AutoTotem.isSwapping()) return;
        if (event.getAction() != InputConstants.PRESS) return;

        if (fireworkKey.matches(event.getKey())) {
            queueFirework();
        } else if (elytraKey.matches(event.getKey())) {
            queueElytra();
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (AutoTotem.isSwapping()) return;
        if (event.getAction() != InputConstants.PRESS) return;

        if (fireworkKey.matchesMouse(event.getButton())) {
            if (queueFirework()) {
                event.cancel();
            }
        } else if (elytraKey.matchesMouse(event.getButton())) {
            if (queueElytra()) {
                event.cancel();
            }
        }
    }

    private boolean queueFirework() {
        if (AutoTotem.isSwapping() || !inGame() || player() == null || mc.gameMode == null) return false;
        if (mc.gui.screen() != null || PanelRefractions.isOpen() || state != State.IDLE) return false;
        fireworkQueued = true;
        return true;
    }

    private boolean queueElytra() {
        if (AutoTotem.isSwapping() || !inGame() || player() == null || mc.gameMode == null) return false;
        if (mc.gui.screen() != null || PanelRefractions.isOpen() || state != State.IDLE) return false;
        elytraQueued = true;
        return true;
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || mc.gameMode == null) {
            reset();
            return;
        }

        if (AutoTotem.isSwapping()) {
            reset();
            return;
        }

        LocalPlayer player = player();

        if (fireworkQueued) {
            fireworkQueued = false;
            handleFirework(player);
        }

        if (elytraQueued) {
            elytraQueued = false;
            handleElytra(player);
        }

        switch (state) {
            case STOP_SPRINT -> {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                    wasSprinting = true;
                }
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }
                MoveBlockUtility.freeze(this, 4);
                MoveBlockUtility.blockSprint(this, 5);
                state = State.EXECUTE;
            }

            case EXECUTE -> {
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }
                // держим блокировку движения на время самого свапа
                MoveBlockUtility.freeze(this, 3);

                switch (currentTask) {
                    case FIREWORK_INV -> executeFireworkFromInventory(player);
                    case ELYTRA_INV -> executeElytraFromInventory(player);
                    case ELYTRA_PACKET -> {
                        int targetSlot = findSwapTargetSlot(player);
                        if (targetSlot != -1) {
                            InventorySwaps.grimSwapsArmor(player, targetSlot);
                        }
                    }
                    case ELYTRA_HOTBAR -> executeElytraFromHotbar(player);
                    case NONE -> {
                    }
                }

                currentTask = TaskType.NONE;
                cooldownTicks = 2;
                state = State.COOLDOWN;
            }

            case COOLDOWN -> {
                MoveBlockUtility.freeze(this, 2);
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                if (--cooldownTicks <= 0) {
                    // если весь путь проходил внутри призрачного инвентаря — закрываем
                    if (mc.gui.screen() instanceof GhostInventoryScreen) {
                        mc.gui.setScreen(null);
                    }
                    if (wasSprinting && mc.getConnection() != null) {
                        mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
                    }
                    wasSprinting = false;
                    state = State.IDLE;
                    MoveBlockUtility.unblock(this);
                }
            }

            case IDLE -> {
            }
        }
    }

    // ===================== ФЕЙЕРВЕРК =====================

    private void handleFirework(LocalPlayer player) {
        // уже в руке — просто юзаем
        if (player.getMainHandItem().is(Items.FIREWORK_ROCKET)) {
            InventoryUtil.useDirect(player);
            return;
        }
        if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            InventoryUtil.useDirect(player, InteractionHand.OFF_HAND);
            return;
        }

        // в хотбаре — переключаемся, юзаем, возвращаем предмет который держали
        int hotbarSlot = findFireworkHotbarSlot(player);
        if (hotbarSlot != -1) {
            int previousSlot = player.getInventory().getSelectedSlot();
            selectHotbarSlot(player, hotbarSlot);
            InventoryUtil.useDirect(player);
            selectHotbarSlot(player, previousSlot);
            return;
        }

        // только в инвентаре
        int invSlot = findFireworkInventorySlot(player);
        if (invSlot == -1) return;

        if (fireworkMode.is("Normal") && takeFromInventory.getValue()) {
            // легит: заходим в невидимый инвентарь, берём в держимый слот, юзаем, возвращаем, выходим
            currentTask = TaskType.FIREWORK_INV;
            state = State.STOP_SPRINT;
        } else {
            // пакетный свап без открытия инвентаря
            performPacketFirework(player, invSlot);
        }
    }

    private void executeFireworkFromInventory(LocalPlayer player) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof GhostInventoryScreen) && screen == null) {
            mc.gui.setScreen(new GhostInventoryScreen(player));
        }

        int invSlot = findFireworkInventorySlot(player);
        if (invSlot == -1) {
            if (mc.gui.screen() instanceof GhostInventoryScreen) {
                mc.gui.setScreen(null);
            }
            return;
        }

        int selectedHotbar = player.getInventory().getSelectedSlot();

        // берём фейерверк в слот который держишь
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
        // используем
        InventoryUtil.useDirect(player);
        // убираем обратно в инвентарь
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);

        // выходим из инвентаря
        if (mc.gui.screen() instanceof GhostInventoryScreen) {
            mc.gui.setScreen(null);
        }
    }

    private void performPacketFirework(LocalPlayer player, int invSlot) {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        int selectedHotbar = player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
        InventoryUtil.useDirect(player);
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }



    // ===================== ЭЛИТРА / НАГРУДНИК =====================

    private void handleElytra(LocalPlayer player) {
        int targetSlot = findSwapTargetSlot(player);
        if (targetSlot == -1) return;

        // цель в хотбаре — свапаем из хотбара: в Normal юзом, в Packet пакетным свапом
        if (isHotbarSlot(targetSlot)) {
            currentTask = elytraMode.is("Normal") ? TaskType.ELYTRA_HOTBAR : TaskType.ELYTRA_PACKET;
            state = State.STOP_SPRINT;
            return;
        }

        if (elytraMode.is("Normal") && takeFromInventory.getValue()) {
            // легит: заходим в невидимый инвентарь и надеваем, потом выходим
            currentTask = TaskType.ELYTRA_INV;
            state = State.STOP_SPRINT;
        } else {
            // пакетный свап брони
            currentTask = TaskType.ELYTRA_PACKET;
            state = State.STOP_SPRINT;
        }
    }

    /** Ищем слот свапа: если надеты элитры — нагрудник, иначе элитры. Хотбар приоритетнее (findPlayerMenuSlot ищет его первым). */
    private int findSwapTargetSlot(LocalPlayer player) {
        ItemStack chestItem = player.inventoryMenu.getSlot(CHESTPLATE_SLOT).getItem();
        boolean wearingElytra = chestItem.is(Items.ELYTRA);

        return wearingElytra
                ? InventoryUtil.findPlayerMenuSlot(player, this::isChestplate)
                : InventoryUtil.findPlayerMenuSlot(player, stack -> stack.is(Items.ELYTRA));
    }

    /** Normal-режим: переключаемся на хотбар-слот, надеваем юзом, возвращаем прежний слот. */
    private void executeElytraFromHotbar(LocalPlayer player) {
        int targetSlot = findSwapTargetSlot(player);
        if (targetSlot == -1 || !isHotbarSlot(targetSlot)) return;

        int hotbarIndex = targetSlot - HOTBAR_CONTAINER_START;
        if (hotbarIndex < 0 || hotbarIndex >= HOTBAR_SIZE) return;

        int previousSlot = player.getInventory().getSelectedSlot();
        selectHotbarSlot(player, hotbarIndex);
        InventoryUtil.useDirect(player);
        selectHotbarSlot(player, previousSlot);
    }

    /** Переключение держимого слота с уведомлением сервера (иначе сервер не знает о смене слота и юз не срабатывает). */
    private void selectHotbarSlot(LocalPlayer player, int hotbarIndex) {
        player.getInventory().setSelectedSlot(hotbarIndex);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarIndex));
        }
    }

    private void executeElytraFromInventory(LocalPlayer player) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof GhostInventoryScreen) && screen == null) {
            mc.gui.setScreen(new GhostInventoryScreen(player));
        }

        int targetSlot = findSwapTargetSlot(player);

        if (targetSlot != -1) {
            InventorySwaps.executeContainerSwap(player, targetSlot, CHESTPLATE_SLOT);
        }

        // выходим из инвентаря
        if (mc.gui.screen() instanceof GhostInventoryScreen) {
            mc.gui.setScreen(null);
        }
    }

    // ===================== УТИЛИТЫ =====================

    private boolean isHotbarSlot(int containerSlot) {
        return containerSlot >= HOTBAR_CONTAINER_START
                && containerSlot < HOTBAR_CONTAINER_START + HOTBAR_SIZE;
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(Items.NETHERITE_CHESTPLATE)
                || stack.is(Items.DIAMOND_CHESTPLATE)
                || stack.is(Items.IRON_CHESTPLATE)
                || stack.is(Items.GOLDEN_CHESTPLATE)
                || stack.is(Items.CHAINMAIL_CHESTPLATE)
                || stack.is(Items.LEATHER_CHESTPLATE);
    }

    private int findFireworkHotbarSlot(LocalPlayer player) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (player.getInventory().getItem(slot).is(Items.FIREWORK_ROCKET)) {
                return slot;
            }
        }
        return -1;
    }

    private int findFireworkInventorySlot(LocalPlayer player) {
        // ищем только в инвентаре (не в хотбаре) — в хотбаре обработано отдельно
        return InventoryUtil.findInventorySlot(player, stack -> stack.is(Items.FIREWORK_ROCKET));
    }
}

