package pub.godweer.event.list;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import pub.godweer.event.Event;

/**
 * Create by daun kvass
 */
@Getter
public final class WorldLeaveEvent extends Event {
    private Minecraft client;
    private ClientLevel level;

    public WorldLeaveEvent set(Minecraft client, ClientLevel level) {
        this.client = client;
        this.level = level;
        return this;
    }
}
