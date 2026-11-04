package pub.godweer.hud;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.list.Render2DEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Effects HUD as the reference screenshot draws it: one header pill, then one
 * pill per effect — name, amplifier, remaining time, and the effect icon at the
 * right edge. No shared card, no progress ring.
 */
public final class PotionsHud extends HudElement implements IMinecraft {

    private static final float ROW_GAP = 3.0F;
    private static final float TEXT = 7.5F;
    private static final float FX_ICON = 9.0F;

    private static final int HARM_COLOR = ColorUtil.rgba(255, 84, 92, 255);

    private static final class EffectRow {
        final String key;
        Identifier icon;
        String baseName;
        int amplifier;
        String timeText;
        boolean bad;
        float anim;
        boolean present;

        EffectRow(String key, Identifier icon, String baseName, int amplifier,
                  String timeText, boolean bad) {
            this.key = key;
            this.icon = icon;
            this.baseName = baseName;
            this.amplifier = amplifier;
            this.timeText = timeText;
            this.bad = bad;
            this.anim = 0.0F;
            this.present = true;
        }
    }

    private final Map<String, EffectRow> rows = new LinkedHashMap<>();
    private float animatedW = -1.0F;
    private float animatedH = -1.0F;

    public PotionsHud() {
        super("potions", "Potions", 440.0F, 305.0F, 148.0F, 76.0F);
    }

