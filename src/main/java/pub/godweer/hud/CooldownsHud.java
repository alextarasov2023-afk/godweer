package pub.godweer.hud;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.list.Render2DEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cooldowns HUD: every item that is currently on cooldown, with its icon, name,
 * a flat remaining-time bar and the seconds left. Cooldowns are keyed by the
 * item's cooldown group, so two pearls share one row.
 */
public final class CooldownsHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 4.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ROW_H = 16.0F;
    private static final float RADIUS = 7.0F;
    private static final float SLOT = 13.0F;
    private static final float BAR_H = 3.0F;
    private static final int TRACK = ColorUtil.rgba(255, 255, 255, 22);

    private static final class CooldownRow {
        final String key;
        ItemStack stack;
        float fraction;
        float seconds;
        float anim;
        boolean present;

        CooldownRow(String key, ItemStack stack, float fraction, float seconds) {
            this.key = key;
            this.stack = stack;
            this.fraction = fraction;
            this.seconds = seconds;
            this.anim = 0.0F;
            this.present = true;
        }
    }

    private final Map<String, CooldownRow> rows = new LinkedHashMap<>();
    private float animatedW = -1.0F;
    private float animatedH = -1.0F;

    public CooldownsHud() {
        super("cooldowns", "CoolDowns", 300.0F, 12.0F, 132.0F, HEADER_H + 4.0F + ROW_H + PAD_BOTTOM);
    }

    private static String groupKey(ItemStack stack, Identifier group) {
        if (group != null) return group.toString();
        try {
            return stack.getItem().toString();
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private void syncCooldowns(float partialTicks) {
        if (!beginSyncFrame()) return;
        for (CooldownRow r : rows.values()) {
            r.present = false;
        }

        Player player = mc.player;
        if (player != null) {
            ItemStack held = null;
            try {
                held = player.getOffhandItem();
            } catch (Exception ignored) {
            }
            collect(player, held, partialTicks);
            try {
                List<ItemStack> items = player.getInventory().getNonEquipmentItems();
                for (ItemStack stack : items) collect(player, stack, partialTicks);
            } catch (Exception ignored) {
            }
        }

        // Preview row while the HUD is being positioned in chat edit mode.
        // It has to be refreshed every frame, otherwise the row fades out and
        // flickers back in a second later.
        boolean hasRealRow = false;
        for (String key : rows.keySet()) {
            if (!"preview".equals(key)) {
                hasRealRow = true;
                break;
            }
        }
        if (!hasRealRow && HudManager.editMode()) {
            CooldownRow preview = rows.get("preview");
            if (preview == null) {
                preview = new CooldownRow("preview", new ItemStack(Items.ENDER_PEARL), 0.55F, 9.0F);
                rows.put("preview", preview);
            }
            preview.present = true;
            preview.fraction = 0.55F;
            preview.seconds = 9.0F;
            preview.stack = new ItemStack(Items.ENDER_PEARL);
        }

        Iterator<Map.Entry<String, CooldownRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            CooldownRow r = it.next().getValue();
            float target = r.present ? 1.0F : 0.0F;
            r.anim += (target - r.anim) * 0.24F;
            if (!r.present && r.anim < 0.02F) {
                it.remove();
            }
        }
    }

    private void collect(Player player, ItemStack stack, float partialTicks) {
        if (stack == null || stack.isEmpty() || player == null) return;
        float fraction = 0.0F;
        try {
            if (!player.getCooldowns().isOnCooldown(stack)) return;
            fraction = Math.max(0.0F, Math.min(1.0F, player.getCooldowns().getCooldownPercent(stack, partialTicks)));
        } catch (Exception ignored) {
            return;
        }
        Identifier group = null;
        try {
            group = player.getCooldowns().getCooldownGroup(stack);
        } catch (Exception ignored) {
        }
        String key = groupKey(stack, group);
        float seconds = 0.0F;
        try {
            UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
            if (cooldown != null) seconds = cooldown.seconds() * fraction;
        } catch (Exception ignored) {
        }

        CooldownRow row = rows.get(key);
        if (row == null) {
            if (rows.size() >= 8) return;
            rows.put(key, new CooldownRow(key, stack, fraction, seconds));
        } else {
            row.stack = stack;
            row.fraction = fraction;
            row.seconds = seconds;
            row.present = true;
        }
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        if (mc.player == null) return false;
        syncCooldowns(0.0F);
        for (CooldownRow r : rows.values()) {
            if (r.present || r.anim > 0.03F) return true;
        }
        return false;
    }

    @Override
    public void draw(Render2DEvent event) {
        float partialTicks = 0.0F;
        try {
            partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        } catch (Exception ignored) {
        }
        syncCooldowns(partialTicks);

        List<CooldownRow> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparingDouble((CooldownRow r) -> -r.fraction));

        if (sorted.isEmpty()) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        float listH = 0.0F;
        float maxRowW = Fonts.SF_MEDIUM.getWidth("CoolDowns", 7.0F) + 26.0F;
        for (CooldownRow r : sorted) {
            listH += ROW_H * r.anim;
            if (r.anim > 0.05F) {
                String name = itemName(r.stack);
                maxRowW = Math.max(maxRowW, SLOT + 5.0F + Fonts.SF_MEDIUM.getWidth(name, 7.5F)
                        + 10.0F + Fonts.SF_MEDIUM.getWidth(timeText(r), 7.0F));
            }
        }

        float targetW = Math.max(118.0F, maxRowW + PAD_X * 2.0F);
        float targetH = HEADER_H + 2.0F + Math.max(ROW_H, listH) + PAD_BOTTOM;

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        // Панель прижимается к правому краю, чтобы длинные названия предметов не уехали за экран.
        float screenW = mc.getWindow().getGuiScaledWidth();
        this.x = Math.max(0.0F, Math.min(x, screenW - getScaledWidth() - 4.0F));

        int accent = Theme.getAccentColor();

        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawLabel(x, y, width, HEADER_H, "CoolDowns", IconUse.CLOCK);

        Render2D.pushScissor(x, y + HEADER_H + 1.0F, width, Math.max(0.0F, height - HEADER_H - 1.0F));

        var extractor = event.getGuiGraphicsExtractor();
        float cy = y + HEADER_H + 2.0F;
        for (CooldownRow r : sorted) {
            float a = r.anim;
            if (a <= 0.03F) continue;
            float rh = ROW_H * a;
            float midY = cy + rh / 2.0F;
            float slide = (1.0F - a) * -6.0F;
            float cx = x + PAD_X + slide;

            // Item icon in a flat slot
            Render2D.drawRoundedRect(cx, midY - SLOT / 2.0F, SLOT, SLOT, 3.0F,
                    ColorUtil.rgba(255, 255, 255, Math.round(12 * a)));
            if (extractor != null && !r.stack.isEmpty()) {
                try {
                    Render2DUtil.flush();
                    extractor.pose().pushMatrix();
                    float iconX = cx + (SLOT - 16.0F) / 2.0F;
                    float iconY = midY - 8.0F;
                    extractor.item(r.stack, Math.round(iconX), Math.round(iconY));
                    extractor.pose().popMatrix();
                } catch (Exception ignored) {
                }
            }
            cx += SLOT + 5.0F;

            // Remaining seconds on the right
            String time = timeText(r);
            float timeW = Fonts.SF_MEDIUM.getWidth(time, 7.0F);
            float tx = x + width - PAD_X - timeW;
            Fonts.drawString(Fonts.SF_MEDIUM, time, tx,
                    midY - Fonts.SF_MEDIUM.textHeight(7.0F) / 2.0F - 1.0F + 0.35F, 7.0F,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, a));

            // Name over the bar, bar fills down to zero as the cooldown runs out
            float barW = Math.max(20.0F, tx - 8.0F - cx);
            String name = ServerGui.ellipsize(itemName(r.stack), barW, 7.5F);
            Fonts.drawString(Fonts.SF_MEDIUM, name, cx,
                    midY - Fonts.SF_MEDIUM.textHeight(7.5F) / 2.0F - 1.6F + 0.35F, 7.5F,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, a));

            float barY = midY + 3.2F;
            Render2D.drawRoundedRect(cx, barY, barW, BAR_H, 1.5F,
                    ColorUtil.multiplyAlpha(TRACK, a));
            if (r.fraction > 0.01F) {
                Render2D.drawRoundedRect(cx, barY, Math.max(BAR_H, barW * r.fraction), BAR_H, 1.5F,
                        ColorUtil.withAlpha(accent, Math.round(235 * a)));
            }

            cy += rh;
        }

        Render2D.popScissor();
    }

    private static String itemName(ItemStack stack) {
        try {
            String s = ServerGui.stripCodes(stack.getHoverName().getString()).trim();
            if (!s.isEmpty()) return s;
        } catch (Exception ignored) {
        }
        return "Item";
    }

    private static String timeText(CooldownRow r) {
        if (r.seconds <= 0.0F) return Math.round(r.fraction * 100.0F) + "%";
        return String.format(Locale.ROOT, "%.1fs", r.seconds);
    }
}
