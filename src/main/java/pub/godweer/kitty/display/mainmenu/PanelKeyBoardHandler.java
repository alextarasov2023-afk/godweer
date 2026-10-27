package pub.godweer.kitty.display.mainmenu;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.*;
import pub.godweer.kitty.module.impl.render.ClickGui;

/**
 * Create by daun kvass
 */
public final class PanelKeyBoardHandler implements IMinecraft {

    @EventTarget(priority = 1000)
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (event.getKey() == InputConstants.KEY_F11) return;

        // Vanilla screens always take priority: if one is open while the overlay is
        // active, silently close the overlay and let vanilla handle the key.
        // This guarantees ESC closes chat/inventory and opens the pause menu.
        if (PanelRefractions.isOpen() && screen() != null) {
            PanelRefractions.forceClose();
        }

        ClickGui clickGui = Client.INSTANCE.moduleManager.getClickGui();
        boolean isGuiKey = clickGui != null && clickGui.getBind().matches(event.getKey());

        if (clickGui != null && !clickGui.getBind().isBound() && event.getKey() == InputConstants.KEY_RSHIFT) {
            isGuiKey = true;
        }

        if (event.getAction() == InputConstants.PRESS && isGuiKey && screen() == null) {
            PanelRefractions.toggle(mc);
            event.cancel();
            return;
        }

        if (!PanelRefractions.isOpen()) {
            if (event.getAction() == InputConstants.PRESS && screen() == null) {
                Client.INSTANCE.moduleManager.onKey(event.getKey());
            }
            return;
        }

        boolean pressOrRepeat = event.getAction() == InputConstants.PRESS || event.getAction() == InputConstants.REPEAT;
        if (pressOrRepeat && event.getKey() == InputConstants.KEY_ESCAPE) {
            // ESC closes the top-most layer first: modal / search / bind listening.
            // Only if nothing consumed it, close the panel itself.
            if (!PanelRefractions.handleKey(event.getKey(), event.getScanCode(), event.getModifiers())) {
                PanelRefractions.close(mc);
            }
            event.cancel();
            return;
        }

        if (pressOrRepeat && PanelRefractions.handleKey(event.getKey(), event.getScanCode(), event.getModifiers())) {
            event.cancel();
            return;
        }

        if (PanelRefractions.isTyping()) {
            event.cancel();
        }
    }

    @EventTarget(priority = 1000)
    public void onCharacterInput(CharacterInputEvent event) {
        if (PanelRefractions.isOpen()) {
            PanelRefractions.handleChar(event.getCodePoint());
            if (PanelRefractions.isTyping()) {
                event.cancel();
            }
        }
    }

    @EventTarget(priority = 1000)
    public void onMouseInput(MouseInputEvent event) {
        // Keep the overlay from coexisting with vanilla screens (it would block
        // their mouse input and swallow ESC on the next key press).
        if (PanelRefractions.isOpen() && screen() != null) {
            PanelRefractions.forceClose();
        }

        ClickGui clickGui = Client.INSTANCE.moduleManager.getClickGui();
        boolean isGuiMouse = clickGui != null && clickGui.getBind().matchesMouse(event.getButton());

        if (isGuiMouse && event.getAction() == InputConstants.PRESS && screen() == null) {
            if (PanelRefractions.toggle(mc)) {
                event.cancel();
                return;
            }
        }

        if (!PanelRefractions.isOpen()) {
            if (event.getAction() == InputConstants.PRESS && screen() == null) {
                Client.INSTANCE.moduleManager.onMouse(event.getButton());
            }
            return;
        }

        if (PanelRefractions.handleMouseButton(mc, event.getButton(), event.getAction())) {
            event.cancel();
        }
    }

    @EventTarget(priority = 1000)
    public void onScreenKey(ScreenKeyEvent event) {
        if (PanelRefractions.isTyping()) event.cancel();
    }

    @EventTarget(priority = 1000)
    public void onScreenMouseButton(ScreenMouseButtonEvent event) {
        if (PanelRefractions.isOpen()) event.cancel();
    }
}
