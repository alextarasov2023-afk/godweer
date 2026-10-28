package pub.godweer.util.render;

import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import pub.godweer.util.RenderExtend;
import pub.godweer.util.render.menu.BlurRectRenderState;
import pub.godweer.util.render.menu.CircleArcRenderState;
import pub.godweer.util.render.MenuBacks;
import pub.godweer.util.render.menu.RectRenderState;
import pub.godweer.util.render.menu.TextureRenderState;

/**
 * Create by daun kvass
 */
public class Render2D {

    public static void pushScissor(float x, float y, float width, float height) {
        Render2DUtil.pushScissor(x, y, width, height);
    }

    public static void popScissor() {
        Render2DUtil.popScissor();
    }

    public static void drawRect(float x, float y, float width, float height, int color) {
        drawRoundedRect(x, y, width, height, 0.0F, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        drawRoundedRectComplex(x, y, width, height, radius, radius, radius, radius, color, color, color, color, 0.0F, 0, 0.0F, 0, false);
    }

    public static void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, int borderColor) {
        drawRoundedRectComplex(x, y, width, height, radius, radius, radius, radius, 0, 0, 0, 0, thickness, borderColor, 0.0F, 0, false);
    }

    public static void drawRoundedRectWithOutline(float x, float y, float width, float height, float radius, int fillColor, float thickness, int borderColor) {
        drawRoundedRectComplex(x, y, width, height, radius, radius, radius, radius, fillColor, fillColor, fillColor, fillColor, thickness, borderColor, 0.0F, 0, false);
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius, int cTL, int cTR, int cBR, int cBL) {
        drawRoundedRectComplex(x, y, width, height, radius, radius, radius, radius, cTL, cTR, cBR, cBL, 0.0F, 0, 0.0F, 0, false);
    }

    public static void drawShadow(float x, float y, float width, float height, float radius, float blur, int shadowColor) {
        drawRoundedRectComplex(x, y, width, height, radius, radius, radius, radius, 0, 0, 0, 0, 0.0F, 0, blur, shadowColor, true);
    }


    public static void drawCircle(float cx, float cy, float radius, int color) {
        drawArc(cx, cy, radius, radius, 0.0F, 360.0F, color);
    }


    public static void drawCircleOutline(float cx, float cy, float radius, float thickness, int color) {
        drawArc(cx, cy, radius, thickness, 0.0F, 360.0F, color);
    }


    public static void drawArc(float cx, float cy, float radius, float thickness, float startAngleDeg, float sweepAngleDeg, int color) {
        if (Render2DUtil.hasEmptyScissor() || Math.abs(sweepAngleDeg) <= 0.01F || radius <= 0.0F) return;
        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null) return;

        Render2DUtil.queue(new CircleArcRenderState(
                extractor.pose(),
                cx, cy, radius, thickness,
                startAngleDeg, sweepAngleDeg,
                color,
                Render2DUtil.currentScissor()
        ));
    }

    public static void drawBlur(float x, float y, float width, float height, float radius, int tintColor, float opacity) {
        drawBlur(x, y, width, height, radius, 16.0F, tintColor, opacity);
    }

    public static void drawBlur(float x, float y, float width, float height, float radius, float blurRadius, int tintColor, float opacity) {
        if (Render2DUtil.hasEmptyScissor()) return;
        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null) return;

        var backdropView = MenuBacks.acquireView();
        if (backdropView != null) {
            int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());

            int rawAlpha = (tintColor >>> 24) & 0xFF;
            int tintAlpha = Math.round(rawAlpha * 0.8F);
            int safeTint = (tintColor & 0x00FFFFFF) | (tintAlpha << 24);

            Render2DUtil.queue(new BlurRectRenderState(
                    extractor.pose(),
                    x, y, x + width, y + height,
                    safeTint,
                    radius,
                    blurRadius * guiScale,
                    opacity,
                    backdropView,
                    Render2DUtil.currentScissor()
            ));
        }
    }

    public static void drawTexture(String path, float x, float y, float width, float height) {
        drawTexture(path, x, y, width, height, 0.0F, 0xFFFFFFFF);
    }

    public static void drawTexture(String path, float x, float y, float width, float height, float radius, int color) {
        Identifier id = Identifier.fromNamespaceAndPath("godweer", path);
        drawTexture(id, x, y, width, height, radius, color);
    }

    public static void drawTexture(Identifier texture, float x, float y, float width, float height) {
        drawTexture(texture, x, y, width, height, 0.0F, 0xFFFFFFFF);
    }

    public static void drawTexture(Identifier texture, float x, float y, float width, float height, int color) {
        drawTexture(texture, x, y, width, height, 0.0F, color);
    }

    public static void drawTexture(Identifier texture, float x, float y, float width, float height, float radius, int color) {
        drawTexture(texture, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, radius, color, FilterMode.LINEAR);
    }

    public static void drawTexture(Identifier texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, float radius, int color, FilterMode filterMode) {
        if (Render2DUtil.hasEmptyScissor()) return;
        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null || texture == null) return;

        Render2DUtil.queue(new TextureRenderState(
                extractor.pose(),
                texture,
                x, y, width, height,
                u0, v0, u1, v1,
                color, radius, filterMode,
                Render2DUtil.currentScissor()
        ));
    }

    public static void drawHead(Identifier skin, float x, float y, float size) {
        drawHead(skin, x, y, size, 0.0F, 0xFFFFFFFF);
    }

    public static void drawHead(Identifier skin, float x, float y, float size, float radius) {
        drawHead(skin, x, y, size, radius, 0xFFFFFFFF);
    }

    public static void drawHead(Identifier skin, float x, float y, float size, float radius, int tint) {
        if (skin == null || size <= 0) return;

        drawTexture(skin, x, y, size, size,
                8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F,
                radius, tint, FilterMode.NEAREST);

        drawTexture(skin, x, y, size, size,
                40.0F / 64.0F, 8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F,
                radius, tint, FilterMode.NEAREST);
    }

    public static void drawRoundedRectComplex(
            float x, float y, float w, float h,
            float rTL, float rTR, float rBR, float rBL,
            int cTL, int cTR, int cBR, int cBL,
            float borderThickness, int borderColor,
            float shadowBlur, int shadowColor, boolean isShadow
    ) {
        if (Render2DUtil.hasEmptyScissor()) return;
        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null) return;

        Render2DUtil.queue(new RectRenderState(
                extractor.pose(),
                x, y, x + w, y + h,
                cTL, cBL, cBR, cTR,
                rTL, rTR, rBR, rBL,
                borderThickness, borderColor,
                shadowBlur, shadowColor, isShadow,
                Render2DUtil.currentScissor()
        ));
    }
}