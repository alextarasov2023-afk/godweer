package pub.godweer.kitty.display.mainmenu.popup;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;

/**
 * Каркас всплывающих окон гуи: общие отступы, радиусы и цвета, чтобы окно конфигов
 * не отличалось от окна биндов. Плюс поле ввода — оно нужно и там, и там.
 */
public final class ModalShell {

    public record Window(float x, float y, float w, float h) {
    }

    public static final float CLOSE = 14.0F;
    public static final float LABEL = 8.5F;
    public static final float SMALL = 8.0F;

    private ModalShell() {
    }

    public static Window centered(float screenW, float screenH, float w, float h) {
        return new Window((screenW - w) / 2.0F, (screenH - h) / 2.0F, w, h);
    }

    public static float closeX(Window r) {
        return r.x() + r.w() - CLOSE - 12.0F;
    }

    public static float closeY(Window r) {
        return r.y() + 10.0F;
    }

    public static void drawWindow(Window r, String title, String hint, float alpha,
                                  double mouseX, double mouseY) {
        Render2D.drawBlur(r.x(), r.y(), r.w(), r.h(), 8.0F, ColorUtil.multiplyAlpha(0xFF101216, alpha), alpha);

        Fonts.drawString(Fonts.SF_MEDIUM, title, r.x() + 14.0F, r.y() + 12.0F, 10.0F,
                ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));
        if (hint != null && !hint.isEmpty()) {
            float hintW = Fonts.SF_MEDIUM.getWidth(hint, SMALL);
            Fonts.drawString(Fonts.SF_MEDIUM, hint, closeX(r) - 10.0F - hintW, r.y() + 14.0F, SMALL,
                    ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));
        }

        boolean closeHover = over(closeX(r), closeY(r), CLOSE, CLOSE, mouseX, mouseY);
        Fonts.drawString(Fonts.SF_MEDIUM, "Ч", closeX(r) + 2.0F, closeY(r) + 2.0F, 9.0F,
                ColorUtil.multiplyAlpha(closeHover ? 0xFFFF5555 : Theme.TEXT_MUTED, alpha));
    }

    public static boolean overClose(Window r, double mouseX, double mouseY) {
        return over(closeX(r), closeY(r), CLOSE, CLOSE, mouseX, mouseY);
    }

    public static boolean over(double x, double y, double w, double h,
                               double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /** Капча-кнопка в акцентном стиле клиента; primary — залитая акцентом, обычная — с обводкой. */
    public static void button(float x, float y, float w, float h, String label, boolean primary,
                              boolean hovered, float alpha) {
        int accent = Theme.getAccentColor();
        int bg = primary ? ColorUtil.withAlpha(accent, hovered ? 205 : 150)
                : (hovered ? ColorUtil.rgba(36, 39, 53, 255) : ColorUtil.rgba(25, 27, 36, 255));
        Render2D.drawRoundedRect(x, y, w, h, 4.0F, ColorUtil.multiplyAlpha(bg, alpha));
        if (!primary) {
            Render2D.drawRoundedOutline(x, y, w, h, 4.0F, 0.7F,
                    ColorUtil.withAlpha(accent, Math.round((hovered ? 120 : 70) * alpha)));
        }
        float tw = Fonts.SF_MEDIUM.getWidth(label, SMALL);
        Fonts.drawString(Fonts.SF_MEDIUM, label, x + (w - tw) / 2.0F,
                y + (h - Fonts.SF_MEDIUM.textHeight(SMALL)) / 2.0F + 0.35F, SMALL,
                ColorUtil.multiplyAlpha(primary ? ColorUtil.WHITE : Theme.TEXT_MAIN, alpha));
    }

    /** Квадратная кнопка-иконка; armed подсвечивает её красным — так работают «подтверди удаление». */
    public static void iconButton(float x, float y, float size, Icon icon, boolean hovered,
                                  boolean armed, float alpha) {
        int accent = Theme.getAccentColor();
        int color = armed ? 0xFFFF5555 : (icon.accent ? accent : Theme.TEXT_MUTED);
        int bg = armed ? ColorUtil.rgba(58, 22, 28, 255)
                : (hovered ? ColorUtil.withAlpha(accent, 60) : ColorUtil.rgba(25, 27, 36, 255));
        Render2D.drawRoundedRect(x, y, size, size, 3.5F, ColorUtil.multiplyAlpha(bg, alpha));
        Fonts.drawIconCentered(icon.use, x + size / 2.0F, y + size / 2.0F, size * 0.5F,
                ColorUtil.multiplyAlpha(color, alpha));
    }

    public enum Icon {
        LOAD(IconUse.CHECK, true),
        DELETE(IconUse.CROSS, false),
        ADD(IconUse.ADD, true),
        REMOVE(IconUse.REMOVEFRIEND, false),
        UP(IconUse.UP, false),
        DOWN(IconUse.DOWN, false),
        FOLDER(IconUse.HOME, false);

        final IconUse use;
        final boolean accent;

        Icon(IconUse use, boolean accent) {
            this.use = use;
            this.accent = accent;
        }
    }

    /**
     * Однострочное поле ввода. Enter и прочее решает хост: поле только принимает текст
     * и съедает Backspace, Escape отдаёт наружу, чтобы закрыть окно.
     */
    public static final class Field {
        private final StringBuilder text = new StringBuilder();
        private boolean focused = false;

        public String value() {
            return text.toString();
        }

        public boolean isEmpty() {
            return text.length() == 0;
        }

        public boolean isFocused() {
            return focused;
        }

        public void set(String value) {
            text.setLength(0);
            if (value != null) text.append(value);
        }

        public void clear() {
            text.setLength(0);
        }

        public void blur() {
            focused = false;
        }

        public void render(float x, float y, float w, float h, String placeholder, float alpha) {
            Render2D.drawRoundedRect(x, y, w, h, 4.0F,
                    ColorUtil.multiplyAlpha(focused ? 0xCC12141D : 0x8812141D, alpha));
            if (focused) {
                Render2D.drawRoundedOutline(x, y, w, h, 4.0F, 0.8F,
                        ColorUtil.withAlpha(Theme.getAccentColor(), Math.round(130 * alpha)));
            }

            String shown;
            int color;
            if (text.length() == 0) {
                shown = focused ? "|" : placeholder;
                color = Theme.TEXT_MUTED;
            } else {
                shown = text + (focused ? "|" : "");
                color = Theme.TEXT_MAIN;
            }
            Fonts.drawString(Fonts.SF_MEDIUM, shown, x + 8.0F,
                    y + (h - Fonts.SF_MEDIUM.textHeight(LABEL)) / 2.0F + 0.35F, LABEL,
                    ColorUtil.multiplyAlpha(color, alpha));
        }

        public boolean mouseClicked(double mouseX, double mouseY, double x, double y, double w, double h) {
            focused = over(x, y, w, h, mouseX, mouseY);
            return focused;
        }

        public void charTyped(int codePoint) {
            if (!focused || text.length() >= 32 || codePoint < 32 || codePoint == 127) return;
            text.appendCodePoint(codePoint);
        }

        public boolean keyPressed(int keyCode) {
            if (!focused) return false;
            if (keyCode == InputConstants.KEY_BACKSPACE && text.length() > 0) {
                int start = text.length() - Character.charCount(text.codePointBefore(text.length()));
                text.delete(start, text.length());
                return true;
            }
            return keyCode != InputConstants.KEY_ESCAPE;
        }
    }
}
