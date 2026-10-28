package pub.godweer.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.ui.clickgui.PanelRefractions;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Тосты в правом нижнем углу, стек растёт снизу вверх: включение/выключение модулей
 * и использование предметов. Карточки — те же, что у остальных элементов HUD (HudStyle),
 * список один и рисуется и в игре, и поверх клик-гуи.
 */
public final class Notifications implements IMinecraft {

    private static final Notifications INSTANCE = new Notifications();

    private static final int MAX_TOASTS = 4;
    private static final long LIFE_MS = 3400L;
    private static final long FADE_MS = 420L;

    private static final float W = 178.0F;
    private static final float H = 28.0F;
    private static final float GAP = 6.0F;
    private static final float MARGIN = 8.0F;
    private static final float RADIUS = 8.0F;
    private static final float SLOT = 16.0F;
    private static final float TEXT = 7.5F;

    private static final int ON = ColorUtil.rgba(96, 226, 133, 255);
    private static final int OFF = ColorUtil.rgba(236, 98, 106, 255);

    private static final class Toast {
        final String text;
        final int accent;
        final IconUse icon;
        final long born = System.currentTimeMillis();
        float anim;

        Toast(String text, int accent, IconUse icon) {
            this.text = text;
            this.accent = accent;
            this.icon = icon;
        }
    }

    private final ArrayDeque<Toast> toasts = new ArrayDeque<>();

    /** На время загрузки конфига: иначе при старте высыпает все модули разом. */
    private boolean muted = true;

    private Notifications() {
    }

    public static Notifications getInstance() {
        return INSTANCE;
    }

    public static void setMuted(boolean muted) {
        INSTANCE.muted = muted;
    }

    public static void push(String text, int accent, IconUse icon) {
        INSTANCE.add(text, accent, icon);
    }

    public static void push(String text) {
        INSTANCE.add(text, Theme.getAccentColor(), IconUse.INFO);
    }

    /** Переключение модуля: иконка и цвет по состоянию. */
    public static void moduleToggle(String moduleName, boolean enabled) {
        INSTANCE.add(moduleName + (enabled ? " включён" : " выключен"),
                enabled ? ON : OFF, enabled ? IconUse.ONMODULE : IconUse.OFFMODULE);
    }

    /** Расходник по бинду: остаток считается уже после использования. */
    public static void itemUse(String itemName, int remaining) {
        INSTANCE.add(itemName + " · осталось " + remaining, Theme.getAccentColor(), IconUse.MISC);
    }

    private synchronized void add(String text, int accent, IconUse icon) {
        if (muted || text == null || text.isEmpty()) return;
        toasts.addLast(new Toast(text, accent, icon));
        while (toasts.size() > MAX_TOASTS) {
            toasts.removeFirst();
        }
    }

    @EventTarget(priority = 900)
    public void onRender2D(Render2DEvent event) {
        if (PanelRefractions.isOpen()) return;
        draw(event.getGuiGraphicsExtractor(),
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    public void draw(GuiGraphicsExtractor extractor, float screenW, float screenH) {
        if (extractor == null) return;
        long now = System.currentTimeMillis();
        float right = screenW - MARGIN;

        synchronized (this) {
            Iterator<Toast> it = toasts.iterator();
            while (it.hasNext()) {
                Toast t = it.next();
                long age = now - t.born;
                if (age > LIFE_MS) {
                    it.remove();
                    continue;
                }
                float out = age > LIFE_MS - FADE_MS ? (LIFE_MS - age) / (float) FADE_MS : 1.0F;
                t.anim += (out - t.anim) * 0.28F;
            }

            int index = 0;
            // Стек растёт снизу вверх: свежий тост прижат к нижнему краю, старые уходят выше.
            for (Iterator<Toast> rit = toasts.descendingIterator(); rit.hasNext();) {
                Toast t = rit.next();
                float a = t.anim;
                if (a <= 0.02F) continue;
                float y = screenH - MARGIN - H - index * (H + GAP);
                float x = right - W + (1.0F - Math.min(1.0F, a)) * 22.0F;

                HudStyle.drawCard(x, y, W, H, RADIUS, a);

                float slotX = x + 6.0F;
                float slotY = y + (H - SLOT) / 2.0F;
                Render2D.drawRoundedRectWithOutline(slotX, slotY, SLOT, SLOT, 4.0F,
                        ColorUtil.withAlpha(t.accent, Math.round(34 * a)),
                        0.65F, ColorUtil.withAlpha(t.accent, Math.round(95 * a)));

                Fonts.drawIconFittedCentered(t.icon, slotX + SLOT / 2.0F, slotY + SLOT / 2.0F,
                        9.0F, ColorUtil.multiplyAlpha(t.accent, a));

                String shown = ServerGui.ellipsize(t.text, W - (SLOT + 18.0F), TEXT);
                Fonts.drawString(Fonts.SF_MEDIUM, shown, slotX + SLOT + 6.0F,
                        y + (H - Fonts.SF_MEDIUM.textHeight(TEXT)) / 2.0F + 0.35F, TEXT,
                        ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, a));

                index++;
            }
        }
    }
}
