package pub.godweer.hud;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import pub.godweer.IMinecraft;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render2DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;
import pub.godweer.enjoy.event.list.Render2DEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Effects (Potions) HUD:
 * Clean header ("Effects" + right potion icon + divider),
 * potion icon slots, amplifier highlights, circular progress rings, and smooth enter/exit animations.
 */
public final class PotionsHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 5.0F;
    private static final float HEADER_H = 19.0F;
    private static final float ROW_H = 15.0F;
    private static final float RADIUS = 8.0F;

    private static final int HARM_COLOR = ColorUtil.rgba(255, 84, 92, 255);
    private static final int RING_TRACK = ColorUtil.rgba(255, 255, 255, 26);
    private static final float RING_R = 4.2F;
    private static final float RING_THICK = 1.6F;

    private static final class EffectRow {
        final String key;
        Identifier icon;
        String baseName;
        int amplifier;
        String timeText;
        boolean bad;
        int duration;
        boolean infinite;
        float anim;
        boolean present;

        EffectRow(String key, Identifier icon, String baseName, int amplifier,
                  String timeText, boolean bad, int duration, boolean infinite) {
            this.key = key;
            this.icon = icon;
            this.baseName = baseName;
            this.amplifier = amplifier;
            this.timeText = timeText;
            this.bad = bad;
            this.duration = duration;
            this.infinite = infinite;
            this.anim = 0.0F;
            this.present = true;
        }
    }

    private final Map<String, EffectRow> rows = new LinkedHashMap<>();
    private final Map<String, Integer> maxDurations = new HashMap<>();
    private float animatedW = -1.0F;
    private float animatedH = -1.0F;

    public PotionsHud() {
        super("potions", "Potions", 440.0F, 305.0F, 148.0F, 76.0F);
    }

    private void syncEffects() {
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
                boolean infinite = inst.isInfiniteDuration();
                String t;
                if (infinite) {
                    t = "**:**";
                } else {
                    int sec = Math.max(0, inst.getDuration() / 20);
                    t = String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
                }
                String key = id.toString();
                upsert(key, tex, baseName, amp, t, eff.getCategory() == MobEffectCategory.HARMFUL,
                        inst.getDuration(), infinite);
                hasReal = true;
            }
        }

        if (!hasReal && HudManager.editMode()) {
            upsert("speed", Identifier.withDefaultNamespace("textures/mob_effect/speed.png"),
                    "Скорость", 2, "4:19", false, 5180, false);
            upsert("strength", Identifier.withDefaultNamespace("textures/mob_effect/strength.png"),
                    "Сила", 2, "0:25", false, 500, false);
            upsert("haste", Identifier.withDefaultNamespace("textures/mob_effect/haste.png"),
                    "Спешка", 1, "1:25", false, 1700, false);
        }

        Iterator<Map.Entry<String, EffectRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            EffectRow r = it.next().getValue();
            if (!r.infinite && r.present) {
                int prev = maxDurations.getOrDefault(r.key, 0);
                if (r.duration > prev) maxDurations.put(r.key, r.duration);
            }
            float target = r.present ? 1.0F : 0.0F;
            r.anim += (target - r.anim) * 0.24F;
            if (!r.present && r.anim < 0.02F) {
                maxDurations.remove(r.key);
                it.remove();
            }
        }
    }

    private void upsert(String key, Identifier icon, String baseName, int amplifier,
                        String timeText, boolean bad, int duration, boolean infinite) {
        EffectRow r = rows.get(key);
        if (r == null) {
            r = new EffectRow(key, icon, baseName, amplifier, timeText, bad, duration, infinite);
            rows.put(key, r);
        } else {
            r.icon = icon;
            r.baseName = baseName;
            r.amplifier = amplifier;
            r.timeText = timeText;
            r.bad = bad;
            r.duration = duration;
            r.infinite = infinite;
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

        float listH = 0.0F;
        float maxW = Fonts.SF_MEDIUM.getWidth("Effects", 8.5F) + 32.0F;

        for (EffectRow r : sorted) {
            listH += ROW_H * r.anim;
            if (r.anim > 0.05F) {
                String fullLabel = r.amplifier > 1 ? r.baseName + " " + r.amplifier : r.baseName;
                float rowW = 14.5F + Fonts.SF_MEDIUM.getWidth(fullLabel, 7.5F)
                        + 10.0F + (RING_R * 2.0F + 4.5F) + Fonts.SF_MEDIUM.getWidth(r.timeText, 7.5F);
                maxW = Math.max(maxW, rowW);
            }
        }

        float targetW = Math.max(128.0F, maxW + PAD_X * 2.0F);
        float targetH = HEADER_H + 4.0F + Math.max(ROW_H, listH) + PAD_BOTTOM;

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        int accent = Theme.getAccentColor();

        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawHeader(x, y, width, HEADER_H, "Effects", IconUse.POTION);

        Render2D.pushScissor(x, y + HEADER_H + 1.0F, width, Math.max(0.0F, height - HEADER_H - 1.0F));

        var extractor = event.getGuiGraphicsExtractor();
        float cy = y + HEADER_H + 4.0F;
        for (EffectRow r : sorted) {
            float a = r.anim;
            if (a <= 0.03F) continue;
            float rh = ROW_H * a;
            float midY = cy + rh / 2.0F;
            float slide = (1.0F - a) * -6.0F;
            int rowAccent = r.bad ? HARM_COLOR : accent;

            // Potion icon slot
            float slotSz = 11.0F;
            float sx = x + PAD_X + slide;
            float sy = midY - slotSz / 2.0F;
            Render2D.drawRoundedRect(sx, sy, slotSz, slotSz, 3.2F,
                    ColorUtil.rgba(255, 255, 255, Math.round(13 * a)));
            float iconSize = 8.5F * a;
            if (extractor != null && iconSize > 2.0F && r.icon != null) {
                Render2DUtil.flush();
                extractor.pose().pushMatrix();
                extractor.pose().translate(sx + (slotSz - iconSize) / 2.0F, sy + (slotSz - iconSize) / 2.0F);
                float sc = iconSize / 18.0F;
                extractor.pose().scale(sc, sc);
                extractor.blit(r.icon, 0, 0, 18, 18, 0.0F, 1.0F, 0.0F, 1.0F);
                extractor.pose().popMatrix();
            }

            // Duration pill + circular progress ring on the right
            float timeSize = 7.5F;
            float tw = Fonts.SF_MEDIUM.getWidth(r.timeText, timeSize);
            float timeX = x + width - PAD_X - tw;
            float textY = midY - Fonts.SF_MEDIUM.textHeight(timeSize) / 2.0F + 0.35F;

            boolean lowTime = !r.infinite && r.duration <= 200;
            int timeCol = r.bad || lowTime
                    ? ColorUtil.withAlpha(HARM_COLOR, Math.round(240 * a))
                    : ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, a);
            Fonts.drawString(Fonts.SF_MEDIUM, r.timeText, timeX, textY, timeSize, timeCol);

            float ringCx = timeX - 4.5F - RING_R;
            float frac;
            if (r.infinite) {
                frac = 1.0F;
            } else {
                int max = maxDurations.getOrDefault(r.key, Math.max(1, r.duration));
                frac = max <= 0 ? 1.0F : Mth.clamp(r.duration / (float) max, 0.0F, 1.0F);
            }
            Render2D.drawArc(ringCx, midY, RING_R, RING_THICK, 0.0F, 360.0F,
                    ColorUtil.multiplyAlpha(RING_TRACK, a));
            if (frac > 0.001F) {
                Render2D.drawArc(ringCx, midY, RING_R, RING_THICK, -90.0F, 360.0F * frac,
                        ColorUtil.withAlpha(rowAccent, Math.round(245 * a)));
            }

            // Effect name + colored amplifier (e.g., "Скорость 2")
            float nx = sx + slotSz + 4.5F;
            String ampStr = r.amplifier > 1 ? " " + r.amplifier : "";
            float ampW = ampStr.isEmpty() ? 0.0F : Fonts.SF_MEDIUM.getWidth(ampStr, 7.5F);
            float availBaseW = Math.max(16.0F, (ringCx - RING_R - 4.5F) - nx - ampW);
            String shownBase = ServerGui.ellipsize(r.baseName, availBaseW, 7.5F);

            Fonts.drawString(Fonts.SF_MEDIUM, shownBase, nx, textY, 7.5F,
                    ColorUtil.multiplyAlpha(r.bad ? HARM_COLOR : HudStyle.TEXT_PRIMARY, a));
            if (!ampStr.isEmpty()) {
                float baseW = Fonts.SF_MEDIUM.getWidth(shownBase, 7.5F);
                Fonts.drawString(Fonts.SF_MEDIUM, ampStr, nx + baseW, textY, 7.5F,
                        ColorUtil.withAlpha(rowAccent, Math.round(255 * a)));
            }

            cy += rh;
        }

        Render2D.popScissor();
    }
}
