package pub.godweer.auxiliary.render.renders.font;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import pub.godweer.auxiliary.RenderExtend;
import pub.godweer.auxiliary.render.Render2DUtil;
import pub.godweer.auxiliary.render.renders.menuhelper.TextRenderState;

/**
 * Create by daun kvass
 */
public final class Fonts {
    public static final MsdfFont SF_MEDIUM = MsdfFont.load(Identifier.parse("godweer:fonts/sfmedium.json"));
    public static final MsdfFont ICONS = MsdfFont.load(Identifier.parse("godweer:fonts/icons.json"));
    public static final MsdfFont EMOJIS = MsdfFont.load(Identifier.parse("godweer:fonts/emojis.json"));
    public static final MsdfFont ENERGY = MsdfFont.load(Identifier.parse("godweer:fonts/energy.json"));
    public static final MsdfFont ICONS_NURIK = MsdfFont.load(Identifier.parse("godweer:fonts/icons_nurik.json"));

    public static void drawString(MsdfFont font, String text, float x, float y, float size, int color) {
        draw(font, text, x, y, size, color, TextAlign.LEFT);
    }

    public static void drawCenteredString(MsdfFont font, String text, float x, float y, float size, int color) {
        draw(font, text, x, y, size, color, TextAlign.CENTER);
    }

    public static void drawIcon(IconUse icon, float x, float y, float size, int color) {
        draw(icon.emoji ? EMOJIS : ICONS, icon.glyph, x, y, size, color, TextAlign.LEFT);
    }

    public static void drawCenteredIcon(IconUse icon, float x, float y, float size, int color) {
        draw(icon.emoji ? EMOJIS : ICONS, icon.glyph, x, y, size, color, TextAlign.CENTER);
    }

    public static float getIconWidth(IconUse icon, float size) {
        return (icon.emoji ? EMOJIS : ICONS).getWidth(icon.glyph, size);
    }

    public static float getIconHeight(IconUse icon, float size) {
        return (icon.emoji ? EMOJIS : ICONS).textHeight(size);
    }

    /**
     * Иконка ровно по центру бокса: cx/cy — центр. У drawCenteredIcon y — это верх
     * строки, поэтому без этой обёртки глиф уезжает вниз на половину своей высоты.
     */
    public static void drawIconCentered(IconUse icon, float cx, float cy, float size, int color) {
        drawCenteredIcon(icon, cx, cy - getIconHeight(icon, size) * 0.5F, size, color);
    }

    private static void draw(MsdfFont font, String text, float x, float y, float size, int color, TextAlign align) {
        if (Render2DUtil.hasEmptyScissor()) return;
        if (font == null || text == null || text.isEmpty()) return;
        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null) return;

        Render2DUtil.queue(new TextRenderState(
                extractor.pose(),
                font,
                x, y, size,
                font.shape(text),
                color,
                align,
                0.0F,
                Render2DUtil.currentScissor()
        ));
    }

    private Fonts() {}
}