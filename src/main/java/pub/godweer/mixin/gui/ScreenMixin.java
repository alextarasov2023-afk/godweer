package pub.godweer.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.event.EventManager;
import pub.godweer.event.list.ScreenCloseEvent;

/**
 * Create by daun kvass
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final ScreenCloseEvent SCREEN_CLOSE_EVENT = new ScreenCloseEvent();
    @Shadow
    @Final
    protected Minecraft minecraft;
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void onClose(CallbackInfo ci) {
        Client.INSTANCE.eventManager.call(SCREEN_CLOSE_EVENT.set(this.minecraft, (Screen) (Object) this)).isCancelled();
    }

}
