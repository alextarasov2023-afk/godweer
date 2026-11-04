package pub.godweer.hud;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;

/**
 * Shared visual language for HUD elements, matching the reference screenshot:
 * every metric, header and row is its own small near-black pill — no glass, no
 * blur, no gradient, no outline, no shared card behind a list. Text is white /
 * grey, icons are tiny and the accent color only touches progress and glyphs.
 */
public final class HudStyle {

    /** Pill fill: near-black and almost opaque, so the world barely shows through. */
    public static final int CARD_BG = ColorUtil.rgba(6, 7, 12, 232);

    /** Pill geometry, shared by every HUD element so rows line up across panels. */
    public static final float PILL_H = 16.0F;
    public static final float PILL_R = 4.5F;
    public static final float PILL_GAP = 4.0F;
    public static final float PILL_PAD = 6.0F;
    /** Visible height every icon is fitted to, so glyphs of different size read as one line. */
    public static final float ICON = 8.0F;

    /** Subtle row / slot fills used inside the cards. */
    public static final int ROW_BG = ColorUtil.rgba(255, 255, 255, 7);
    public static final int ROW_BG_ACTIVE = ColorUtil.rgba(255, 255, 255, 14);
    public static final int PILL_BG = ColorUtil.rgba(255, 255, 255, 9);
    public static final int TRACK = ColorUtil.rgba(255, 255, 255, 22);
    /** Thin vertical stroke between segments of one pill. */
    public static final int DIVIDER = ColorUtil.rgba(255, 255, 255, 34);

    public static final int TEXT_PRIMARY = ColorUtil.rgba(240, 243, 247, 255);
    public static final int TEXT_SECONDARY = ColorUtil.rgba(150, 156, 168, 255);
    public static final int TEXT_DIM = ColorUtil.rgba(112, 118, 130, 255);

    /** Health bar from the reference: flat yellow body, lavender absorption tail. */
    public static final int HP_FILL = ColorUtil.rgba(252, 213, 0, 255);
    public static final int HP_ABSORB = ColorUtil.rgba(168, 160, 232, 255);

    private HudStyle() {
    }

    public static void drawCard(float x, float y, float w, float h, float radius) {
        drawCard(x, y, w, h, radius, 1.0F);
    }

    public static void drawCard(float x, float y, float w, float h, float radius, float alpha) {
        if (w <= 0.5F || h <= 0.5F || alpha <= 0.01F) return;
        // Flat translucent fill: no outline, no blur, no gradient.
        Render2D.drawRoundedRect(x, y, w, h, radius, ColorUtil.multiplyAlpha(CARD_BG, alpha));
    }

    /** Standard pill: the one shape every HUD row, metric and header uses. */
    public static void drawPill(float x, float y, float w) {
        drawCard(x, y, w, PILL_H, PILL_R);
    }

    /** Thin vertical stroke between pill segments. Returns the x after it. */
    public static final float DIVIDER_W = 1.0F;

    public static float drawDivider(float x, float cy) {
        Render2D.drawRoundedRect(x, cy - 4.0F, DIVIDER_W, 8.0F, 0.5F, DIVIDER);
        return x + DIVIDER_W;
    }

    /** Width of `icon | text` content, the shape every pill segment is built from. */
    public static float segmentWidth(IconUse icon, String text, float size) {
        return Fonts.iconFittedWidth(icon, ICON) + 4.0F + DIVIDER_W + 4.0F
                + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /** Draws `icon | text` at x, vertically centred on cy. Returns the x after it. */
    public static float drawSegment(float x, float cy, IconUse icon, String text, float size,
                                    int iconColor, int textColor) {
        Fonts.drawIconFitted(icon, x, cy, ICON, iconColor);
        x += Fonts.iconFittedWidth(icon, ICON) + 4.0F;
        x = drawDivider(x, cy);
        Fonts.drawString(Fonts.SF_MEDIUM, text, x + 4.0F,
                cy - Fonts.SF_MEDIUM.textHeight(size) / 2.0F + 0.35F, size, textColor);
        return x + 4.0F + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /**
     * Compact label row: muted title on the left, tiny accent icon on the right.
     * No divider, no header pill. Returns the y where card content starts.
     */
    public static float drawLabel(float x, float y, float w, float labelH,
                                  String title, IconUse rightIcon) {
        float padX = 8.0F;
        float size = 7.0F;
        float ty = y + (labelH - Fonts.SF_MEDIUM.textHeight(size)) / 2.0F + 0.3F;

        float titleW = w - padX * 2.0F;
        if (rightIcon != null) titleW -= 15.0F;
        String shownTitle = ServerGui.ellipsize(title, Math.max(0.0F, titleW), size);
        Fonts.drawString(Fonts.SF_MEDIUM, shownTitle, x + padX, ty, size, TEXT_SECONDARY);

        if (rightIcon != null) {
            float glyphW = Fonts.iconFittedWidth(rightIcon, size);
            Fonts.drawIconFitted(rightIcon, x + w - padX - glyphW, y + labelH / 2.0F, size,
                    ColorUtil.withAlpha(Theme.getAccentColor(), 235));
        }
        return y + labelH;
    }

    /**
     * Flat status dot with a thin ring (no soft glow / halo).
     */
    public static void drawStatusDot(float cx, float cy, float radius, int color, float alpha) {
        if (alpha <= 0.02F) return;
        Render2D.drawCircle(cx, cy, radius, ColorUtil.multiplyAlpha(color, alpha));
        Render2D.drawCircleOutline(cx, cy, radius + 1.0F, 0.8F,
                ColorUtil.withAlpha(color, Math.round(70 * alpha)));
    }
}
