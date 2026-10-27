package pub.godweer.auxiliary.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public class NetworkUtils {

    private static final Set<Packet<?>> silentPackets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static Set<Packet<?>> getPackets() {
        return silentPackets;
    }

    public static void sendPacket(Packet<?> packet) {
        if (mc.getConnection() != null) {
            mc.getConnection().send(packet);
        }
    }
    public static void sendInputPacket(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        Input input = new Input(forward, backward, left, right, jump, sneak, sprint);
        mc.getConnection().send(new ServerboundPlayerInputPacket(input));
    }
    public static void sendSilentPacket(Packet<?> packet) {
        if (mc.getConnection() != null && packet != null) {
            silentPackets.add(packet);
            mc.getConnection().send(packet);
        }
    }
}