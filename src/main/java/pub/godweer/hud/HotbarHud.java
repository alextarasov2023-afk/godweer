package pub.godweer.hud;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render2DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;

/**
 * Custom hotbar in HUD style: one solid panel, bars inside, animated selection.
 * Vanilla hotbar is cancelled in HudMixin while this replaces it.
 */
public final class HotbarHud implements IMinecraft {

    private static final HotbarHud INSTANCE = new HotbarHud();

    private static final int BG = ColorUtil.rgba(10, 10, 14, 200);
    private static final int TRACK = ColorUtil.rgba(255, 255, 255, 18);
    private static final int SLOT_BG = ColorUtil.rgba(255, 255, 255, 10);
    private static final int SLOT_BG_SEL = ColorUtil.rgba(255, 255, 255, 24);

    private static final float SLOT = 24.0F;
    private static final float GAP = 4.0F;
    private static final float PAD = 6.0F;
    private static final float RADIUS = 10.0F;
    private static final float BOTTOM = 6.0F;
    private static final float BAR_W = 9.0F * SLOT + 8.0F * GAP;
    private static final float OFF_GAP = 8.0F;

    private static final float EXP_ROW = 8.0F;
    private static final float EXP_H = 3.0F;
    private static final float BAR_H = 8.0F;
    private static final float ROW_GAP_1 = 5.0F;
    private static final float ROW_GAP_2 = 6.0F;

    private boolean enabled = false;

    private float smoothHp = -1.0F;
    private float smoothAbs;
    private float smoothFood = -1.0F;
    private float smoothSelX = Float.NaN;

    private String lastSelKey = "";
    private long selNameTime;
    private static final long SEL_NAME_MS = 2500L;

    private HotbarHud() {
    }

