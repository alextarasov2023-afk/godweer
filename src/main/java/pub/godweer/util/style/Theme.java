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

    // Плоская палитра ClickGUI: почти чёрный фон, панели чуть светлее,
    // карточки модулей — холодный серо-синий с обводкой. Без градиентов и стекла.
    public static final int BG_SIDEBAR = ColorUtil.rgba(5, 5, 5, 255);
    public static final int BG_CARD = ColorUtil.rgba(29, 39, 52, 255);
    public static final int BG_CARD_ACTIVE = ColorUtil.rgba(45, 61, 83, 255);
    public static final int BG_ELEMENT = ColorUtil.rgba(15, 15, 23, 255);
    public static final int DIVIDER_COLOR = ColorUtil.rgba(45, 61, 83, 255);
    public static final int BG_COLOR = ColorUtil.rgba(10, 11, 15, 245);
    public static final int TEXT_MAIN = ColorUtil.rgba(245, 245, 250, 255);
    public static final int TEXT_MUTED = ColorUtil.rgba(130, 130, 140, 255);

    /** Обводка панелей и карточек — единственный способ отделить элементы. */
    public static final int CARD_OUTLINE = ColorUtil.rgba(45, 61, 83, 255);

    public static int getAccentWithAlpha(int alpha) {
        return ColorUtil.withAlpha(accentColor, alpha);
    }
}