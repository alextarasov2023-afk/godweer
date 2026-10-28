package pub.godweer.util.player;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import pub.godweer.ui.InventoryProvider;

import java.util.function.Predicate;

/**
 * Create by daun kvass
 */
public final class InventoryUtil implements InventoryProvider {
    private static final InventoryUtil CONTEXT = new InventoryUtil();
    private static final int INVENTORY_SLOTS_START = 9;

    private InventoryUtil() {
    }
    public static int findPlayerMenuSlot(LocalPlayer player, Predicate<ItemStack> predicate) {
        for (int slot = InventoryMenu.USE_ROW_SLOT_START; slot < InventoryMenu.USE_ROW_SLOT_END; slot++) {
            if (predicate.test(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        for (int slot = INVENTORY_SLOTS_START; slot < InventoryMenu.USE_ROW_SLOT_START; slot++) {
            if (predicate.test(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }
    public static int findInventorySlot(LocalPlayer player, Predicate<ItemStack> predicate) {
        for (int slot = InventoryMenu.INV_SLOT_START; slot < InventoryMenu.USE_ROW_SLOT_START; slot++) {
            if (predicate.test(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }
    public static void useDirect(LocalPlayer player) {
        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            //    player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }
    public static void useDirect(LocalPlayer player,InteractionHand hand) {
        if (mc.gameMode != null) {
            mc.gameMode.useItem(player,hand);
            //    player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }
    public static boolean swapsinHotbars(int slotId, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        return clicksToSlot(slotId, hotbarSlot, ContainerInput.SWAP);
    }
    public static AbstractContainerMenu getTagetMenu() {return CONTEXT.menu();}

    public static boolean clicksToSlot(int slotId, int button, ContainerInput input) {
        AbstractContainerMenu menu = getTagetMenu();
        return menu != null && clickMenu(menu, slotId, button, input);
    }
    private static boolean clickMenu(AbstractContainerMenu menu, int slotId, int button, ContainerInput input) {
        MultiPlayerGameMode gameMode = CONTEXT.gameMode();
        LocalPlayer player = CONTEXT.player();
        if (gameMode == null || player == null) {
            return false;
        }
        gameMode.handleContainerInput(menu.containerId, slotId, button, input, player);
        return true;
    }



}
