package pub.godweer.event.list;


import lombok.Getter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
public final class MenuRenderEvent extends Event {
    private Minecraft client;
    private Gui gui;
    private GuiGraphicsExtractor guiGraphicsExtractor;
    private DeltaTracker deltaTracker;
    private boolean renderHud;
    private boolean renderScreen;
    private int mouseX;
    private int mouseY;

    public MenuRenderEvent set(Minecraft client, Gui gui, GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker, boolean renderHud, boolean renderScreen, int mouseX, int mouseY) {
        this.client = client;
        this.gui = gui;
        this.guiGraphicsExtractor = guiGraphicsExtractor;
        this.deltaTracker = deltaTracker;
        this.renderHud = renderHud;
        this.renderScreen = renderScreen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }
}