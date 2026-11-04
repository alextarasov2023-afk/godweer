package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.game.Tps;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.list.Render2DEvent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Top-left watermark, laid out the way the reference screenshot reads: every
 * metric lives in its own pill instead of one long bar.
 * Row 1: [logo | clock] [nick | fps | time]
 * Row 2: [compass | x y z] [signal | ping] [hourglass | ticks] [chevrons | bps]
 */
public final class WatermarkHud extends HudElement implements IMinecraft {

    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath("godweer", "images/ui/logo.png");
    /** Mark is 152x128 in the atlas, so the box has to keep that ratio. */
    private static final float LOGO_H = 11.0F;
    private static final float LOGO_W = LOGO_H * 152.0F / 128.0F;

    private static final float FONT = 8.0F;
    private static final float UNIT = 7.5F;
    private static final float ROW_GAP = 4.0F;
    /** Space between an icon and the text it labels, and around a divider. */
    private static final float INNER = 4.0F;
    private static final float AROUND_DIVIDER = 5.0F;

    private static final DateTimeFormatter CLOCK_SHORT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK_FULL = DateTimeFormatter.ofPattern("HH:mm:ss");

    public WatermarkHud() {
        super("watermark", "Watermark", 12.0F, 12.0F, 220.0F, HudStyle.PILL_H * 2.0F + ROW_GAP);
    }

