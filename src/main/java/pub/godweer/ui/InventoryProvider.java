package pub.godweer.ui;


import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Create by daun kvass
 */
public interface InventoryProvider extends ScreenProvider {
    default AbstractContainerMenu menu() {AbstractContainerScreen<?> containerScreen = containerScreen();return containerScreen != null ? containerScreen.getMenu() : null;}
    default boolean hasMenu() {return menu() != null;}
}