    private void syncEffects() {
        if (!beginSyncFrame()) return;
        for (EffectRow r : rows.values()) {
            r.present = false;
        }

        boolean hasReal = false;
        if (mc.player != null) {
            Collection<MobEffectInstance> fx = mc.player.getActiveEffects();
            for (MobEffectInstance inst : fx) {
                Holder<MobEffect> holder = inst.getEffect();
                MobEffect eff = holder.value();
                Identifier id = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (id == null) continue;
                Identifier tex = id.withPath(p -> "textures/mob_effect/" + p + ".png");
                String baseName = eff.getDisplayName().getString();
                int amp = inst.getAmplifier() + 1;
                String t;
                if (inst.isInfiniteDuration()) {
                    t = "**:**";
                } else {
                    int sec = Math.max(0, inst.getDuration() / 20);
                    t = String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
                }
                upsert(id.toString(), tex, baseName, amp, t,
                        eff.getCategory() == MobEffectCategory.HARMFUL);
                hasReal = true;
            }
        }

        if (!hasReal && HudManager.editMode()) {
            upsert("speed", Identifier.withDefaultNamespace("textures/mob_effect/speed.png"),
                    "Скорость", 2, "4:19", false);
            upsert("strength", Identifier.withDefaultNamespace("textures/mob_effect/strength.png"),
                    "Сила", 2, "0:25", false);
            upsert("haste", Identifier.withDefaultNamespace("textures/mob_effect/haste.png"),
                    "Спешка", 1, "1:25", false);
        }

        Iterator<Map.Entry<String, EffectRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            EffectRow r = it.next().getValue();
            r.anim += ((r.present ? 1.0F : 0.0F) - r.anim) * 0.24F;
            if (!r.present && r.anim < 0.02F) {
                it.remove();
            }
        }
    }

    private void upsert(String key, Identifier icon, String baseName, int amplifier,
                        String timeText, boolean bad) {
        EffectRow r = rows.get(key);
        if (r == null) {
            rows.put(key, new EffectRow(key, icon, baseName, amplifier, timeText, bad));
        } else {
            r.icon = icon;
            r.baseName = baseName;
            r.amplifier = amplifier;
            r.timeText = timeText;
            r.bad = bad;
            r.present = true;
        }
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        syncEffects();
        for (EffectRow r : rows.values()) {
            if (r.present || r.anim > 0.03F) return true;
        }
        return false;
    }

    @Override
    public void draw(Render2DEvent event) {
        syncEffects();

        List<EffectRow> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparing(r -> r.baseName.toLowerCase(Locale.ROOT)));

        if (sorted.isEmpty() && !HudManager.editMode()) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        String title = "Active potions";
        float headerW = HudStyle.PILL_PAD * 2.0F
                + HudStyle.segmentWidth(IconUse.POTION, title, TEXT);

        float maxW = headerW;
        int visible = 0;
        for (EffectRow r : sorted) {
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
                IconUse.POTION, title, TEXT, HudStyle.TEXT_PRIMARY, HudStyle.TEXT_PRIMARY);
        cy += HudStyle.PILL_H + ROW_GAP;

        var extractor = event.getGuiGraphicsExtractor();
        int accent = Theme.getAccentColor();

        for (EffectRow r : sorted) {
            float a = r.anim;
            if (a <= 0.03F) continue;
            float rowW = rowWidth(r);
            float slide = (1.0F - a) * -6.0F;
            float pillX = x + slide;
            float midY = cy + HudStyle.PILL_H / 2.0F;

            HudStyle.drawCard(pillX, cy, rowW, HudStyle.PILL_H, HudStyle.PILL_R, a);

            float nameX = pillX + HudStyle.PILL_PAD;
            String ampStr = r.amplifier > 1 ? " " + r.amplifier : "";
            float ampW = Fonts.SF_MEDIUM.getWidth(ampStr, TEXT);
            float timeW = Fonts.SF_MEDIUM.getWidth(r.timeText, TEXT);
            float rightBlock = FX_ICON + 4.0F + HudStyle.DIVIDER_W + 4.0F + timeW + HudStyle.PILL_PAD;
            String shown = ServerGui.ellipsize(r.baseName,
                    Math.max(16.0F, rowW - rightBlock - HudStyle.PILL_PAD - ampW), TEXT);

            Fonts.drawString(Fonts.SF_MEDIUM, shown, nameX, midY - Fonts.SF_MEDIUM.textHeight(TEXT) / 2.0F + 0.35F,
                    TEXT, ColorUtil.multiplyAlpha(r.bad ? HARM_COLOR : HudStyle.TEXT_PRIMARY, a));
            if (!ampStr.isEmpty()) {
                Fonts.drawString(Fonts.SF_MEDIUM, ampStr, nameX + Fonts.SF_MEDIUM.getWidth(shown, TEXT),
                        midY - Fonts.SF_MEDIUM.textHeight(TEXT) / 2.0F + 0.35F, TEXT,
                        ColorUtil.withAlpha(r.bad ? HARM_COLOR : accent, Math.round(255 * a)));
            }

            float tailX = pillX + rowW - HudStyle.PILL_PAD - FX_ICON;
            if (extractor != null && r.icon != null) {
                Render2DUtil.flush();
                extractor.pose().pushMatrix();
                extractor.pose().translate(tailX, midY - FX_ICON / 2.0F);
                float sc = FX_ICON / 18.0F;
                extractor.pose().scale(sc, sc);
                extractor.blit(r.icon, 0, 0, 18, 18, 0.0F, 1.0F, 0.0F, 1.0F);
                extractor.pose().popMatrix();
            }

            float divX = tailX - FX_ICON - 4.0F;
            Render2D.drawRoundedRect(divX, midY - 4.0F, HudStyle.DIVIDER_W, 8.0F, 0.5F,
                    ColorUtil.multiplyAlpha(HudStyle.DIVIDER, a));
            Fonts.drawString(Fonts.SF_MEDIUM, r.timeText, divX - 4.0F - timeW,
                    midY - Fonts.SF_MEDIUM.textHeight(TEXT) / 2.0F + 0.35F, TEXT,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, a));

            cy += HudStyle.PILL_H + ROW_GAP;
        }
    }

    private float rowWidth(EffectRow r) {
        float ampW = Fonts.SF_MEDIUM.getWidth(r.amplifier > 1 ? " " + r.amplifier : "", TEXT);
        float timeW = Fonts.SF_MEDIUM.getWidth(r.timeText, TEXT);
        return HudStyle.PILL_PAD * 2.0F + Fonts.SF_MEDIUM.getWidth(r.baseName, TEXT) + ampW
                + timeW + FX_ICON + HudStyle.DIVIDER_W + 8.0F;
    }
}
