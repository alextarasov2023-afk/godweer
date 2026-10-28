package pub.godweer.event.list;

import lombok.Getter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
public final class ScreenMouseButtonEvent extends Event {
    public enum Action { CLICK, RELEASE, DRAG }

    private Screen screen;
    private MouseButtonEvent mouseButtonEvent;
    private Action action;
    private double dragX;
    private double dragY;

    public ScreenMouseButtonEvent set(Screen screen, MouseButtonEvent mouseButtonEvent, Action action, double dragX, double dragY) {
        this.screen = screen;
        this.mouseButtonEvent = mouseButtonEvent;
        this.action = action;
        this.dragX = dragX;
        this.dragY = dragY;
        return this;
    }
}