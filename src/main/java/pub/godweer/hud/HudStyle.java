package pub.godweer.hud;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;

/**
 * Shared visual language for HUD cards:
 * deep glass blur, top specular rim + accent border,
 * and unified headers with right-aligned icon & subtle divider.
 */
public final class HudStyle {

    public static final int CARD_BG = ColorUtil.rgba(12, 13, 20, 224);
    public static final int CARD_BG_TOP = ColorUtil.rgba(22, 24, 36, 228);
    public static final int CARD_BG_BOTTOM = ColorUtil.rgba(10, 11, 17, 232);
    public static final int TEXT_PRIMARY = ColorUtil.rgba(242, 244, 252, 255);
    public static final int TEXT_SECONDARY = ColorUtil.rgba(162, 166, 186, 255);
    public static final int TEXT_DIM = ColorUtil.rgba(118, 122, 142, 255);

    private HudStyle() {
    }

    public static void drawCard(float x, float y, float w, float h, float radius) {
        drawCard(x, y, w, h, radius, 1.0F);
    }

    public static void drawCard(float x, float y, float w, float h, float radius, float alpha) {
        if (w <= 0.5F || h <= 0.5F || alpha <= 0.01F) return;
        int accent = Theme.getAccentColor();

        // Frosted blur base (no external drop shadow)
        Render2D.drawBlur(x, y, w, h, radius, 18.0F,
                ColorUtil.multiplyAlpha(CARD_BG, alpha), alpha);

        // Subtle vertical gradient sheen
        Render2D.drawGradientRound(x, y, w, h, radius,
                ColorUtil.withAlpha(accent, Math.round(18 * alpha)),
                ColorUtil.rgba(255, 255, 255, Math.round(7 * alpha)),
                ColorUtil.rgba(0, 0, 0, Math.round(35 * alpha)),
                ColorUtil.rgba(0, 0, 0, Math.round(25 * alpha)));

        // Subtle outer border
        Render2D.drawRoundedOutline(x, y, w, h, radius, 0.85F,
                ColorUtil.lerp(ColorUtil.rgba(255, 255, 255, Math.round(28 * alpha)),
                        ColorUtil.withAlpha(accent, Math.round(75 * alpha)), 0.35F));
    }

    /**
     * Draws a clean header: bold title on the left, accent icon on the right,
     * and a full-width subtle separator line.
     */
    public static void drawHeader(float x, float y, float w, float headerH,
                                  String title, IconUse rightIcon) {
        int accent = Theme.getAccentColor();
        float padX = 8.0F;
        float titleSize = 8.5F;
        float titleY = y + (headerH - Fonts.SF_MEDIUM.textHeight(titleSize)) / 2.0F + 0.5F;

        Fonts.drawString(Fonts.SF_MEDIUM, title, x + padX, titleY, titleSize, TEXT_PRIMARY);

        if (rightIcon != null) {
            // Высота подгоняется под реальную видимую часть глифа: у иконок из разных
            // шрифтов (в т.ч. эмодзи) при одном размере шрифта она разная.
            float iconH = 8.5F;
            float glyphW = Fonts.iconFittedWidth(rightIcon, iconH);
            Fonts.drawIconFitted(rightIcon, x + w - padX - glyphW, y + headerH / 2.0F,
                    iconH, ColorUtil.withAlpha(accent, 255));
        }

        // Full-width divider line
        float divY = y + headerH;
        Render2D.drawRect(x + 0.5F, divY, w - 1.0F, 0.75F, ColorUtil.rgba(255, 255, 255, 20));
    }

    /**
     * Status dot with soft outer halo (used in Staff list).
     */
    public static void drawStatusDot(float cx, float cy, float radius, int color, float alpha) {
        if (alpha <= 0.02F) return;
        Render2D.drawCircle(cx, cy, radius + 2.0F, ColorUtil.withAlpha(color, Math.round(45 * alpha)));
        Render2D.drawCircle(cx, cy, radius, ColorUtil.multiplyAlpha(color, alpha));
    }
}
