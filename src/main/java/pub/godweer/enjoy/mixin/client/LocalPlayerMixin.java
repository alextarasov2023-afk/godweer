package pub.godweer.enjoy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.Client;
import pub.godweer.cc.RotationHandler;
import pub.godweer.enjoy.event.list.EventPostMotion;
import pub.godweer.enjoy.event.list.EventPreMotion;
import pub.godweer.enjoy.event.list.EventSprint;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.impl.movement.NoForward;
import pub.godweer.kitty.module.impl.player.AntiPush;

/**
 * Create by daun kvass
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Shadow @Final protected Minecraft minecraft;

    private static final EventPreMotion PRE_MOTION = new EventPreMotion();
    private static final EventPostMotion POST_MOTION = new EventPostMotion();

    private float cachedYaw;
    private float cachedPitch;
    private double cachedX;
    private double cachedY;
    private double cachedZ;
    private boolean cachedOnGround;
    private boolean cachedHorizontalCollision;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPre(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        RotationHandler.onPlayerTick(player);
        Client.getInstance().getEventManager().call(new PlayerTickEvent().set(player, PlayerTickEvent.Phase.PRE));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickPost(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Client.getInstance().getEventManager().call(new PlayerTickEvent().set(player, PlayerTickEvent.Phase.POST));
    }
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void onMoveTowardsClosestSpace(double x, double z, CallbackInfo ci) {
        if (AntiPush.INSTANCE.isEnabled() && AntiPush.INSTANCE.modes.isEnabled("Blocks")) {
            ci.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStep(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.isSprinting()) {
            EventSprint sprintEvent = new EventSprint(true);
            Client.getInstance().getEventManager().call(sprintEvent);
            if (!sprintEvent.isSprinting()) {
                player.setSprinting(false);
            }
        }
    }
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendPositionHead(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        this.cachedYaw = player.getYRot();
        this.cachedPitch = player.getXRot();
        this.cachedX = player.getX();
        this.cachedY = player.getY();
        this.cachedZ = player.getZ();
        this.cachedOnGround = player.onGround();
        this.cachedHorizontalCollision = player.horizontalCollision;

        EventPreMotion event = PRE_MOTION.set(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.onGround(), player.horizontalCollision
        );
        Client.getInstance().getEventManager().call(event);

        player.setYRot(event.getYaw());
        player.setXRot(event.getPitch());
        player.setPos(event.getPosX(), event.getPosY(), event.getPosZ());
        player.setOnGround(event.isOnGround());
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onSendPositionTail(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        player.setYRot(this.cachedYaw);
        player.setXRot(this.cachedPitch);
        player.setPos(this.cachedX, this.cachedY, this.cachedZ);
        player.setOnGround(this.cachedOnGround);
        player.horizontalCollision = this.cachedHorizontalCollision;

        Client.getInstance().getEventManager().call(POST_MOTION);
    }

    @Redirect(
            method = "applyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F")
    )
    private float useCameraPitchForHandBob(LocalPlayer player) {
        // В 26.2 свободная ротация подменяла цель hand-bob лерпа (getXRot в сабмите рук);
        // в 26.3 этот лерп теперь живёт в applyInput, поэтому ветка осталась здесь.
        if (RotationHandler.isActive()) {
            return RotationHandler.getFreePitch();
        }
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            return this.minecraft.gameRenderer.mainCamera().xRot();
        }
        return player.getXRot();
    }

    @Redirect(
            method = "applyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F")
    )
    private float useCameraYawForHandBob(LocalPlayer player) {
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            return this.minecraft.gameRenderer.mainCamera().yRot();
        }
        return player.getYRot();
    }

    @ModifyArg(
            method = "sendChanges",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundPlayerInputPacket;<init>(Lnet/minecraft/world/entity/player/Input;)V")
    )
    private Input suppressForwardInPacket(Input input) {
        // Только модуль NoForward: сброс спринта и удары уходят отдельными пакетами
        // (ServerboundPlayerCommandPacket) — сюда не заходят.
        if (!NoForward.serverSuppressed()) return input;
        return NoForward.stripForward(input);
    }

    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void onGetViewYRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (RotationHandler.isActive()) {
            cir.setReturnValue(RotationHandler.getFreeYaw());
        }
    }
}