package pub.godweer.auxiliary.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ContainerInput;
import pub.godweer.auxiliary.network.NetworkUtils;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public class InventorySwaps {
    private static final int INVENTORY_SLOTS_START = 9;
    private static final int CHESTPLATE_SLOT = 6;

    public static void grimSwapsArmor(LocalPlayer player, int targetSlot) {
        boolean wasSprinting = player.isSprinting();
        NetworkUtils.sendInputPacket(false, false, false, false, false, false, false);
        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }
        executeContainerSwap(player, targetSlot, CHESTPLATE_SLOT);
        NetworkUtils.sendSilentPacket(new ServerboundContainerClosePacket(0));
        NetworkUtils.sendPacket(new ServerboundPlayerInputPacket(mc.player.input.keyPresses));
        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }

    public static void grimSwapsOffHand(LocalPlayer player, int slot) {
        boolean wasSprinting = player.isSprinting();
        int syncId = mc.player.inventoryMenu.containerId;
        NetworkUtils.sendInputPacket(false, false, false, false, false, false, false);
        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }
        mc.gameMode.handleContainerInput(syncId, slot, 40, ContainerInput.SWAP, player);
        NetworkUtils.sendSilentPacket(new ServerboundContainerClosePacket(0));
        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }


    public static void grimSwapToHotbar(LocalPlayer player, int invSlot, int hotbarSlot) {
        if (player == null || mc.gameMode == null || mc.getConnection() == null) return;

        boolean wasSprinting = player.isSprinting();
        NetworkUtils.sendInputPacket(false, false, false, false, false, false, false);

        if (wasSprinting) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        int containerId = player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(containerId, invSlot, hotbarSlot, ContainerInput.SWAP, player);

        NetworkUtils.sendSilentPacket(new ServerboundContainerClosePacket(0));
        NetworkUtils.sendPacket(new ServerboundPlayerInputPacket(player.input.keyPresses));

        if (wasSprinting) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }

    public static void performHotbarFSwap(LocalPlayer player, int hotbarSlot) {
        if (mc.getConnection() == null) return;
        int prevSlot = player.getInventory().getSelectedSlot();
        if (prevSlot != hotbarSlot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));

        if (prevSlot != hotbarSlot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(prevSlot));
        }
    }

    public static void executeContainerSwap(LocalPlayer player, int fromSlot, int slots) {
        int containerId = player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(containerId, fromSlot, 0, ContainerInput.SWAP, player);
        mc.gameMode.handleContainerInput(containerId, slots, 0, ContainerInput.SWAP, player);
        mc.gameMode.handleContainerInput(containerId, fromSlot, 0, ContainerInput.SWAP, player);
    }
}