    public static HotbarHud getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static boolean shouldReplace() {
        return false;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!shouldReplace()) return;
        if (event.getGuiGraphicsExtractor() == null) return;
        drawHotbar(event);
        if (ServerGui.isEnabled()) {
            ServerGui.renderTabList();
            ServerGui.renderSidebar();
        }
    }

    private void drawHotbar(Render2DEvent event) {
        var extractor = event.getGuiGraphicsExtractor();
        Player player = mc.player;
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        // One panel, everything inside: bottom margin guarantees nothing clips.
        float panelW = PAD * 2.0F + BAR_W + OFF_GAP + SLOT;
        float panelH = PAD + EXP_ROW + ROW_GAP_1 + BAR_H + ROW_GAP_2 + SLOT + PAD;
        float panelX = (screenW - panelW) / 2.0F;
        float panelBottom = screenH - BOTTOM;
        float panelTop = panelBottom - panelH;

        float rowX = panelX + PAD;
        float slotY = panelBottom - PAD - SLOT;
        float hpY = slotY - ROW_GAP_2 - BAR_H;
        float expY = hpY - ROW_GAP_1 - EXP_ROW;
        float offX = rowX + BAR_W + OFF_GAP;

        Render2D.drawBlur(panelX, panelTop, panelW, panelH, RADIUS, BG, 1.0F);

        int selected;
        try {
            selected = player.getInventory().getSelectedSlot();
        } catch (Exception e) {
            selected = 0;
        }

        NonNullList<ItemStack> items;
        try {
            items = player.getInventory().getNonEquipmentItems();
        } catch (Exception e) {
            items = null;
        }

        // Animated selection highlight (snaps on big jumps like respawn).
        float selTargetX = rowX + selected * (SLOT + GAP);
        if (Float.isNaN(smoothSelX)) smoothSelX = selTargetX;
        if (Math.abs(selTargetX - smoothSelX) > SLOT * 4.0F) {
            smoothSelX = selTargetX;
        } else {
            smoothSelX += (selTargetX - smoothSelX) * 0.35F;
        }

        int accent = Theme.getAccentColor();
        float glowPad = 2.5F;
        Render2D.drawRoundedRect(smoothSelX - glowPad, slotY - glowPad, SLOT + glowPad * 2.0F, SLOT + glowPad * 2.0F,
                8.0F, ColorUtil.withAlpha(accent, 55));

        for (int i = 0; i < 9; i++) {
            ItemStack stack = ItemStack.EMPTY;
            try {
                if (items != null && i < items.size()) stack = items.get(i);
            } catch (Exception ignored) {
            }
            boolean isSel = i == selected;
            drawSlot(event, extractor, player, stack, rowX + i * (SLOT + GAP), slotY, isSel, String.valueOf(i + 1), accent);
        }

        // Divider + offhand in the same row (no more staggered "crooked" slot).
        float divX = rowX + BAR_W + OFF_GAP / 2.0F;
        Render2D.drawRoundedRect(divX - 0.5F, slotY + 4.0F, 1.0F, SLOT - 8.0F, 0.5F,
                ColorUtil.rgba(255, 255, 255, 16));
        ItemStack offhand = ItemStack.EMPTY;
        try {
            offhand = player.getOffhandItem();
        } catch (Exception ignored) {
        }
        drawSlot(event, extractor, player, offhand == null ? ItemStack.EMPTY : offhand, offX, slotY, false, "F", accent);

        drawBars(player, rowX, BAR_W, hpY, expY);

        boolean hasAir = drawAir(player, rowX, BAR_W, panelTop);
        drawSelectedName(player, items, selected, screenW, panelTop, hasAir);

        // Carried stack follows cursor inside containers.
        try {
            if (mc.gui.screen() != null) {
                ItemStack carried = player.inventoryMenu.getCarried();
                if (carried != null && !carried.isEmpty()) {
                    int mx = (int) HudManager.mouseX() - 8;
                    int my = (int) HudManager.mouseY() - 8;
                    Render2DUtil.flush();
                    extractor.item(carried, mx, my);
                    if (carried.getCount() > 1) {
                        drawCount(String.valueOf(carried.getCount()), mx + 8.0F, my + 8.0F);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void drawSlot(Render2DEvent event, net.minecraft.client.gui.GuiGraphicsExtractor extractor,
                          Player player, ItemStack stack, float sx, float sy, boolean selected,
                          String hint, int accent) {
        Render2D.drawRoundedRect(sx, sy, SLOT, SLOT, 6.0F, selected ? SLOT_BG_SEL : SLOT_BG);
        if (hint != null) {
            Fonts.drawString(Fonts.SF_MEDIUM, hint, sx + 3.0F, sy + 1.5F, 6.0F,
                    ColorUtil.rgba(255, 255, 255, 110));
        }
        if (selected) {
            Render2D.drawRoundedOutline(sx - 1.0F, sy - 1.0F, SLOT + 2.0F, SLOT + 2.0F, 7.0F, 1.5F,
                    ColorUtil.withAlpha(accent, 255));
        }

        if (stack == null || stack.isEmpty()) return;

        Render2DUtil.flush();
        extractor.item(stack, Math.round(sx + (SLOT - 16.0F) / 2.0F), Math.round(sy + (SLOT - 16.0F) / 2.0F));

        // Item cooldown (ender pearls etc): darkens the slot while on cooldown.
        try {
            if (player != null && player.getCooldowns().isOnCooldown(stack)) {
                Render2D.drawRoundedRect(sx, sy, SLOT, SLOT, 6.0F, ColorUtil.rgba(0, 0, 0, 150));
                Render2D.drawRoundedRect(sx + 3.0F, sy + 2.0F, SLOT - 6.0F, 1.5F, 0.75F,
                        ColorUtil.rgba(255, 90, 90, 255));
            }
        } catch (Exception ignored) {
        }

        // Attack cooldown on the selected slot.
        if (selected && player != null) {
            try {
                float atk = Math.max(0.0F, Math.min(1.0F, player.getAttackStrengthScale(0.0F)));
                if (atk < 1.0F) {
                    Render2D.drawRoundedRect(sx + 3.0F, sy + 2.5F, (SLOT - 6.0F) * atk, 1.5F, 0.75F,
                            ColorUtil.rgba(255, 255, 255, 220));
                }
            } catch (Exception ignored) {
            }
        }

        if (stack.isDamaged()) {
            try {
                float max = Math.max(1.0F, stack.getMaxDamage());
                float frac = Math.max(0.0F, Math.min(1.0F, 1.0F - ((float) stack.getDamageValue() / max)));
                int col = ColorUtil.rgba((int) (255 * (1.0F - frac)), (int) (225 * frac + 30), 80, 255);
                Render2D.drawRoundedRect(sx + 3.0F, sy + SLOT - 4.0F, (SLOT - 6.0F) * frac, 2.0F, 1.0F, col);
            } catch (Exception ignored) {
            }
        }
        if (stack.getCount() > 1) {
            drawCount(String.valueOf(stack.getCount()), sx + SLOT / 2.0F, sy + SLOT / 2.0F);
        }
    }

    private void drawCount(String text, float slotCx, float slotCy) {
        float size = 7.5F;
        float tw = Fonts.SF_MEDIUM.getWidth(text, size);
        float tx = slotCx + SLOT / 2.0F - tw - 2.5F;
        float ty = slotCy + SLOT / 2.0F - size - 2.0F;
        Render2D.drawRoundedRect(tx - 2.0F, ty - 1.0F, tw + 4.0F, size + 3.0F, 2.5F,
                ColorUtil.rgba(0, 0, 0, 140));
        Fonts.drawString(Fonts.SF_MEDIUM, text, tx, ty, size, ColorUtil.rgba(255, 255, 255, 255));
    }

    private void drawBars(Player player, float rowX, float rowW, float hpY, float expY) {
        float hp = 0.0F;
        float maxHp = 20.0F;
        float abs = 0.0F;
        float food = 20.0F;
        int armor = 0;
        float prog = 0.0F;
        int level = 0;
        try {
            hp = Math.max(0.0F, player.getHealth());
            maxHp = Math.max(1.0F, player.getMaxHealth());
            abs = Math.max(0.0F, player.getAbsorptionAmount());
            food = player.getFoodData().getFoodLevel();
            armor = player.getArmorValue();
            prog = Math.max(0.0F, Math.min(1.0F, player.experienceProgress));
            level = player.experienceLevel;
        } catch (Exception ignored) {
        }

        // Snap on first frame / respawn instead of sliding from stale values.
        if (smoothHp < 0.0F || Math.abs(Math.min(hp, maxHp) - smoothHp) > maxHp * 0.5F) smoothHp = Math.min(hp, maxHp);
        if (smoothFood < 0.0F || Math.abs(food - smoothFood) > 10.0F) smoothFood = food;
        smoothHp += (Math.min(hp, maxHp) - smoothHp) * 0.25F;
        smoothAbs += (abs - smoothAbs) * 0.25F;
        smoothFood += (food - smoothFood) * 0.25F;

        // Experience row: level left, bar fills the rest.
        float lvW = 0.0F;
        String lvText = level > 0 ? String.valueOf(level) : null;
        if (lvText != null) {
            lvW = Fonts.SF_MEDIUM.getWidth(lvText, 7.0F);
            Fonts.drawString(Fonts.SF_MEDIUM, lvText, rowX, expY - 0.5F, 7.0F,
                    ColorUtil.rgba(140, 240, 160, 255));
        }
        float exX = rowX + (lvText != null ? lvW + 5.0F : 0.0F);
        float exW = rowX + rowW - exX;
        float exBarY = expY + (EXP_ROW - EXP_H) / 2.0F;
        if (exW > 1.0F) {
            Render2D.drawRoundedRect(exX, exBarY, exW, EXP_H, EXP_H / 2.0F, TRACK);
            if (prog * exW > 0.5F) {
                Render2D.drawRoundedRect(exX, exBarY, Math.max(EXP_H, prog * exW), EXP_H, EXP_H / 2.0F,
                        ColorUtil.rgba(110, 220, 130, 255));
            }
        }

        float halfGap = 4.0F;
        float halfW = (rowW - halfGap) / 2.0F;
        float hx = rowX + halfW + halfGap;

        // Health (left): absorption extends the bar in yellow, number centered.
        float denom = Math.max(maxHp, smoothHp + smoothAbs);
        float totFrac = denom <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, (smoothHp + smoothAbs) / denom));
        float hpFrac = denom <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, smoothHp / denom));
        Render2D.drawRoundedRect(rowX, hpY, halfW, BAR_H, BAR_H / 2.0F, TRACK);
        if (totFrac * halfW > 0.5F) {
            Render2D.drawRoundedRect(rowX, hpY, Math.max(BAR_H, totFrac * halfW), BAR_H, BAR_H / 2.0F,
                    ColorUtil.rgba(255, 200, 50, 255));
        }
        if (hpFrac * halfW > 0.5F) {
            Render2D.drawRoundedRect(rowX, hpY, Math.max(BAR_H, hpFrac * halfW), BAR_H, BAR_H / 2.0F,
                    hpColor(hp / maxHp));
        }
        String hpText = abs > 0.5F ? String.format("%.0f+%.0f", (float) Math.ceil(hp), (float) Math.ceil(abs))
                : String.format("%.0f", (float) Math.ceil(hp));
        Fonts.drawCenteredString(Fonts.SF_MEDIUM, hpText, rowX + halfW / 2.0F, hpY - 0.5F, 6.5F,
                ColorUtil.rgba(245, 248, 250, 255));

        // Hunger (right), same direction, number centered.
        float foodFrac = Math.max(0.0F, Math.min(1.0F, smoothFood / 20.0F));
        Render2D.drawRoundedRect(hx, hpY, halfW, BAR_H, BAR_H / 2.0F, TRACK);
        if (foodFrac * halfW > 0.5F) {
            Render2D.drawRoundedRect(hx, hpY, Math.max(BAR_H, foodFrac * halfW), BAR_H, BAR_H / 2.0F,
                    ColorUtil.rgba(235, 150, 55, 255));
        }
        Fonts.drawCenteredString(Fonts.SF_MEDIUM, String.format("%.0f", food), hx + halfW / 2.0F, hpY - 0.5F,
                6.5F, ColorUtil.rgba(245, 248, 250, 255));

        // Armor: thin bar tucked in the gap under the hp/hunger row.
        if (armor > 0) {
            float armorFrac = Math.max(0.0F, Math.min(1.0F, armor / 20.0F));
            float aY = hpY + BAR_H + (ROW_GAP_2 - 2.5F) / 2.0F;
            Render2D.drawRoundedRect(rowX, aY, halfW, 2.5F, 1.25F, TRACK);
            Render2D.drawRoundedRect(rowX, aY, Math.max(2.5F, armorFrac * halfW), 2.5F, 1.25F,
                    ColorUtil.rgba(120, 175, 255, 255));
        }
    }

    private static int hpColor(float pct) {
        float t = Math.max(0.0F, Math.min(1.0F, pct));
        int red = ColorUtil.rgba(255, 85, 75, 255);
        int yellow = ColorUtil.rgba(255, 200, 70, 255);
        int green = ColorUtil.rgba(95, 225, 125, 255);
        if (t < 0.5F) return ColorUtil.lerp(red, yellow, t * 2.0F);
        return ColorUtil.lerp(yellow, green, (t - 0.5F) * 2.0F);
    }

    private boolean drawAir(Player player, float rowX, float rowW, float panelTop) {
        int air = 0;
        int maxAir = 1;
        try {
            air = player.getAirSupply();
            maxAir = Math.max(1, player.getMaxAirSupply());
        } catch (Exception ignored) {
        }
        if (air >= maxAir) return false;
        int filled = (int) Math.ceil(air * 10.0F / maxAir);
        float r = 2.0F;
        float step = 6.5F;
        float cy = panelTop - 7.0F;
        float startX = rowX + rowW / 2.0F - (10.0F * step) / 2.0F + step / 2.0F;
        for (int i = 0; i < 10; i++) {
            float cx = startX + i * step;
            if (i < filled) {
                Render2D.drawCircle(cx, cy, r, ColorUtil.rgba(120, 200, 255, 230));
            } else {
                Render2D.drawCircleOutline(cx, cy, r, 1.0F, ColorUtil.rgba(120, 200, 255, 120));
            }
        }
        return true;
    }

    private void drawSelectedName(Player player, NonNullList<ItemStack> items, int selected,
                                  float screenW, float panelTop, boolean hasAir) {
        String name = "";
        try {
            ItemStack stack = (items != null && selected >= 0 && selected < items.size())
                    ? items.get(selected) : ItemStack.EMPTY;
            if (stack != null && !stack.isEmpty()) {
                name = stack.getHoverName().getString();
                if (stack.getCount() > 1) name = stack.getCount() + " × " + name;
            }
        } catch (Exception ignored) {
        }
        String key = selected + "|" + name;
        long now = System.currentTimeMillis();
        if (!key.equals(lastSelKey)) {
            lastSelKey = key;
            selNameTime = now;
        }
        if (name.isEmpty()) return;
        try {
            if (mc.gui.screen() != null) return;
        } catch (Exception ignored) {
        }
        long age = now - selNameTime;
        if (age > SEL_NAME_MS) return;
        float alpha = age > SEL_NAME_MS - 500L ? (SEL_NAME_MS - age) / 500.0F : 1.0F;

        float size = 8.0F;
        float tw = Fonts.SF_MEDIUM.getWidth(name, size);
        float w = tw + 12.0F;
        float h = 13.0F;
        float x = screenW / 2.0F - w / 2.0F;
        float y = panelTop - (hasAir ? 29.0F : 19.0F);
        Render2D.drawRoundedRect(x, y, w, h, 4.0F,
                ColorUtil.rgba(8, 8, 12, Math.round(170 * alpha)));
        Fonts.drawCenteredString(Fonts.SF_MEDIUM, name, screenW / 2.0F, y + 2.0F, size,
                ColorUtil.withAlpha(ColorUtil.rgba(240, 242, 248, 255), alpha));
    }
}
