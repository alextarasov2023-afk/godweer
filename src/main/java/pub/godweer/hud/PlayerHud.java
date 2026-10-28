package pub.godweer.hud;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.IMinecraft;
import pub.godweer.util.misc.Annstable;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.module.impl.combat.KillAura;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Target HUD (PlayerHud) matching the reference screenshot:
 * - Left: circular player avatar framed by a smooth circular HP progress arc ring
 *   (with dark ring track, trailing damage arc, and main accent arc)
 * - Right Row 1: bold target nickname
 * - Right Row 2: "15HP" text
 * - Right Row 3: 4 dark rounded equipment slots with items or subtle empty-slot dots
 */
public final class PlayerHud extends HudElement implements IMinecraft {

    private static final float PAD = 7.0F;
    private static final float RING_R = 16.5F;
    private static final float RING_THICK = 2.4F;
    private static final float HEAD = 24.0F;
    private static final float RADIUS = 9.0F;
    private static final int RING_TRACK = ColorUtil.rgba(255, 255, 255, 22);
    private static final int SLOT_BG = ColorUtil.rgba(18, 22, 34, 210);
    private static final int SLOT_DOT = ColorUtil.rgba(145, 152, 182, 130);
    private static final int HURT_TINT = 0xFFFF4848;
    private static final int HURT_OVERLAY = ColorUtil.rgba(255, 66, 66, 255);

    private float blush = 0.0F;
    private LivingEntity lastTarget = null;
    private int lastHurtTime = 0;
    private long lastFrameNanos = 0L;

    private float animatedW = -1.0F;
    private float smoothHp = 20.0F;
    private float trailHp = 20.0F;
    private float smoothAbs = 0.0F;

    public PlayerHud() {
        super("player", "Player", 275.0F, 175.0F, 126.0F, 48.0F);
    }

