package pub.godweer.hud;

import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds HUD as the reference screenshot draws it: one header pill, then one pill
 * per enabled module — name on the left, a thin stroke and the key on the right.
 * No keycap box, no shared card.
 */
public final class HotkeysHud extends HudElement implements IMinecraft {

    private static final float ROW_GAP = 3.0F;
    private static final float NAME = 7.5F;
    private static final float KEY = 7.0F;

    private static final class BindRow {
        final String id;
        String name;
        String keyText;
        float anim;
        boolean present;

        BindRow(String id, String name, String keyText) {
            this.id = id;
            this.name = name;
            this.keyText = keyText;
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

    private void syncBinds() {
        if (!beginSyncFrame()) return;
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

                upsert(m.getName().toLowerCase(Locale.ROOT), m.getName(), keyStr);
                anyActive = true;
            }
        }

        if (!anyActive && HudManager.editMode()) {
            upsert("speed", "Speed", "G");
        }

        Iterator<Map.Entry<String, BindRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            BindRow r = it.next().getValue();
            r.anim += ((r.present ? 1.0F : 0.0F) - r.anim) * 0.24F;
            if (!r.present && r.anim < 0.02F) {
                it.remove();
            }
        }
    }

    private void upsert(String id, String name, String keyText) {
        BindRow r = rows.get(id);
        if (r == null) {
            rows.put(id, new BindRow(id, name, keyText));
        } else {
            r.name = name;
            r.keyText = keyText;
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

        String title = "Hotkeys";
        float headerW = HudStyle.PILL_PAD * 2.0F
                + HudStyle.segmentWidth(IconUse.KEYBOARD, title, NAME);

        float maxW = headerW;
        int visible = 0;
        for (BindRow r : sorted) {
            if (r.anim > 0.03F) visible++;
            if (r.anim > 0.05F) {
                maxW = Math.max(maxW, rowWidth(r));
            }
        }

        float targetW = maxW;
        float targetH = HudStyle.PILL_H + ROW_GAP + visible * (HudStyle.PILL_H + ROW_GAP);

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        float cy = y;
        HudStyle.drawPill(x, cy, headerW);
        HudStyle.drawSegment(x + HudStyle.PILL_PAD, cy + HudStyle.PILL_H / 2.0F,
                IconUse.KEYBOARD, title, NAME, HudStyle.TEXT_PRIMARY, HudStyle.TEXT_PRIMARY);
        cy += HudStyle.PILL_H + ROW_GAP;

        for (BindRow r : sorted) {
            float a = r.anim;
            if (a <= 0.03F) continue;
            float rowW = rowWidth(r);
            float slide = (1.0F - a) * -6.0F;
            float pillX = x + slide;
            float midY = cy + HudStyle.PILL_H / 2.0F;
            float textY = midY - Fonts.SF_MEDIUM.textHeight(NAME) / 2.0F + 0.35F;

            HudStyle.drawCard(pillX, cy, rowW, HudStyle.PILL_H, HudStyle.PILL_R, a);

            float keyW = Fonts.SF_MEDIUM.getWidth(r.keyText, KEY);
            float divX = pillX + rowW - HudStyle.PILL_PAD - keyW - 6.0F;
            String shown = ServerGui.ellipsize(r.name,
                    Math.max(16.0F, divX - 6.0F - (pillX + HudStyle.PILL_PAD)), NAME);

            Fonts.drawString(Fonts.SF_MEDIUM, shown, pillX + HudStyle.PILL_PAD, textY, NAME,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, a));
            Render2D.drawRoundedRect(divX, midY - 4.0F, HudStyle.DIVIDER_W, 8.0F, 0.5F,
                    ColorUtil.multiplyAlpha(HudStyle.DIVIDER, a));
            Fonts.drawString(Fonts.SF_MEDIUM, r.keyText, divX + 6.0F,
                    midY - Fonts.SF_MEDIUM.textHeight(KEY) / 2.0F + 0.35F, KEY,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, a));

            cy += HudStyle.PILL_H + ROW_GAP;
        }
    }

    private static float rowWidth(BindRow r) {
        return HudStyle.PILL_PAD * 2.0F + Fonts.SF_MEDIUM.getWidth(r.name, NAME) + 6.0F
                + HudStyle.DIVIDER_W + 6.0F + Fonts.SF_MEDIUM.getWidth(r.keyText, KEY);
    }
}
