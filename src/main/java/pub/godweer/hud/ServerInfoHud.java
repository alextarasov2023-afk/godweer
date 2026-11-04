package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.GameType;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.game.Tps;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.list.Render2DEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server info panel (right side of the screen): server name in the header and
 * flat label/value rows — version, game mode, players, FPS, ping, TPS, mouse
 * sensitivity and the address we are connected to.
 */
public final class ServerInfoHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 6.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ROW_H = 13.0F;
    private static final float RADIUS = 7.0F;
    private static final float LABEL = 7.0F;
    private static final float VALUE = 7.5F;

    public ServerInfoHud() {
        super("server", "Server", 300.0F, 60.0F, 132.0F, HEADER_H + 2.0F + ROW_H * 8 + PAD_BOTTOM);
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

    private static ServerData serverData() {
        try {
            if (mc.getConnection() != null) return mc.getConnection().getServerData();
        } catch (Exception ignored) {
        }
        try {
            return mc.getCurrentServer();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String serverName(ServerData data) {
        try {
            if (data != null && data.name != null && !data.name.isBlank()) {
                return ServerGui.stripCodes(data.name).trim();
            }
        } catch (Exception ignored) {
        }
        return "Server";
    }

    private static String versionText(ServerData data) {
        try {
            if (data != null && data.version != null) {
                String s = ServerGui.stripCodes(data.version.getString()).trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
        }
        try {
            if (data != null && data.protocol != 0) return "proto " + data.protocol;
        } catch (Exception ignored) {
        }
        return "-";
    }

    private static String modeText() {
        try {
            if (mc.player != null && mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) {
                    GameType type = info.getGameMode();
                    if (type != null) {
                        String s = type.getShortDisplayName().getString();
                        if (!s.isBlank()) return s;
                        return type.getName();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "-";
    }

    private static String playersText(ServerData data) {
        try {
            if (data != null && data.players != null && data.players.online() > 0) {
                return data.players.online() + "/" + data.players.max();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.getConnection() != null) {
                return String.valueOf(mc.getConnection().getOnlinePlayers().size());
            }
        } catch (Exception ignored) {
        }
        return "-";
    }

    private static String sensitivityText() {
        try {
            double raw = mc.options.sensitivity().get();
            return String.format(Locale.ROOT, "%.1f%%", raw * 100.0D);
        } catch (Exception ignored) {
        }
        return "-";
    }

    private static String ipText(ServerData data) {
        try {
            if (data != null && data.ip != null && !data.ip.isBlank()) return data.ip;
        } catch (Exception ignored) {
        }
        return "-";
    }

    private List<String[]> rows(ServerData data) {
        List<String[]> rows = new ArrayList<>(7);
        rows.add(new String[]{"Version", versionText(data)});
        rows.add(new String[]{"Mode", modeText()});
        rows.add(new String[]{"Players", playersText(data)});
        rows.add(new String[]{"FPS", String.valueOf(mc.player != null ? mc.getFps() : 0)});
        rows.add(new String[]{"Ping", getPing() + "ms"});
        float tps = Tps.INSTANCE.effectiveTps();
        rows.add(new String[]{"TPS", String.format(Locale.ROOT, tps >= 19.95F ? "%.0f" : "%.1f", tps)});
        rows.add(new String[]{"Sens", sensitivityText()});
        rows.add(new String[]{"IP", ipText(data)});
        return rows;
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        return mc.player != null && mc.getConnection() != null;
    }

    @Override
    public void draw(Render2DEvent event) {
        ServerData data = serverData();
        String title = serverName(data);
        List<String[]> rows = rows(data);

        float contentW = Fonts.SF_MEDIUM.getWidth(title, 7.0F) + 26.0F;
        for (String[] row : rows) {
            float w = Fonts.SF_MEDIUM.getWidth(row[0], LABEL) + 10.0F
                    + Fonts.SF_MEDIUM.getWidth(row[1], VALUE);
            contentW = Math.max(contentW, w);
        }
        this.width = Math.max(118.0F, Math.min(190.0F, contentW + PAD_X * 2.0F));
        this.height = HEADER_H + 2.0F + ROW_H * rows.size() + PAD_BOTTOM;

        // Панель прижимается к правому краю, чтобы широкие значения не уехали за экран.
        float screenW = mc.getWindow().getGuiScaledWidth();
        this.x = Math.max(0.0F, Math.min(screenW - getScaledWidth() - 4.0F, this.x));

        int accent = Theme.getAccentColor();

        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawLabel(x, y, width, HEADER_H, title, IconUse.WORLD);

        float cy = y + HEADER_H + 2.0F;
        for (String[] row : rows) {
            float midY = cy + ROW_H / 2.0F;

            Fonts.drawString(Fonts.SF_MEDIUM, row[0], x + PAD_X,
                    midY - Fonts.SF_MEDIUM.textHeight(LABEL) / 2.0F + 0.35F,
                    LABEL, HudStyle.TEXT_SECONDARY);

            int valueColor = "IP".equals(row[0]) ? accent
                    : "Ping".equals(row[0]) ? pingColor(row[1])
                    : "TPS".equals(row[0]) ? tpsColor(row[1])
                    : HudStyle.TEXT_PRIMARY;

            float avail = Math.max(24.0F, width - PAD_X * 2.0F
                    - Fonts.SF_MEDIUM.getWidth(row[0], LABEL) - 10.0F);
            String value = ServerGui.ellipsize(row[1], avail, VALUE);
            float vx = x + width - PAD_X - Fonts.SF_MEDIUM.getWidth(value, VALUE);
            Fonts.drawString(Fonts.SF_MEDIUM, value, vx,
                    midY - Fonts.SF_MEDIUM.textHeight(VALUE) / 2.0F + 0.35F, VALUE, valueColor);

            cy += ROW_H;
        }
    }

    private static int pingColor(String value) {
        try {
            int ms = Integer.parseInt(value.replace("ms", "").trim());
            if (ms < 120) return HudStyle.TEXT_PRIMARY;
            return ms < 250 ? ColorUtil.rgba(255, 200, 75, 255) : ColorUtil.rgba(255, 90, 95, 255);
        } catch (Exception ignored) {
        }
        return HudStyle.TEXT_PRIMARY;
    }

    private static int tpsColor(String value) {
        try {
            float tps = Float.parseFloat(value);
            if (tps >= 18.0F) return HudStyle.TEXT_PRIMARY;
            return tps >= 14.0F ? ColorUtil.rgba(255, 200, 75, 255) : ColorUtil.rgba(255, 90, 95, 255);
        } catch (Exception ignored) {
        }
        return HudStyle.TEXT_PRIMARY;
    }
}
