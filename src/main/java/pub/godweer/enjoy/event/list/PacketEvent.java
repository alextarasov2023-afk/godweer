package pub.godweer.enjoy.event.list;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.protocol.Packet;
import pub.godweer.enjoy.event.Event;

/**
 * Create by daun kvass
 */
@Getter
@Setter
public final class PacketEvent extends Event {

    public enum Type {
        SEND,
        RECEIVE
    }

    private final Type type;
    private Packet<?> packet;


    public PacketEvent(Type type, Packet<?> packet) {
        this.type = type;
        this.packet = packet;
    }


    public boolean isSend() {
        return this.type == Type.SEND;
    }


    public boolean isReceive() {
        return this.type == Type.RECEIVE;
    }



    public boolean is(Class<?> packetClass) {
        return packetClass != null && packetClass.isInstance(this.packet);
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet<?>> T getPacket() {
        return (T) this.packet;
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet<?>> T get(Class<T> clazz) {
        return is(clazz) ? (T) this.packet : null;
    }
}