package pub.godweer.util.style;

import lombok.Getter;
import lombok.Setter;

/**
 * Create by daun kvass
 */
public final class Theme {
    @Getter @Setter
    private static int accentColor = ColorUtil.rgba(255, 170, 45, 255);

    @Getter @Setter
    private static String backgroundMode = "Blur";

    @Getter @Setter
    private static int bgColor1 = ColorUtil.rgba(18, 18, 24, 255);

    @Getter @Setter
    private static int bgColor2 = ColorUtil.rgba(12, 12, 16, 255);

    @Getter @Setter
    private static float panelAlpha = 0.90F;

    public static final int BG_SIDEBAR = ColorUtil.rgba(13, 13, 16, 255);
    public static final int BG_CARD = ColorUtil.rgba(20, 20, 26, 255);
    public static final int BG_CARD_ACTIVE = ColorUtil.rgba(24, 26, 34, 255);
    public static final int BG_ELEMENT = ColorUtil.rgba(26, 26, 33, 255);
    public static final int DIVIDER_COLOR = ColorUtil.rgba(255, 255, 255, 25);
    public static final int BG_COLOR = ColorUtil.rgba(16, 16, 20, 195);
    public static final int TEXT_MAIN = ColorUtil.rgba(245, 245, 250, 255);
    public static final int TEXT_MUTED = ColorUtil.rgba(130, 130, 140, 255);

    public static int getAccentWithAlpha(int alpha) {
        return ColorUtil.withAlpha(accentColor, alpha);
    }
}