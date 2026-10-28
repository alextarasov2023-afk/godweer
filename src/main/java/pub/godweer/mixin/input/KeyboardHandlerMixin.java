package pub.godweer.mixin.input;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.event.Events;

/**
 * Create by daun kvass
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharacterTyped(long window, CharacterEvent characterEvent, CallbackInfo ci) {
        if (!characterEvent.isAllowedChatCharacter()) return;

        if (Client.getInstance().getEventManager().call(
                Events.CHARACTER_INPUT.set(window, characterEvent.codepoint())
        ).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if (Client.getInstance().getEventManager().call(
                Events.KEYBOARD_INPUT.set(window, keyEvent.key(), keyEvent.keycode(), action, keyEvent.modifiers())
        ).isCancelled()) {
            ci.cancel();
        }
    }
}