package pub.godweer.hud;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.IMinecraft;
import pub.godweer.util.misc.Annstable;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.module.impl.combat.KillAura;

import java.util.Locale;

/**
 * Target HUD as the reference screenshot draws it: rounded head on the left,
 * nickname and "HP: cur (absorption)" on the right, and one flat bar under them
 * — yellow body for health, lavender tail for absorption. No ring, no slots.
 */
public final class PlayerHud extends HudElement implements IMinecraft {

    private static final float PAD = 6.0F;
    private static final float HEAD = 26.0F;
    private static final float HEAD_R = 5.0F;
    private static final float RADIUS = 6.0F;
    private static final float BAR_H = 6.0F;
    private static final float BAR_W = 64.0F;
    private static final int HURT_TINT = 0xFFFF4848;
    private static final int HURT_OVERLAY = ColorUtil.rgba(255, 66, 66, 255);

    private float blush = 0.0F;
    private LivingEntity lastTarget = null;
    private int lastHurtTime = 0;
    private long lastFrameNanos = 0L;

    private float animatedW = -1.0F;
    private float smoothHp = 20.0F;
    private float smoothAbs = 0.0F;

    public PlayerHud() {
        super("player", "Player", 275.0F, 175.0F, 126.0F, 42.0F);
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
            smoothAbs = curAbs;
        }

        smoothHp = Mth.lerp(0.22F, smoothHp, curHp);
        smoothAbs = Mth.lerp(0.22F, smoothAbs, curAbs);

        String nick = entityName(t);
        String hpLabel = String.format(Locale.ROOT, "HP: %.1f (%.1f)", curHp, curAbs);

        float nickW = Fonts.SF_MEDIUM.getWidth(nick, 9.0F);
        float hpW = Fonts.SF_MEDIUM.getWidth(hpLabel, 7.5F);
        float rightW = Math.max(BAR_W, Math.max(nickW, hpW));

        float targetW = PAD * 2.0F + HEAD + 7.0F + rightW;
        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.25F;

        this.width = animatedW;
        this.height = PAD * 2.0F + HEAD;

        HudStyle.drawCard(x, y, width, height, RADIUS);

        // Head, with the hurt flash riding on the skin tint itself
        float headX = x + PAD;
        float headY = y + (height - HEAD) / 2.0F;
        int hurt = Math.max(0, t.hurtTime);
        if (hurt > lastHurtTime) {
            blush = 1.0F;
        }
        lastHurtTime = hurt;
        blush = Math.max(blush - dt * 2.4F, Annstable.factor(t));
        blush = Mth.clamp(blush, 0.0F, 1.0F);

        int headTint = ColorUtil.lerp(0xFFFFFFFF, HURT_TINT, blush);
        if (t instanceof AbstractClientPlayer p) {
            try {
                Render2D.drawHead(p.getSkin().body().texturePath(), headX, headY, HEAD, HEAD_R, headTint);
            } catch (Exception ignored) {
                Render2D.drawRoundedRect(headX, headY, HEAD, HEAD, HEAD_R,
                        ColorUtil.rgba(255, 255, 255, 20));
            }
        } else {
            Render2D.drawRoundedRect(headX, headY, HEAD, HEAD, HEAD_R, ColorUtil.rgba(255, 255, 255, 20));
            String letter = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, letter, headX + HEAD / 2.0F,
                    headY + HEAD / 2.0F, 10.0F, HudStyle.TEXT_PRIMARY);
        }
        if (blush > 0.02F) {
            Render2D.drawRoundedRect(headX, headY, HEAD, HEAD, HEAD_R,
                    ColorUtil.withAlpha(HURT_OVERLAY, Math.round(blush * 115.0F)));
        }

        float rx = headX + HEAD + 7.0F;
        float availRightW = Math.max(30.0F, (x + width - PAD) - rx);

        Fonts.drawString(Fonts.SF_MEDIUM, ServerGui.ellipsize(nick, availRightW, 9.0F), rx,
                y + 6.0F, 9.0F, HudStyle.TEXT_PRIMARY);
        Fonts.drawString(Fonts.SF_MEDIUM, hpLabel, rx, y + 17.5F, 7.5F, HudStyle.TEXT_SECONDARY);

        // Flat bar: yellow health, lavender absorption continuing after it
        float barX = rx;
        float barY = y + height - PAD - BAR_H;
        float barW = Math.min(BAR_W, availRightW);
        float radius = BAR_H / 2.0F;
        Render2D.drawRoundedRect(barX, barY, barW, BAR_H, radius, HudStyle.TRACK);

        float hpFrac = Mth.clamp(smoothHp / maxHp, 0.0F, 1.0F);
        float absFrac = Mth.clamp(smoothAbs / maxHp, 0.0F, 1.0F - hpFrac);
        float hpW2 = barW * hpFrac;
        if (hpW2 > 0.5F) {
            Render2D.drawRoundedRect(barX, barY, hpW2, BAR_H, radius, HudStyle.HP_FILL);
        }
        float absW = barW * absFrac;
        if (absW > 0.5F) {
            Render2D.drawRoundedRect(barX + hpW2, barY, absW, BAR_H, radius, HudStyle.HP_ABSORB);
        }
    }
}
