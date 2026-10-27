package pub.godweer.enjoy.mixin.gui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.auxiliary.RenderExtend;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.auxiliary.render.Render2DUtil;

/**
 * Create by daun kvass
 */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Unique
    private static final Render2DEvent EVENT = new Render2DEvent();
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        RenderExtend.enter2D(null, guiGraphicsExtractor, deltaTracker);
        try {Render2DUtil.beginFrame();Client.getInstance().getEventManager().call(EVENT.set(this.minecraft, null, guiGraphicsExtractor, deltaTracker));Render2DUtil.flush();
        } finally {
            RenderExtend.exit2D();}
    }
}