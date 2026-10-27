package pub.godweer.kitty.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import pub.godweer.auxiliary.client.persiki.KeyUtil;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;

import java.util.HashSet;
import java.util.Set;

/**
 * Create by daun kvass
 */
public class ItemScroller extends Module {
    public static ItemScroller INSTANCE;
    private final Set<Integer> draggedSlots = new HashSet<>();

    public ItemScroller() {
        super("ItemScroller", "Слоты крутить быстро", Category.MISC);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        draggedSlots.clear();
    }


    public boolean onMouseDragged(AbstractContainerScreen<?> screen, Slot slot, MouseButtonEvent event) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return false;

        boolean isLmb = event.button() == InputConstants.MOUSE_BUTTON_LEFT || KeyUtil.isMouseButtonDown(InputConstants.MOUSE_BUTTON_LEFT);
        boolean isShift = isShiftDown();

        if (!isLmb || !isShift) {
            draggedSlots.clear();
            return false;
        }

        if (slot == null || !slot.hasItem()) return false;
        if (draggedSlots.contains(slot.index)) return false;

        draggedSlots.add(slot.index);

        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
        return true;
    }

    public void onMouseClicked(Slot slot) {
        if (slot != null && isShiftDown()) {
            draggedSlots.add(slot.index);
        }
    }

    public void onMouseReleased() {
        draggedSlots.clear();
    }


    public boolean onKeyPressed(AbstractContainerScreen<?> screen, Slot hoveredSlot, KeyEvent event) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return false;

        if (event.key() == InputConstants.KEY_Q && isCtrlDown() && isShiftDown()) {
            if (hoveredSlot == null || !hoveredSlot.hasItem()) return false;

            Item targetItem = hoveredSlot.getItem().getItem();

            for (Slot slot : screen.getMenu().slots) {
                if (slot.hasItem() && slot.getItem().getItem() == targetItem) {
                    mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 1, ContainerInput.THROW, mc.player);
                }
            }
            return true;
        }
        return false;
    }

    public static boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) ||
                InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static boolean isCtrlDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) ||
                InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }
}