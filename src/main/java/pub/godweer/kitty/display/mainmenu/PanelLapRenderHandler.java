package pub.godweer.kitty.display.mainmenu;

import net.minecraft.client.Minecraft;
import pub.godweer.auxiliary.RenderExtend;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.MenuRenderEvent;
import pub.godweer.auxiliary.render.Render2DUtil;

/**
 * Create by daun kvass
 */

public final class PanelLapRenderHandler {
    @EventTarget
    public void renderMenu(MenuRenderEvent event) {
        Minecraft minecraft = event.getClient();
        RenderExtend.enter2D(event.getGui(), event.getGuiGraphicsExtractor(), event.getDeltaTracker());
        try {int screenWidth = minecraft.getWindow().getGuiScaledWidth();int screenHeight = minecraft.getWindow().getGuiScaledHeight();Render2DUtil.beginFrame();
            PanelRefractions.render(minecraft, event.getGuiGraphicsExtractor(), screenWidth, screenHeight);Render2DUtil.flush();
        } finally {
            RenderExtend.exit2D();}
    }
}