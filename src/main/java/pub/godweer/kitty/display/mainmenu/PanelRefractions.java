package pub.godweer.kitty.display.mainmenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import pub.godweer.hud.Notifications;
import pub.godweer.kitty.display.mainmenu.popup.Modal;
import pub.godweer.kitty.display.night.NightRenderer;
import pub.godweer.kitty.display.night.NightState;

/**
 * Compatibility facade: same static API, fresh NightX backend.
 */
public final class PanelRefractions {
    private static final NightState STATE = new NightState();
    private static final NightRenderer RENDERER = new NightRenderer();

    public static boolean toggle(Minecraft mc) {
        if (STATE.isInteractive()) {
            close(mc);
            return true;
        }
        open(mc);
        return true;
    }

    public static void open(Minecraft mc) {
        STATE.open();
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    public static void close(Minecraft mc) {
        if (!STATE.isOpen() || STATE.isClosing()) return;
        STATE.beginClose();
        if (mc.level != null && mc.player != null && mc.gui.screen() == null) {
            mc.mouseHandler.grabMouse();
        }
    }

    public static void forceClose() {
        STATE.forceClose();
    }

    public static void openModal(Modal modal) {
        STATE.setModal(modal);
    }

    public static boolean isOpen() {
        return STATE.isInteractive();
    }

    public static boolean isTyping() {
        return STATE.isTyping();
    }

    public static boolean blocksInput() {
        return STATE.isInteractive();
    }

    public static boolean handleMouseButton(Minecraft mc, int button, int action) {
        if (!STATE.isInteractive()) return false;
        int mouseX = (int) Math.round(mc.mouseHandler.getScaledXPos(mc.getWindow()));
        int mouseY = (int) Math.round(mc.mouseHandler.getScaledYPos(mc.getWindow()));
        return RENDERER.handleMouseButton(STATE, mouseX, mouseY, button, action);
    }

    public static boolean handleKey(int key, int scanCode, int modifiers) {
        return STATE.isInteractive() && RENDERER.handleKey(STATE, key, scanCode, modifiers);
    }

    public static void handleChar(int codePoint) {
        if (STATE.isInteractive()) {
            RENDERER.handleChar(STATE, codePoint);
        }
    }

    public static void handleScroll(double vertical) {
        if (!STATE.isInteractive()) return;
        Minecraft mc = Minecraft.getInstance();
        int mouseX = (int) Math.round(mc.mouseHandler.getScaledXPos(mc.getWindow()));
        int mouseY = (int) Math.round(mc.mouseHandler.getScaledYPos(mc.getWindow()));
        RENDERER.handleScroll(STATE, vertical, mouseX, mouseY);
    }

    public static void render(Minecraft mc, GuiGraphicsExtractor extractor, int screenWidth, int screenHeight) {
        int mouseX = (int) Math.round(mc.mouseHandler.getScaledXPos(mc.getWindow()));
        int mouseY = (int) Math.round(mc.mouseHandler.getScaledYPos(mc.getWindow()));
        RENDERER.render(mc, extractor, STATE, screenWidth, screenHeight, mouseX, mouseY);
        Notifications.getInstance().draw(extractor, screenWidth, screenHeight);
    }
}
