package pub.godweer.ui.modal;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.game.KeyUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;

import static pub.godweer.IMinecraft.mc;

/**
 * Каркас всплывающих окон гуи: общие отступы, радиусы и цвета, чтобы окно конфигов
 * не отличалось от окна биндов. Плюс поле ввода — оно нужно и там, и там.
 */
public final class ModalShell {

    /**
     * Символьные события в 26.3 приходят из SDL, а vanilla включает их только экрану
     * с EditBox. Наш оверлей экраном не является, поэтому символ может не прийти вовсе:
     * пока система хоть раз сама принесла символ, синтез из клавиш не нужен, а если не
     * приносит — текст берётся прямо из кода клавиши. Эхо synthesised-клавиши гасится,
     * чтобы буква не появлялась дважды там, где оба пути работают.
     */
    private static boolean systemChars = false;
    private static boolean synthEcho = false;

    /** @return false, если это отклик на уже синтезированный символ. */
    public static boolean acceptSystemChar(int codePoint) {
        if (synthEcho) {
            synthEcho = false;
            systemChars = true;
            return false;
        }
        systemChars = true;
        return true;
    }

    /** @return символ клавиши, если настоящих символьных событий так и не дождались. */
    public static int synthChar(int keyCode) {
        if (systemChars) return 0;
        int codePoint = KeyUtil.typedChar(keyCode);
        if (codePoint != 0) synthEcho = true;
        return codePoint;
    }

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
     * Однострочное поле ввода: каретка, выделение, вставка из буфера. Enter и прочее
     * решает хост — поле только хранит текст и понимает клавиши редактирования.
     */
    public static final class Field {
        private static final float PAD_X = 8.0F;

        private final StringBuilder text = new StringBuilder();
        private final int maxLength;
        private int caret;
        private int selAnchor = -1;
        private int view;
        private boolean focused = false;

        public Field() {
            this(32);
        }

        public Field(int maxLength) {
            this.maxLength = maxLength;
        }

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
            if (value != null) append(value);
            selAnchor = -1;
            caret = text.length();
            view = 0;
        }

        public void clear() {
            text.setLength(0);
            selAnchor = -1;
            caret = 0;
            view = 0;
        }

        public void blur() {
            focused = false;
            selAnchor = -1;
        }

        public void focus() {
            focused = true;
        }

        public void render(float x, float y, float w, float h, String placeholder, float alpha) {
            Render2D.drawRoundedRect(x, y, w, h, 4.0F,
                    ColorUtil.multiplyAlpha(focused ? 0xCC12141D : 0x8812141D, alpha));
            if (focused) {
                Render2D.drawRoundedOutline(x, y, w, h, 4.0F, 0.8F,
                        ColorUtil.withAlpha(Theme.getAccentColor(), Math.round(130 * alpha)));
            }

            if (focused) {
                setArea(x, y + (h - Fonts.SF_MEDIUM.textHeight(LABEL)) / 2.0F, w, h);
            }

            float baseline = y + (h - Fonts.SF_MEDIUM.textHeight(LABEL)) / 2.0F + 0.35F;
            float avail = Math.max(1.0F, w - PAD_X * 2.0F);

            if (text.length() == 0) {
                if (!focused) {
                    Fonts.drawString(Fonts.SF_MEDIUM, placeholder, x + PAD_X, baseline, LABEL,
                            ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));
                    return;
                }
                drawCaret(x + PAD_X, y, h, alpha);
                return;
            }

            view = Math.min(view, caret);
            while (view < caret && tooWide(view, caret, avail)) view++;
            while (view > 0 && tooWide(view, caret, avail)) view--;

            int to = visibleEnd(view, avail);
            String shown = text.substring(view, to);

            if (selAnchor >= 0) {
                int selStart = Math.min(selAnchor, caret);
                int selEnd = Math.max(selAnchor, caret);
                int from = Math.max(view, Math.min(to, selStart));
                int end = Math.max(view, Math.min(to, selEnd));
                if (end > from) {
                    float sx = x + PAD_X + Fonts.SF_MEDIUM.getWidth(text.substring(view, from), LABEL);
                    float sw = Fonts.SF_MEDIUM.getWidth(text.substring(from, end), LABEL);
                    Render2D.drawRect(sx, y + 3.0F, sw, h - 6.0F,
                            ColorUtil.withAlpha(Theme.getAccentColor(), Math.round(70 * alpha)));
                }
            }