    private static int getPing() {
        try {
            if (mc.player != null && mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) return Math.max(0, info.getLatency());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static double getBps() {
        try {
            if (mc.player != null) {
                double dx = mc.player.getX() - mc.player.xo;
                double dz = mc.player.getZ() - mc.player.zo;
                return Math.hypot(dx, dz) * 20.0D;
            }
        } catch (Exception ignored) {
        }
        return 0.0D;
    }

    @Override
    public void draw(Render2DEvent event) {
        int primary = HudStyle.TEXT_PRIMARY;
        int secondary = HudStyle.TEXT_SECONDARY;

        String nick = mc.player != null ? mc.player.getGameProfile().name() : "Player";
        String fpsVal = String.valueOf(mc.getFps());
        String pingVal = String.valueOf(getPing());
        float tpsVal = Tps.INSTANCE.effectiveTps();
        String ticksVal = String.format(Locale.ROOT, tpsVal >= 19.95F ? "%.0f" : "%.1f", tpsVal);
        String bpsVal = String.format(Locale.ROOT, "%.1f", getBps());
        LocalTime now = LocalTime.now();

        float row1Y = y;
        float row1Cy = row1Y + HudStyle.PILL_H / 2.0F;
        float row2Y = y + HudStyle.PILL_H + ROW_GAP;
        float row2Cy = row2Y + HudStyle.PILL_H / 2.0F;

        // Pill A: logo | HH:mm
        String hhmm = now.format(CLOCK_SHORT);
        float hhmmW = Fonts.SF_MEDIUM.getWidth(hhmm, UNIT);
        float brandW = HudStyle.PILL_PAD * 2.0F + LOGO_W + AROUND_DIVIDER * 2.0F + HudStyle.DIVIDER_W + hhmmW;
        HudStyle.drawPill(x, row1Y, brandW);
        drawLogo(x + HudStyle.PILL_PAD, row1Cy);
        float cx = x + HudStyle.PILL_PAD + LOGO_W + AROUND_DIVIDER;
        cx = HudStyle.drawDivider(cx, row1Cy);
        Fonts.drawString(Fonts.SF_MEDIUM, hhmm, cx + AROUND_DIVIDER, textY(UNIT, row1Cy), UNIT, secondary);

        // Pill B: nick | fps | HH:mm:ss
        String hhmmss = now.format(CLOCK_FULL);
        float nickW = Fonts.SF_MEDIUM.getWidth(nick, FONT);
        float fpsW = Fonts.SF_MEDIUM.getWidth(fpsVal, FONT);
        float unitW = Fonts.SF_MEDIUM.getWidth("Fps", UNIT);
        float clockW = Fonts.SF_MEDIUM.getWidth(hhmmss, FONT);
        float nickSeg = Fonts.iconFittedWidth(IconUse.PLAYER, HudStyle.ICON) + INNER + nickW;
        float fpsSeg = Fonts.iconFittedWidth(IconUse.FPS, HudStyle.ICON) + INNER + fpsW + 2.0F + unitW;
        float clockSeg = Fonts.iconFittedWidth(IconUse.CLOCK, HudStyle.ICON) + INNER + clockW;
        float infoW = HudStyle.PILL_PAD * 2.0F + nickSeg + dividerGap() + fpsSeg
                + dividerGap() + clockSeg;
        float infoX = x + brandW + HudStyle.PILL_GAP;
        HudStyle.drawPill(infoX, row1Y, infoW);

        cx = infoX + HudStyle.PILL_PAD;
        cx = drawIconText(cx, row1Cy, IconUse.PLAYER, nick, FONT, primary, primary);
        cx = HudStyle.drawDivider(cx + HudStyle.PILL_GAP, row1Cy) + HudStyle.PILL_GAP;
        cx = drawIconText(cx, row1Cy, IconUse.FPS, fpsVal, FONT, primary, primary);
        Fonts.drawString(Fonts.SF_MEDIUM, "Fps", cx + 2.0F, textY(UNIT, row1Cy), UNIT, secondary);
        cx += 2.0F + unitW;
        cx = HudStyle.drawDivider(cx + HudStyle.PILL_GAP, row1Cy) + HudStyle.PILL_GAP;
        drawIconText(cx, row1Cy, IconUse.CLOCK, hhmmss, FONT, primary, primary);

        // Row 2: four standalone `icon | value` pills
        int bx = mc.player != null ? (int) Math.floor(mc.player.getX()) : 0;
        int by = mc.player != null ? (int) Math.floor(mc.player.getY()) : 0;
        int bz = mc.player != null ? (int) Math.floor(mc.player.getZ()) : 0;

        float sx = x;
        sx = metricPill(sx, row2Y, row2Cy, IconUse.COMPASS,
                bx + " " + by + " " + bz, primary, primary);
        sx = metricPill(sx, row2Y, row2Cy, IconUse.PING, pingVal + " Ping", primary, secondary);
        sx = metricPill(sx, row2Y, row2Cy, IconUse.HOURGLASS, ticksVal + " Ticks", primary, secondary);
        sx = metricPill(sx, row2Y, row2Cy, IconUse.MOVEMENT, bpsVal + " Bps", primary, secondary);

        this.width = Math.max(brandW + HudStyle.PILL_GAP + infoW, sx - x - HudStyle.PILL_GAP);
        this.height = HudStyle.PILL_H * 2.0F + ROW_GAP;
    }

    /** Row-2 shape: one pill holding `icon | text`. Returns the x after the pill and its gap. */
    private static float metricPill(float pillX, float pillY, float cy, IconUse icon, String text,
                                    int iconColor, int textColor) {
        float w = HudStyle.PILL_PAD * 2.0F + HudStyle.segmentWidth(icon, text, UNIT);
        HudStyle.drawPill(pillX, pillY, w);
        HudStyle.drawSegment(pillX + HudStyle.PILL_PAD, cy, icon, text, UNIT, iconColor, textColor);
        return pillX + w + HudStyle.PILL_GAP;
    }

    /** `icon text` without a divider, used inside pill B. Returns the x after the text. */
    private static float drawIconText(float px, float cy, IconUse icon, String text, float size,
                                      int iconColor, int textColor) {
        Fonts.drawIconFitted(icon, px, cy, HudStyle.ICON, iconColor);
        px += Fonts.iconFittedWidth(icon, HudStyle.ICON) + INNER;
        Fonts.drawString(Fonts.SF_MEDIUM, text, px, textY(size, cy), size, textColor);
        return px + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /** Width a divider plus its breathing room costs inside a pill. */
    private static float dividerGap() {
        return HudStyle.PILL_GAP * 2.0F + HudStyle.DIVIDER_W;
    }

    private static float textY(float size, float cy) {
        return cy - Fonts.SF_MEDIUM.textHeight(size) / 2.0F + 0.35F;
    }

    /**
     * Знак клиента из атласа. Если текстура не поднялась — плоский белый квадрат,
     * чтобы строка не схлопнулась и пилюля осталась нужной ширины.
     */
    private void drawLogo(float left, float cy) {
        float top = cy - LOGO_H / 2.0F;
        try {
            Render2D.drawTexture(LOGO_TEXTURE, left, top, LOGO_W, LOGO_H, 0.0F, 0xFFFFFFFF);
        } catch (Exception ignored) {
            Render2D.drawRoundedRect(left, top, LOGO_W, LOGO_H, 2.0F, ColorUtil.rgba(255, 255, 255, 200));
        }
    }
}
