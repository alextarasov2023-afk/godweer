package pub.godweer.auxiliary;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Create by daun kvass
 */
public final class RenderExtend {
    private static Gui currentGui;
    private static GuiGraphicsExtractor currentExtractor;
    private static DeltaTracker currentDeltaTracker;
    private static boolean in2D;

    private static GameRenderer currentGameRenderer;
    private static DeltaTracker current3DDeltaTracker;
    private static boolean in3D;

    private RenderExtend() {}

    public static void enter2D(Gui gui, GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        currentGui = gui;
        currentExtractor = extractor;
        currentDeltaTracker = deltaTracker;
        in2D = true;
    }

    public static void exit2D() {
        currentGui = null;
        currentExtractor = null;
        currentDeltaTracker = null;
        in2D = false;
    }


    public static GuiGraphicsExtractor currentGuiGraphicsExtractor() {
        return currentExtractor;
    }

    public static void enter3D(GameRenderer gameRenderer, DeltaTracker deltaTracker) {
        currentGameRenderer = gameRenderer;
        current3DDeltaTracker = deltaTracker;
        in3D = true;
    }

    public static void exit3D() {
        currentGameRenderer = null;
        current3DDeltaTracker = null;
        in3D = false;
    }
    public static long sttime = -1L;
}