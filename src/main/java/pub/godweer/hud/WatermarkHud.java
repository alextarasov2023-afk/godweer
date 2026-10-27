package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import pub.godweer.IMinecraft;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.client.persiki.Tps;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;
import pub.godweer.enjoy.event.list.Render2DEvent;

import java.util.Locale;

/**
 * Top-left Watermark + Coords/BPS bars:
 * Row 1: [Head] Nick | [FPS] 144fps | [PING] 14ms | [TPS] 20tps
 * Row 2: [COMPASS] x4321 y69 z7534 | [BPS] 0.0 bps
 */
public final class WatermarkHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float SEG_GAP = 7.0F;
    private static final float FONT = 8.0F;
    private static final float ICON = 8.0F;
    private static final float HEAD = 12.0F;
    private static final float TOP_H = 22.0F;
    private static final float SUB_H = 19.0F;
    private static final float ROW_GAP = 4.5F;
    private static final float RADIUS = 7.5F;

    private float animatedTopW = -1.0F;
    private float animatedSubW = -1.0F;

    public WatermarkHud() {
        super("watermark", "Watermark", 12.0F, 12.0F, 220.0F, TOP_H + ROW_GAP + SUB_H);
    }

    private static int getPing() {
        try {
            if (mc.player != null && mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) return Math.max(0, info.getLatency());
            }
        } catch (Exception ignored) {
        }
        return 14;
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
        int accent = Theme.getAccentColor();
        int primary = HudStyle.TEXT_PRIMARY;
        int secondary = HudStyle.TEXT_SECONDARY;

        String nick = mc.player != null ? mc.player.getGameProfile().name() : "Player";
        int fpsVal = mc.player != null ? mc.getFps() : 240;
        int pingVal = getPing();
        float tpsVal = Tps.INSTANCE.effectiveTps();

        String fpsNum = String.valueOf(fpsVal);
        String fpsUnit = "fps";
        String pingNum = String.valueOf(pingVal);
        String pingUnit = "ms";
        String tpsNum = String.format(Locale.ROOT, tpsVal >= 19.95F ? "%.0f" : "%.1f", tpsVal);
        String tpsUnit = "tps";

        float nickW = Fonts.SF_MEDIUM.getWidth(nick, FONT);
        float fpsW = ICON + 3.5F + Fonts.SF_MEDIUM.getWidth(fpsNum, FONT) + Fonts.SF_MEDIUM.getWidth(fpsUnit, 7.5F);
        float pingW = ICON + 3.5F + Fonts.SF_MEDIUM.getWidth(pingNum, FONT) + Fonts.SF_MEDIUM.getWidth(pingUnit, 7.5F);
        float tpsW = ICON + 3.5F + Fonts.SF_MEDIUM.getWidth(tpsNum, FONT) + Fonts.SF_MEDIUM.getWidth(tpsUnit, 7.5F);

        float topContentW = (HEAD + 4.5F + nickW)
                + SEG_GAP * 2.0F + 1.0F
                + fpsW
                + SEG_GAP * 2.0F + 1.0F
                + pingW
                + SEG_GAP * 2.0F + 1.0F
                + tpsW;

        float targetTopW = topContentW + PAD_X * 2.0F;
        if (animatedTopW < 0.0F) animatedTopW = targetTopW;
        animatedTopW += (targetTopW - animatedTopW) * 0.25F;

        // Draw Row 1: Main Watermark Pill
        HudStyle.drawCard(x, y, animatedTopW, TOP_H, RADIUS);

        float cy = y + TOP_H / 2.0F;
        float cx = x + PAD_X;

        // Player head + Nick
        float hy = cy - HEAD / 2.0F;
        if (mc.player != null) {
            try {
                Render2D.drawHead(mc.player.getSkin().body().texturePath(), cx, hy, HEAD, 3.0F);
            } catch (Exception ignored) {
                Render2D.drawRoundedRect(cx, hy, HEAD, HEAD, 3.0F, ColorUtil.withAlpha(accent, 45));
            }
        } else {
            Render2D.drawRoundedRect(cx, hy, HEAD, HEAD, 3.0F, ColorUtil.withAlpha(accent, 45));
        }
        cx += HEAD + 4.5F;

        Fonts.drawString(Fonts.SF_MEDIUM, nick, cx,
                cy - Fonts.SF_MEDIUM.textHeight(FONT) / 2.0F + 0.35F, FONT, primary);
        cx += nickW;

        cx = drawDivider(cx, cy);

        // FPS
        cx = drawMetric(cx, cy, IconUse.FPS, fpsNum, fpsUnit, accent, primary, secondary);
        cx = drawDivider(cx, cy);

        // Ping
        int pingTint = pingVal < 120 ? accent : pingVal < 250 ? ColorUtil.rgba(255, 200, 75, 255) : ColorUtil.rgba(255, 90, 95, 255);
        cx = drawMetric(cx, cy, IconUse.PING, pingNum, pingUnit, pingTint, primary, secondary);
        cx = drawDivider(cx, cy);

        // TPS
        int tpsTint = tpsVal >= 18.0F ? accent : tpsVal >= 14.0F ? ColorUtil.rgba(255, 200, 75, 255) : ColorUtil.rgba(255, 90, 95, 255);
        drawMetric(cx, cy, IconUse.TPS, tpsNum, tpsUnit, tpsTint, primary, secondary);

        // Draw Row 2: Coordinates + BPS Pill right below
        int px = mc.player != null ? (int) Math.floor(mc.player.getX()) : 4321;
        int py = mc.player != null ? (int) Math.floor(mc.player.getY()) : 69;
        int pz = mc.player != null ? (int) Math.floor(mc.player.getZ()) : 7534;
        String xVal = String.valueOf(px);
        String yVal = String.valueOf(py);
        String zVal = String.valueOf(pz);
        String bpsNum = String.format(Locale.ROOT, "%.1f", getBps());
        String bpsUnit = "bps";

        float subY = y + TOP_H + ROW_GAP;
        float subCy = subY + SUB_H / 2.0F;

        float coordTextW = Fonts.SF_MEDIUM.getWidth("x", 7.5F) + 1.0F + Fonts.SF_MEDIUM.getWidth(xVal, 7.5F) + 5.0F
                + Fonts.SF_MEDIUM.getWidth("y", 7.5F) + 1.0F + Fonts.SF_MEDIUM.getWidth(yVal, 7.5F) + 5.0F
                + Fonts.SF_MEDIUM.getWidth("z", 7.5F) + 1.0F + Fonts.SF_MEDIUM.getWidth(zVal, 7.5F);
        float bpsW = ICON + 3.5F + Fonts.SF_MEDIUM.getWidth(bpsNum, 7.5F) + 1.5F + Fonts.SF_MEDIUM.getWidth(bpsUnit, 7.0F);
        float targetSubW = PAD_X * 2.0F + ICON + 4.5F + coordTextW + SEG_GAP * 2.0F + 1.0F + bpsW;

        if (animatedSubW < 0.0F) animatedSubW = targetSubW;
        animatedSubW += (targetSubW - animatedSubW) * 0.25F;

        HudStyle.drawCard(x, subY, animatedSubW, SUB_H, 6.5F);

        float scx = x + PAD_X;
        Fonts.drawIcon(IconUse.COMPASS, scx, subCy - ICON / 2.0F + 0.3F, ICON, ColorUtil.withAlpha(accent, 255));
        scx += ICON + 4.5F;

        scx = drawAxisCoord(scx, subCy, "x", xVal, secondary, primary) + 5.0F;
        scx = drawAxisCoord(scx, subCy, "y", yVal, secondary, primary) + 5.0F;
        scx = drawAxisCoord(scx, subCy, "z", zVal, secondary, primary);

        scx = drawDivider(scx, subCy);

        Fonts.drawIcon(IconUse.BPS, scx, subCy - 7.5F / 2.0F + 0.3F, 7.5F, ColorUtil.withAlpha(accent, 255));
        scx += 7.5F + 3.5F;
        float ty = subCy - Fonts.SF_MEDIUM.textHeight(7.5F) / 2.0F + 0.35F;
        Fonts.drawString(Fonts.SF_MEDIUM, bpsNum, scx, ty, 7.5F, primary);
        scx += Fonts.SF_MEDIUM.getWidth(bpsNum, 7.5F) + 1.5F;
        Fonts.drawString(Fonts.SF_MEDIUM, bpsUnit, scx, ty + 0.2F, 7.0F, secondary);

        this.width = Math.max(animatedTopW, animatedSubW);
        this.height = TOP_H + ROW_GAP + SUB_H;
    }

    private static float drawDivider(float cx, float cy) {
        cx += SEG_GAP;
        Render2D.drawRoundedRect(cx, cy - 4.5F, 1.0F, 9.0F, 0.5F, ColorUtil.rgba(255, 255, 255, 26));
        return cx + 1.0F + SEG_GAP;
    }

    private static float drawMetric(float cx, float cy, IconUse icon, String num, String unit,
                                    int iconColor, int numColor, int unitColor) {
        Fonts.drawIcon(icon, cx, cy - ICON / 2.0F + 0.3F, ICON, ColorUtil.withAlpha(iconColor, 255));
        cx += ICON + 3.5F;
        float ty = cy - Fonts.SF_MEDIUM.textHeight(FONT) / 2.0F + 0.35F;
        Fonts.drawString(Fonts.SF_MEDIUM, num, cx, ty, FONT, numColor);
        cx += Fonts.SF_MEDIUM.getWidth(num, FONT);
        Fonts.drawString(Fonts.SF_MEDIUM, unit, cx + 0.8F, ty + 0.3F, 7.5F, unitColor);
        return cx + 0.8F + Fonts.SF_MEDIUM.getWidth(unit, 7.5F);
    }

    private static float drawAxisCoord(float cx, float cy, String axis, String val, int axisCol, int valCol) {
        float ty = cy - Fonts.SF_MEDIUM.textHeight(7.5F) / 2.0F + 0.35F;
        Fonts.drawString(Fonts.SF_MEDIUM, axis, cx, ty, 7.5F, axisCol);
        cx += Fonts.SF_MEDIUM.getWidth(axis, 7.5F) + 1.0F;
        Fonts.drawString(Fonts.SF_MEDIUM, val, cx, ty, 7.5F, valCol);
        return cx + Fonts.SF_MEDIUM.getWidth(val, 7.5F);
    }
}
