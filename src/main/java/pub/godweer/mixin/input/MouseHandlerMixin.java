package pub.godweer.mixin.input;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.event.Events;
import pub.godweer.event.list.LookEvent;
import pub.godweer.ui.clickgui.PanelRefractions;
import pub.godweer.module.impl.misc.FreeCam;
import pub.godweer.module.impl.misc.ServerHelper;

/**
 * Create by daun kvass
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (Client.getInstance().getEventManager().call(
                Events.MOUSE_INPUT.set(window, buttonInfo.button(), action, buttonInfo.modifiers())
        ).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        try {
            if (pub.godweer.hud.HudManager.getInstance().handleScroll(vertical)) {
                ci.cancel();
                return;
            }
        } catch (Exception ignored) {}
        if (PanelRefractions.blocksInput()) {
            PanelRefractions.handleScroll(vertical);
            ci.cancel();
        }
    }

    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void onTurnPlayer(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            FreeCam.INSTANCE.turn(yawDelta, pitchDelta);
            return;
        }

        // Пока открыто колесо выбора ServerHelper, камера стоит, а движение мыши
        // уходит в его виртуальный курсор.
        ServerHelper helper = ServerHelper.INSTANCE;
        if (helper != null && helper.handleSelectTurn(yawDelta, pitchDelta)) {
            return;
        }

        LookEvent event = new LookEvent(yawDelta, pitchDelta);
        Client.getInstance().getEventManager().call(event);

        if (event.isCancelled()) {
            return;
        }

        if (RotationHandler.onMouseTurn(event.getCursorDeltaX(), event.getCursorDeltaY())) {
            return;
        }

        player.turn(event.getCursorDeltaX(), event.getCursorDeltaY());
    }
}