            Fonts.drawString(Fonts.SF_MEDIUM, shown, x + PAD_X, baseline, LABEL,
                    ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));
            if (focused) {
                drawCaret(x + PAD_X + Fonts.SF_MEDIUM.getWidth(text.substring(view, caret), LABEL), y, h, alpha);
            }
        }

        private boolean tooWide(int from, int to, float avail) {
            return Fonts.SF_MEDIUM.getWidth(text.substring(from, to), LABEL) > avail;
        }

        /** Последний индекс (исключительно), который ещё влезает в поле от позиции from. */
        private int visibleEnd(int from, float avail) {
            int end = from;
            while (end < text.length() && !tooWide(from, end + 1, avail)) end++;
            return end;
        }

        private void drawCaret(float x, float y, float h, float alpha) {
            if ((System.currentTimeMillis() / 450L) % 2L != 0L) return;
            Render2D.drawRect(x, y + 3.0F, 1.0F, h - 6.0F,
                    ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));
        }

        /** Прямоугольник в экранных координатах — под ним система рисует свои кандидаты IME. */
        private void setArea(float x, float y, float w, float h) {
            if (mc == null) return;
            mc.textInputManager().setTextInputArea((int) x, (int) y, (int) (x + w), (int) (y + h));
        }

        public boolean mouseClicked(double mouseX, double mouseY, double x, double y, double w, double h) {
            if (!over(x, y, w, h, mouseX, mouseY)) {
                blur();
                return false;
            }
            focus();
            caret = caretAt(mouseX, x, w);
            selAnchor = -1;
            return true;
        }

        private int caretAt(double mouseX, double x, double w) {
            float avail = (float) Math.max(1.0F, w - PAD_X * 2.0F);
            int to = visibleEnd(view, avail);
            if (mouseX - (x + PAD_X) >= Fonts.SF_MEDIUM.getWidth(text.substring(view, to), LABEL)) {
                return to;
            }
            for (int i = view; i < to; i++) {
                if (mouseX - (x + PAD_X) < Fonts.SF_MEDIUM.getWidth(text.substring(view, i + 1), LABEL)) {
                    return i;
                }
            }
            return view;
        }

        public void charTyped(int codePoint) {
            if (!focused || codePoint < 32 || codePoint == 127) return;
            insert(String.valueOf(Character.toChars(codePoint)));
        }

        /** @return true, если поле съело клавишу; остальное решает хост. */
        public boolean keyPressed(int keyCode) {
            if (!focused) return false;

            boolean ctrl = KeyUtil.isKeyDown(InputConstants.KEY_LCONTROL)
                    || KeyUtil.isKeyDown(InputConstants.KEY_RCONTROL);
            boolean shift = KeyUtil.isKeyDown(InputConstants.KEY_LSHIFT)
                    || KeyUtil.isKeyDown(InputConstants.KEY_RSHIFT);

            if (ctrl) {
                switch (keyCode) {
                    case InputConstants.KEY_A -> {
                        selAnchor = 0;
                        caret = text.length();
                        return true;
                    }
                    case InputConstants.KEY_C -> {
                        copy(selection());
                        return true;
                    }
                    case InputConstants.KEY_V -> {
                        String paste = mc == null ? "" : mc.keyboardHandler.getClipboard();
                        if (paste != null && !paste.isEmpty()) insert(filter(paste));
                        return true;
                    }
                    case InputConstants.KEY_X -> {
                        String sel = selection();
                        if (!sel.isEmpty()) {
                            copy(sel);
                            deleteSelection();
                        }
                        return true;
                    }
                    case InputConstants.KEY_BACKSPACE -> {
                        if (!deleteSelection()) deleteWord();
                        return true;
                    }
                    default -> {
                        // остальные Ctrl-сочетания остаются за хостом
                    }
                }
            }

            switch (keyCode) {
                case InputConstants.KEY_LEFT -> {
                    move(Math.max(0, caret - 1), shift);
                    return true;
                }
                case InputConstants.KEY_RIGHT -> {
                    move(Math.min(text.length(), caret + 1), shift);
                    return true;
                }
                case InputConstants.KEY_HOME -> {
                    move(0, shift);
                    return true;
                }
                case InputConstants.KEY_END -> {
                    move(text.length(), shift);
                    return true;
                }
                case InputConstants.KEY_BACKSPACE -> {
                    if (!deleteSelection()) deleteWord();
                    return true;
                }
                case InputConstants.KEY_DELETE -> {
                    if (deleteSelection()) return true;
                    if (caret < text.length()) {
                        int next = caret + Character.charCount(text.codePointAt(caret));
                        text.delete(caret, next);
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private void move(int to, boolean shift) {
            if (shift) {
                if (selAnchor < 0) selAnchor = caret;
            } else {
                selAnchor = -1;
            }
            caret = Math.max(0, Math.min(text.length(), to));
        }

        private String selection() {
            if (selAnchor < 0 || selAnchor == caret) return "";
            return text.substring(Math.min(selAnchor, caret), Math.max(selAnchor, caret));
        }

        private boolean deleteSelection() {
            String sel = selection();
            if (sel.isEmpty()) return false;
            caret = Math.min(selAnchor, caret);
            text.delete(caret, caret + sel.length());
            selAnchor = -1;
            return true;
        }

        private void deleteWord() {
            if (deleteSelection()) return;
            int from = caret;
            while (from > 0 && Character.isWhitespace(text.charAt(from - 1))) from--;
            while (from > 0 && !Character.isWhitespace(text.charAt(from - 1))) from--;
            text.delete(from, caret);
            caret = from;
        }

        private void insert(String value) {
            String cleaned = filter(value);
            if (text.length() + cleaned.length() > maxLength) {
                cleaned = cleaned.substring(0, Math.max(0, maxLength - text.length()));
            }
            if (cleaned.isEmpty()) return;
            deleteSelection();
            text.insert(caret, cleaned);
            caret += cleaned.length();
            selAnchor = -1;
        }

        private String filter(String value) {
            StringBuilder out = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 32 || c == 127) continue;
                if (c == '\n' || c == '\r') continue;
                out.append(c);
            }
            return out.toString();
        }

        private void append(String value) {
            text.append(filter(value));
        }

        private void copy(String value) {
            if (mc == null || value.isEmpty()) return;
            mc.keyboardHandler.setClipboard(value);
        }
    }
}
