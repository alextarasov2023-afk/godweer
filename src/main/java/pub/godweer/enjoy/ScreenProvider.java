package pub.godweer.enjoy;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import pub.godweer.IMinecraft;

/**
 * Create by daun kvass
 */
public interface ScreenProvider extends IMinecraft {
    default Screen currentScreen() {
        return screen();
    }
    default AbstractContainerScreen<?> containerScreen() {
        return currentScreen() instanceof AbstractContainerScreen<?> containerScreen ? containerScreen : null;
    }
}
