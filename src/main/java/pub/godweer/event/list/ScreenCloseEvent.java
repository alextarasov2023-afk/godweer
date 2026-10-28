package pub.godweer.event.list;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
public final class ScreenCloseEvent extends Event {
    private Minecraft client;
    private Screen screen;

    public ScreenCloseEvent set(Minecraft client, Screen screen) {
        this.client = client;
        this.screen = screen;
        return this;
    }
}
