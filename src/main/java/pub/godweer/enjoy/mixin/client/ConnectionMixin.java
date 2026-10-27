package pub.godweer.enjoy.mixin.client;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.enjoy.event.list.PacketEvent;
import pub.godweer.auxiliary.network.NetworkUtils;

/**
 * Create by daun kvass
 */
@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (NetworkUtils.getPackets().remove(packet)) {
            return;
        }

        PacketEvent event = new PacketEvent(PacketEvent.Type.SEND, packet);
        Client.getInstance().getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketEvent event = new PacketEvent(PacketEvent.Type.RECEIVE, packet);
        Client.getInstance().getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}