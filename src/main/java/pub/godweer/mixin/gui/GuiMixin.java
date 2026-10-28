package pub.godweer.mixin.gui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import pub.godweer.Client;
import pub.godweer.util.RenderExtend;
import pub.godweer.event.Events;

/**
 * Create by daun kvass
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onExtractFinalRenderState(DeltaTracker deltaTracker, boolean renderHud, boolean renderScreen, CallbackInfo ci, ProfilerFiller profilerFiller, int mouseX, int mouseY, GuiGraphicsExtractor guiGraphicsExtractor) {
        Client.getInstance().getEventManager().call(Events.FINAL_GUI_RENDER.set(this.minecraft, (Gui) (Object) this, guiGraphicsExtractor, deltaTracker, renderHud, renderScreen, mouseX, mouseY));}
    @Inject(method = "setOverlay", at = @At("HEAD"))
    private void onSetOverlay(net.minecraft.client.gui.screens.Overlay overlay, CallbackInfo ci) {if (overlay == null) {
        RenderExtend.sttime = -1L;}
    }


}