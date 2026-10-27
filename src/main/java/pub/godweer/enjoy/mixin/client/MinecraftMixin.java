package pub.godweer.enjoy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.enjoy.event.EventManager;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.enjoy.event.list.WorldJoinEvent;
import pub.godweer.enjoy.event.list.WorldLeaveEvent;
import pub.godweer.kitty.sosiski.account.AccountManager;

/**
 * Create by daun kvass
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    public ClientLevel level;
    @Unique
    private static final GameTickEvent TICK_EVENT = new GameTickEvent();
    @Unique
    private static final WorldLeaveEvent WORLD_LEAVE_EVENT = new WorldLeaveEvent();
    @Unique
    private static final WorldJoinEvent WORLD_JOIN_EVENT = new WorldJoinEvent();
    @Unique
    private ClientLevel previousLevel;
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Client.INSTANCE.eventManager.call(TICK_EVENT);
    }
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void capturePreviousLevel(ClientLevel level, CallbackInfo ci) {
        this.previousLevel = this.level;
    }
    @Shadow
    @Nullable
    public LocalPlayer player;
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void onSetLevel(ClientLevel level, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        ClientLevel previous = this.previousLevel;
        Client.INSTANCE.eventManager.call(WORLD_LEAVE_EVENT.set(client, previous));
        EventManager.call(WORLD_JOIN_EVENT.set(client, level));
    }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onMinecraftInit(GameConfig gameConfig, CallbackInfo ci) {
        AccountManager.getInstance().applyActiveSession();
    }
}