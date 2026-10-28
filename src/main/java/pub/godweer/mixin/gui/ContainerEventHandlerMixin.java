package pub.godweer.mixin.gui;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.Client;
import pub.godweer.event.Events;
import pub.godweer.event.list.ScreenKeyEvent;
import pub.godweer.event.list.ScreenMouseButtonEvent;

/**
 * Create by daun kvass
 */
@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    private void onKeyReleased(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Screen screen)) return;
        if (Client.getInstance().getEventManager().call(Events.SCREEN_KEY.set(screen, keyEvent, ScreenKeyEvent.Action.RELEASE)).isCancelled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Screen screen)) return;
        if (Client.getInstance().getEventManager().call(Events.SCREEN_MOUSE_BUTTON.set(screen, mouseButtonEvent, ScreenMouseButtonEvent.Action.CLICK, 0.0D, 0.0D)).isCancelled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent mouseButtonEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Screen screen)) return;
        if (Client.getInstance().getEventManager().call(Events.SCREEN_MOUSE_BUTTON.set(screen, mouseButtonEvent, ScreenMouseButtonEvent.Action.RELEASE, 0.0D, 0.0D)).isCancelled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent mouseButtonEvent, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Screen screen)) return;
        if (Client.getInstance().getEventManager().call(Events.SCREEN_MOUSE_BUTTON.set(screen, mouseButtonEvent, ScreenMouseButtonEvent.Action.DRAG, dragX, dragY)).isCancelled()) {
            cir.setReturnValue(true);
        }
    }
}