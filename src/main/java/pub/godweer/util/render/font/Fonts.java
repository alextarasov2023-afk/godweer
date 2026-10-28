package pub.godweer.util.render.font;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import pub.godweer.util.RenderExtend;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.menu.TextRenderState;

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
        return font(icon).getWidth(icon.glyph, size);
    }

    public static float getIconHeight(IconUse icon, float size) {
        return font(icon).textHeight(size);
    }

    /**
     * Иконка ровно по центру бокса: cx/cy — центр. У drawCenteredIcon y — это верх
     * строки, поэтому без этой обёртки глиф уезжает вниз на половину своей высоты.
     */
    public static void drawIconCentered(IconUse icon, float cx, float cy, float size, int color) {
        drawCenteredIcon(icon, cx, cy - getIconHeight(icon, size) * 0.5F, size, color);
    }

    /**
     * Глифы иконочного шрифта занимают разную часть эм-бокса: у PING это 34x36,
     * у BPS — 47x37, у INFO — 17x17. Поэтому размер всегда считаем от реальной
     * видимой высоты (planeBounds), а не от размера шрифта.
     */
    public static float iconSizeForHeight(IconUse icon, float height) {
        MsdfFont.Bounds b = iconBounds(icon);
        float em = b == null ? 0.0F : b.top() - b.bottom();
        return em > 0.01F ? height / em : height;
    }

    /** Ширина иконки, подогнанной под видимую высоту height. */
    public static float iconFittedWidth(IconUse icon, float height) {
        MsdfFont font = font(icon);
        MsdfFont.Bounds b = iconBounds(icon);
        float size = iconSizeForHeight(icon, height);
        if (b == null) return font.getWidth(icon.glyph, size);
        return (b.right() - b.left()) * size;
    }

    /** Иконка заданной видимой высоты: left — левый край, cy — центр по вертикали. */
    public static void drawIconFitted(IconUse icon, float left, float cy, float height, int color) {
        MsdfFont font = font(icon);
        MsdfFont.Bounds b = iconBounds(icon);
        float size = iconSizeForHeight(icon, height);
        if (b == null) {
            draw(font, icon.glyph, left, cy - font.textHeight(size) * 0.5F, size, color, TextAlign.LEFT);
            return;
        }
        draw(font, icon.glyph,
                left - b.left() * size,
                cy - font.ascender(size) + (b.top() + b.bottom()) * 0.5F * size,
                size, color, TextAlign.LEFT);
    }

    /** То же, но с центрированием и по горизонтали. */
    public static void drawIconFittedCentered(IconUse icon, float cx, float cy, float height, int color) {
        drawIconFitted(icon, cx - iconFittedWidth(icon, height) * 0.5F, cy, height, color);
    }

    private static MsdfFont font(IconUse icon) {
        return icon.emoji ? EMOJIS : ICONS;
    }

    private static MsdfFont.Bounds iconBounds(IconUse icon) {
        return font(icon).glyph(icon.glyph.codePointAt(0)).planeBounds();
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