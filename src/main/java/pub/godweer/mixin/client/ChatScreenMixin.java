package pub.godweer.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.Client;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void onChatInput(String message, boolean addToRecentChat, CallbackInfo ci) {
        if (Client.INSTANCE.commandManager.execute(message)) {
            if (addToRecentChat) {
                mc.gui.hud.getChat().addRecentChat(message);
            }
            Minecraft.getInstance().gui.setScreen(null);
            ci.cancel();
        }
    }

}