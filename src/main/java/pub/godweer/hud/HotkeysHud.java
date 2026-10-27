package pub.godweer.hud;

import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds (Hotkeys) HUD:
 * Clean header ("Binds" + right icon + divider), smooth appear/disappear animations,
 * category icons, and sleek keycaps.
 */
public final class HotkeysHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 5.0F;
    private static final float HEADER_H = 19.0F;
    private static final float ROW_H = 14.5F;
    private static final float RADIUS = 8.0F;

    private static final class BindRow {
        final String id;
        String name;
        String keyText;
        Category category;
        float anim;
        boolean present;

        BindRow(String id, String name, String keyText, Category category) {
            this.id = id;
            this.name = name;
            this.keyText = keyText;
            this.category = category;
            this.anim = 0.0F;
            this.present = true;
        }
    }

    private final Map<String, BindRow> rows = new LinkedHashMap<>();
    private float animatedW = -1.0F;
    private float animatedH = -1.0F;

    public HotkeysHud() {
        super("hotkeys", "Hotkeys", 12.0F, 145.0F, 128.0F, 76.0F);
    }

    private static IconUse catIcon(Category c) {
        if (c == null) return IconUse.FIGHT;
        return switch (c) {
            case COMBAT -> IconUse.FIGHT;
            case MOVEMENT -> IconUse.MOVEMENT;
            case RENDER -> IconUse.RENDER;
            case PLAYER -> IconUse.PLAYER;
            case MISC -> IconUse.MISC;
        };
    }

    private void syncBinds() {
        for (BindRow r : rows.values()) {
            r.present = false;
        }

        boolean anyActive = false;
        if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
            for (Module m : Client.INSTANCE.moduleManager.getModules()) {
                if (m == null || !m.isEnabled()) continue;
                if (m.getBind() == null || !m.getBind().isBound()) continue;
                String keyStr = m.getBind().getDisplayValue();
                if (keyStr == null || keyStr.equalsIgnoreCase("NONE") || keyStr.isEmpty()) continue;

                String id = m.getName().toLowerCase(Locale.ROOT);
                upsert(id, m.getName(), keyStr, m.getCategory());
                anyActive = true;
            }
        }

        if (!anyActive && HudManager.editMode()) {
            upsert("speed", "Speed", "G", Category.MOVEMENT);
            upsert("timer", "Timer", "X", Category.MOVEMENT);
            upsert("waterspeed", "WaterSpeed", "V", Category.MOVEMENT);
        }

        Iterator<Map.Entry<String, BindRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            BindRow r = it.next().getValue();
            float target = r.present ? 1.0F : 0.0F;
            r.anim += (target - r.anim) * 0.24F;
            if (!r.present && r.anim < 0.02F) {
                it.remove();
            }
        }
    }

    private void upsert(String id, String name, String keyText, Category cat) {
        BindRow r = rows.get(id);
        if (r == null) {
            r = new BindRow(id, name, keyText, cat);
            rows.put(id, r);
        } else {
            r.name = name;
            r.keyText = keyText;
            r.category = cat;
            r.present = true;
        }
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        syncBinds();
        for (BindRow r : rows.values()) {
            if (r.present || r.anim > 0.03F) return true;
        }
        return false;
    }

    @Override
    public void draw(Render2DEvent event) {
        syncBinds();

        List<BindRow> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparing(r -> r.name.toLowerCase(Locale.ROOT)));

        if (sorted.isEmpty() && !HudManager.editMode()) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        float listH = 0.0F;
        float maxW = Fonts.SF_MEDIUM.getWidth("Binds", 8.5F) + 32.0F;

        for (BindRow r : sorted) {
            listH += ROW_H * r.anim;
            if (r.anim > 0.05F) {
                float rowW = 11.0F + Fonts.SF_MEDIUM.getWidth(r.name, 7.5F)
                        + 14.0F + Math.max(13.0F, Fonts.SF_MEDIUM.getWidth(r.keyText, 7.0F) + 7.5F);
                maxW = Math.max(maxW, rowW);
            }
        }

        float targetW = Math.max(110.0F, maxW + PAD_X * 2.0F);
        float targetH = HEADER_H + 4.0F + Math.max(ROW_H, listH) + PAD_BOTTOM;

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        int accent = Theme.getAccentColor();

        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawHeader(x, y, width, HEADER_H, "Binds", IconUse.KEYBIND);

        Render2D.pushScissor(x, y + HEADER_H + 1.0F, width, Math.max(0.0F, height - HEADER_H - 1.0F));

        float cy = y + HEADER_H + 4.0F;
        for (BindRow r : sorted) {
            float a = r.anim;
            if (a <= 0.03F) continue;
            float rh = ROW_H * a;
            float midY = cy + rh / 2.0F;
            float slide = (1.0F - a) * -6.0F;

            // Category icon
            float cx = x + PAD_X + slide;
            Fonts.drawIcon(catIcon(r.category), cx, midY - 7.5F / 2.0F + 0.2F, 7.5F,
                    ColorUtil.withAlpha(accent, Math.round(215 * a)));
            cx += 11.0F;

            // Keycap pill on the right
            float keySize = 7.0F;
            float kw = Math.max(13.0F, Fonts.SF_MEDIUM.getWidth(r.keyText, keySize) + 7.5F);
            float kh = 10.0F;
            float kx = x + width - PAD_X - kw;
            float ky = midY - kh / 2.0F;

            Render2D.drawRoundedRectWithOutline(kx, ky, kw, kh, 3.2F,
                    ColorUtil.withAlpha(accent, Math.round(30 * a)),
                    0.65F,
                    ColorUtil.withAlpha(accent, Math.round(95 * a)));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, r.keyText, kx + kw / 2.0F,
                    midY - Fonts.SF_MEDIUM.textHeight(keySize) / 2.0F + 0.35F,
                    keySize, ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, a));

            // Module name
            float nameSize = 7.5F;
            float availNameW = Math.max(16.0F, kx - 5.0F - cx);
            String shown = ServerGui.ellipsize(r.name, availNameW, nameSize);
            Fonts.drawString(Fonts.SF_MEDIUM, shown, cx,
                    midY - Fonts.SF_MEDIUM.textHeight(nameSize) / 2.0F + 0.35F,
                    nameSize, ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, a));

            cy += rh;
        }

        Render2D.popScissor();
    }
}