    private LivingEntity resolve() {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le.isAlive() && !le.isDeadOrDying() && le != mc.player) {
            return le;
        }
        KillAura killAura = KillAura.INSTANCE;
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null
                && killAura.getTarget().isAlive() && killAura.getTarget() != mc.player) {
            return killAura.getTarget();
        }
        if (HudManager.editMode()) return mc.player;
        return null;
    }

    @Override
    public boolean shouldRender() {
        return enabled && resolve() != null;
    }

    private static String entityName(LivingEntity t) {
        if (t instanceof AbstractClientPlayer p) return p.getGameProfile().name();
        return t.getName().getString();
    }

    @Override
    public void draw(Render2DEvent event) {
        LivingEntity t = resolve();
        if (t == null) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? (1.0F / 60.0F) : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);

        float maxHp = Math.max(1.0F, t.getMaxHealth());
        float curHp = Mth.clamp(t.getHealth(), 0.0F, maxHp);
        float curAbs = Math.max(0.0F, t.getAbsorptionAmount());

        if (t != lastTarget) {
            lastTarget = t;
            lastHurtTime = 0;
            blush = 0.0F;
            smoothHp = curHp;
            trailHp = curHp;
            smoothAbs = curAbs;
        }

        smoothHp = Mth.lerp(0.22F, smoothHp, curHp);
        if (trailHp < smoothHp) {
            trailHp = smoothHp;
        } else {
            trailHp = Mth.lerp(0.08F, trailHp, smoothHp);
        }
        smoothAbs = Mth.lerp(0.22F, smoothAbs, curAbs);

        String nick = entityName(t);
        int dispHp = (int) Math.ceil(curHp + curAbs);
        String hpLabel = dispHp + "HP";

        // Collect up to 4 equipment/held slots (or show 4 slots with empty indicators like on the photo)
        List<ItemStack> stacks = new ArrayList<>(6);
        addStack(stacks, t.getItemBySlot(EquipmentSlot.HEAD));
        addStack(stacks, t.getItemBySlot(EquipmentSlot.CHEST));
        addStack(stacks, t.getItemBySlot(EquipmentSlot.LEGS));
        addStack(stacks, t.getItemBySlot(EquipmentSlot.FEET));
        addStack(stacks, t.getMainHandItem());
        addStack(stacks, t.getOffhandItem());

        int slotCount = Math.max(4, Math.min(6, stacks.size()));
        float slotSz = 10.5F;
        float slotGap = 3.0F;
        float slotsW = slotCount * slotSz + (slotCount - 1) * slotGap;

        float nickW = Fonts.SF_MEDIUM.getWidth(nick, 9.0F);
        float hpW = Fonts.SF_MEDIUM.getWidth(hpLabel, 7.5F);
        float rightContentW = Math.max(slotsW, Math.max(nickW, hpW)) + 4.0F;

        float leftBlockW = RING_R * 2.0F;
        float targetW = Mth.clamp(PAD * 2.0F + leftBlockW + 8.0F + rightContentW, 118.0F, 195.0F);
        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.25F;

        this.width = animatedW;
        this.height = 46.0F;

        int accent = Theme.getAccentColor();

        HudStyle.drawCard(x, y, width, height, RADIUS);

        // Left circular avatar + circular HP progress ring
        float ringCx = x + PAD + RING_R;
        float ringCy = y + height / 2.0F;

        int hurt = Math.max(0, t.hurtTime);
        if (hurt > lastHurtTime) {
            blush = 1.0F;
        }
        lastHurtTime = hurt;
        blush = Math.max(blush - dt * 2.4F, Annstable.factor(t));
        blush = Mth.clamp(blush, 0.0F, 1.0F);

        float headInset = blush * 1.1F;
        float dSize = HEAD - headInset * 2.0F;
        float dhx = ringCx - dSize / 2.0F;
        float dhy = ringCy - dSize / 2.0F;
        float headRadius = dSize / 2.0F;

        int headTint = ColorUtil.lerp(0xFFFFFFFF, HURT_TINT, blush);
        if (t instanceof AbstractClientPlayer p) {
            try {
                Render2D.drawHead(p.getSkin().body().texturePath(), dhx, dhy, dSize, headRadius, headTint);
            } catch (Exception ignored) {
                Render2D.drawCircle(ringCx, ringCy, headRadius, ColorUtil.rgba(255, 255, 255, 20));
            }
        } else {
            Render2D.drawCircle(ringCx, ringCy, headRadius, ColorUtil.rgba(255, 255, 255, 20));
            String letter = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, letter, ringCx,
                    ringCy - Fonts.SF_MEDIUM.textHeight(10.0F) / 2.0F, 10.0F, HudStyle.TEXT_PRIMARY);
        }

        if (blush > 0.02F) {
            Render2D.drawCircle(ringCx, ringCy, headRadius,
                    ColorUtil.withAlpha(HURT_OVERLAY, Math.round(blush * 115.0F)));
        }

        // Circular HP ring around the circular head
        Render2D.drawArc(ringCx, ringCy, RING_R, RING_THICK, 0.0F, 360.0F, RING_TRACK);

        float trailPct = Mth.clamp(trailHp / maxHp, 0.0F, 1.0F);
        float hpPct = Mth.clamp(smoothHp / maxHp, 0.0F, 1.0F);

        if (trailPct > 0.005F) {
            Render2D.drawArc(ringCx, ringCy, RING_R, RING_THICK, -90.0F, 360.0F * trailPct,
                    ColorUtil.withAlpha(accent, 95));
        }
        if (hpPct > 0.005F) {
            int ringCol = ColorUtil.lerp(ColorUtil.withAlpha(accent, 255), HURT_OVERLAY, blush * 0.45F);
            Render2D.drawArc(ringCx, ringCy, RING_R, RING_THICK, -90.0F, 360.0F * hpPct, ringCol);
        }
        if (smoothAbs > 0.1F) {
            float absPct = Mth.clamp(smoothAbs / 20.0F, 0.0F, 1.0F);
            Render2D.drawArc(ringCx, ringCy, RING_R, RING_THICK, -90.0F, 360.0F * absPct,
                    ColorUtil.rgba(255, 210, 72, 235));
        }

        // Right column: Name -> 15HP -> 4 slots
        float rx = x + PAD + leftBlockW + 8.0F;
        float availRightW = Math.max(30.0F, (x + width - PAD) - rx);

        // Row 1: Target nickname
        float nameY = y + 7.5F;
        String trimmedNick = ServerGui.ellipsize(nick, availRightW, 9.0F);
        Fonts.drawString(Fonts.SF_MEDIUM, trimmedNick, rx, nameY, 9.0F, HudStyle.TEXT_PRIMARY);

        // Row 2: "15HP" right under the nickname
        float hpY = nameY + 10.5F;
        Fonts.drawString(Fonts.SF_MEDIUM, hpLabel, rx, hpY, 7.5F, HudStyle.TEXT_SECONDARY);

        // Row 3: 4 rounded equipment slots with items or subtle center dot when empty
        float iy = hpY + 10.0F;
        var ex = event.getGuiGraphicsExtractor();
        for (int i = 0; i < slotCount; i++) {
            float sx = rx + i * (slotSz + slotGap);
            Render2D.drawRoundedRect(sx, iy, slotSz, slotSz, 2.8F, SLOT_BG);

            ItemStack stack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            if (stack != null && !stack.isEmpty() && ex != null) {
                try {
                    Render2DUtil.flush();
                    ex.pose().pushMatrix();
                    ex.pose().translate(sx + 1.0F, iy + 1.0F);
                    float sc = (slotSz - 2.0F) / 16.0F;
                    ex.pose().scale(sc, sc);
                    ex.item(stack, 0, 0);
                    ex.pose().popMatrix();
                } catch (Exception ignored) {
                }
            } else {
                // Subtle center dot in empty slots just like on the 2nd photo
                Render2D.drawCircle(sx + slotSz / 2.0F, iy + slotSz / 2.0F, 1.25F, SLOT_DOT);
            }
        }
    }

    private static void addStack(List<ItemStack> stacks, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) stacks.add(stack);
    }
}
