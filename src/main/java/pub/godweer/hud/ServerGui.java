package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Custom server GUI in HUD style with custom font: boss bars, tab list, sidebar.
 */
public final class ServerGui implements IMinecraft {

    private static final int BG = ColorUtil.rgba(8, 8, 12, 225);
    private static boolean enabled = false;
    private static final Map<UUID, Float> BOSS_ANIM = new HashMap<>();

    private ServerGui() {
    }

    public static boolean isEnabled() {
        return false;
    }

    public static void setEnabled(boolean enabled) {
        ServerGui.enabled = enabled;
    }

    // § color codes

    public static String stripCodes(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int codeColor(char code, int defColor) {
        return switch (code) {
            case '0' -> ColorUtil.rgba(0, 0, 0, 255);
            case '1' -> ColorUtil.rgba(0, 0, 170, 255);
            case '2' -> ColorUtil.rgba(0, 170, 0, 255);
            case '3' -> ColorUtil.rgba(0, 170, 170, 255);
            case '4' -> ColorUtil.rgba(170, 0, 0, 255);
            case '5' -> ColorUtil.rgba(170, 0, 170, 255);
            case '6' -> ColorUtil.rgba(255, 170, 0, 255);
            case '7' -> ColorUtil.rgba(170, 170, 170, 255);
            case '8' -> ColorUtil.rgba(85, 85, 85, 255);
            case '9' -> ColorUtil.rgba(85, 85, 255, 255);
            case 'a' -> ColorUtil.rgba(85, 255, 85, 255);
            case 'b' -> ColorUtil.rgba(85, 255, 255, 255);
            case 'c' -> ColorUtil.rgba(255, 85, 85, 255);
            case 'd' -> ColorUtil.rgba(255, 85, 255, 255);
            case 'e' -> ColorUtil.rgba(255, 255, 85, 255);
            case 'f' -> ColorUtil.rgba(255, 255, 255, 255);
            case 'r' -> defColor;
            default -> -1;
        };
    }

    public static float coloredWidth(String text, float size) {
        return Fonts.SF_MEDIUM.getWidth(stripCodes(text), size);
    }

    public static void drawColoredString(String text, float x, float y, float size, int defColor) {
        if (text == null || text.isEmpty()) return;
        int col = defColor;
        StringBuilder cur = new StringBuilder();
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (cur.length() > 0) {
                    Fonts.drawString(Fonts.SF_MEDIUM, cur.toString(), cx, y, size, col);
                    cx += Fonts.SF_MEDIUM.getWidth(cur.toString(), size);
                    cur.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(++i));
                int cc = codeColor(code, defColor);
                if (cc != -1) col = cc;
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            Fonts.drawString(Fonts.SF_MEDIUM, cur.toString(), cx, y, size, col);
        }
    }

    public static void drawColoredCentered(String text, float centerX, float y, float size, int defColor) {
        drawColoredString(text, centerX - coloredWidth(text, size) / 2.0F, y, size, defColor);
    }

    public static String ellipsize(String text, float availWidth, float size) {
        if (text == null) return "";
        if (Fonts.SF_MEDIUM.getWidth(text, size) <= availWidth + 0.5F) return text;
        float dotsW = Fonts.SF_MEDIUM.getWidth("...", size);
        if (availWidth <= dotsW + 4.0F) return "...";
        return Fonts.SF_MEDIUM.trimToWidth(text, availWidth - dotsW, size) + "...";
    }

    // Boss bars

    public static void renderBossBars(Map<UUID, LerpingBossEvent> events) {
        if (events == null || events.isEmpty()) return;
        if (mc.player == null) return;
        float screenW = mc.getWindow().getGuiScaledWidth();
        float y = 12.0F;

        for (Map.Entry<UUID, LerpingBossEvent> entry : events.entrySet()) {
            LerpingBossEvent boss = entry.getValue();
            UUID id = entry.getKey();

            float target = boss.getProgress();
            float cur = BOSS_ANIM.getOrDefault(id, target);
            cur += (target - cur) * 0.15F;
            BOSS_ANIM.put(id, cur);

            String name;
            try {
                name = boss.getName().getString();
            } catch (Exception e) {
                name = "Boss";
            }
            float nameW = coloredWidth(name, 8.0F);
            float barW = 180.0F;
            float panelW = Math.max(barW + 20.0F, nameW + 24.0F);
            float panelH = 24.0F;
            float px = (screenW - panelW) / 2.0F;

            Render2D.drawBlur(px, y, panelW, panelH, 6.0F, BG, 1.0F);
            drawColoredCentered(name, screenW / 2.0F, y + 3.5F, 8.0F,
                    ColorUtil.rgba(240, 242, 248, 255));

            float barX = (screenW - barW) / 2.0F;
            float barY = y + 14.5F;
            Render2D.drawRoundedRect(barX, barY, barW, 3.5F, 1.75F, ColorUtil.rgba(255, 255, 255, 16));
            if (cur > 0.005F) {
                int col;
                try {
                    col = bossColor(boss.getColor());
                } catch (Exception e) {
                    col = Theme.getAccentColor();
                }
                Render2D.drawRoundedRect(barX, barY, Math.max(3.5F, barW * cur), 3.5F, 1.75F, col);
            }

            y += panelH + 6.0F;
            if (y >= mc.getWindow().getGuiScaledHeight() / 3.0F) break;
        }

        BOSS_ANIM.keySet().retainAll(events.keySet());
    }

    private static int bossColor(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> 0xFFFF82AA;
            case BLUE -> 0xFF64B4FF;
            case RED -> 0xFFFF5555;
            case GREEN -> 0xFF64FF82;
            case YELLOW -> 0xFFFFE664;
            case PURPLE -> 0xFFB482FF;
            case WHITE -> 0xFFE6E6E6;
        };
    }

    // Tab list

    public static void renderTabList() {
        boolean held;
        try {
            held = mc.options.keyPlayerList.isDown();
        } catch (Exception e) {
            return;
        }
        if (!held || mc.getConnection() == null) return;

        List<PlayerInfo> players;
        try {
            players = new ArrayList<>(mc.getConnection().getOnlinePlayers());
        } catch (Exception e) {
            return;
        }
        players.sort((a, b) -> {
            try {
                return a.getProfile().name().compareToIgnoreCase(b.getProfile().name());
            } catch (Exception e) {
                return 0;
            }
        });
        if (players.size() > 60) players = players.subList(0, 60);
        if (players.isEmpty()) return;
        float maxName = 0.0F;
        List<String> names = new ArrayList<>(players.size());
        for (PlayerInfo p : players) {
            String n;
            try {
                n = p.getProfile().name();
            } catch (Exception e) {
                n = "?";
            }
            names.add(n);
            maxName = Math.max(maxName, coloredWidth(n, 8.0F));
        }
        maxName = Math.min(maxName, 130.0F);

        int rows = Math.min(20, players.size());
        int cols = (players.size() + rows - 1) / rows;
        float colW = maxName + 44.0F;
        float rowH = 13.0F;
        float pad = 8.0F;
        float panelW = pad * 2.0F + cols * colW + (cols - 1) * 8.0F;
        float panelH = pad * 2.0F + 15.0F + rows * rowH;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float px = (screenW - panelW) / 2.0F;
        float py = Math.max(10.0F, (screenH - panelH) / 3.0F);

        Render2D.drawBlur(px, py, panelW, panelH, 8.0F, BG, 1.0F);

        String title = "Players — " + players.size();
        Fonts.drawString(Fonts.SF_MEDIUM, title, px + pad, py + pad - 1.0F, 8.5F,
                ColorUtil.withAlpha(Theme.getAccentColor(), 255));

        int accent = Theme.getAccentColor();
        for (int i = 0; i < players.size(); i++) {
            int col = i / rows;
            int row = i % rows;
            float cx = px + pad + col * (colW + 8.0F);
            float cy = py + pad + 15.0F + row * rowH;

            String letter = names.get(i).isEmpty() ? "?" : names.get(i).substring(0, 1).toUpperCase();
            Render2D.drawRoundedRect(cx, cy + 1.0F, 11.0F, 11.0F, 3.5F, ColorUtil.withAlpha(accent, 50));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, letter, cx + 5.5F, cy + 2.0F, 7.5F,
                    ColorUtil.withAlpha(accent, 255));
            String rawName = names.get(i);
            String stripped = stripCodes(rawName);
            String shown = ellipsize(stripped, maxName, 8.0F);
            if (shown.equals(stripped)) {
                drawColoredString(rawName, cx + 14.0F, cy + 2.5F, 8.0F,
                        ColorUtil.rgba(235, 238, 245, 255));
            } else {
                Fonts.drawString(Fonts.SF_MEDIUM, shown,
                        cx + 14.0F, cy + 2.5F, 8.0F, ColorUtil.rgba(235, 238, 245, 255));
            }

            int ping;
            try {
                ping = players.get(i).getLatency();
            } catch (Exception e) {
                ping = 999;
            }
            drawPingBars(cx + colW - 14.0F, cy + 3.0F, ping);
        }
    }

    private static void drawPingBars(float x, float y, int ping) {
        int filled = ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
        int col = ping < 150 ? ColorUtil.rgba(110, 220, 130, 255)
                : ping < 300 ? ColorUtil.rgba(220, 220, 110, 255)
                : ColorUtil.rgba(240, 110, 110, 255);
        int dim = ColorUtil.rgba(255, 255, 255, 30);
        for (int i = 0; i < 5; i++) {
            float bh = 3.0F + i * 2.0F;
            Render2D.drawRoundedRect(x + i * 3.0F, y + 10.0F - bh, 2.0F, bh, 1.0F,
                    i < filled ? col : dim);
        }
    }

    // Sidebar scoreboard

    public static void renderSidebar() {
        if (mc.level == null || mc.player == null) return;
        Scoreboard board;
        try {
            board = mc.level.getScoreboard();
        } catch (Exception e) {
            return;
        }
        Objective obj;
        try {
            obj = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        } catch (Exception e) {
            return;
        }
        if (obj == null) return;

        String title;
        try {
            title = obj.getDisplayName().getString();
        } catch (Exception e) {
            title = obj.getName();
        }

        List<PlayerScoreEntry> scores;
        try {
            scores = board.listPlayerScores(obj).stream()
                    .filter(e -> {
                        try {
                            return !e.isHidden() && !e.owner().startsWith(Scoreboard.HIDDEN_SCORE_PREFIX);
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
                    .limit(15)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return;
        }
        if (scores.isEmpty()) return;

        float rowH = 12.0F;
        float pad = 8.0F;
        float titleH = 15.0F;
        float nameValGap = 8.0F;
        float maxPanelInner = 280.0F;
        float maxValW = 70.0F;
        float nameSize = 8.0F;

        List<String> rawNames = new ArrayList<>(scores.size());
        List<String> rawVals = new ArrayList<>(scores.size());
        List<String> plainNames = new ArrayList<>(scores.size());
        List<String> plainVals = new ArrayList<>(scores.size());
        for (PlayerScoreEntry e : scores) {
            String name;
            try {
                name = e.ownerName().getString();
            } catch (Exception ex) {
                name = e.owner();
            }
            String val;
            try {
                val = e.formatValue(obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)).getString();
            } catch (Exception ex) {
                val = String.valueOf(e.value());
            }
            if (name == null) name = "";
            if (val == null) val = "";
            rawNames.add(name);
            rawVals.add(val);
            plainNames.add(stripCodes(name));
            plainVals.add(stripCodes(val));
        }

        // Per-row layout: each name gets exactly (panelInner - its own value width - gap).
        // Panel is sized from final strings, so nothing is ever cut mid-word without "...".
        float titleW = coloredWidth(title, 8.5F);
        float inner = Math.min(titleW, maxPanelInner);
        float[] valW = new float[scores.size()];
        for (int i = 0; i < scores.size(); i++) {
            float vw = Math.min(Fonts.SF_MEDIUM.getWidth(plainVals.get(i), nameSize), maxValW);
            valW[i] = plainVals.get(i).isEmpty() ? 0.0F : vw;
            float need = Fonts.SF_MEDIUM.getWidth(plainNames.get(i), nameSize)
                    + (valW[i] > 0.0F ? nameValGap + valW[i] : 0.0F);
            inner = Math.max(inner, Math.min(need, maxPanelInner));
        }
        inner = Math.max(inner, 40.0F);

        String shownTitle = ellipsize(stripCodes(title), inner, 8.5F);
        boolean titleFits = shownTitle.equals(stripCodes(title));

        float panelW = inner + pad * 2.0F;
        float panelH = pad * 2.0F + titleH + scores.size() * rowH;
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float px = screenW - panelW - 8.0F;
        float py = Math.max(8.0F, (screenH - panelH) / 2.0F - 20.0F);

        Render2D.drawBlur(px, py, panelW, panelH, 8.0F, BG, 1.0F);
        if (titleFits) {
            drawColoredCentered(title, px + panelW / 2.0F, py + pad - 1.0F, 8.5F,
                    ColorUtil.withAlpha(Theme.getAccentColor(), 255));
        } else {
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, shownTitle, px + panelW / 2.0F, py + pad - 1.0F, 8.5F,
                    ColorUtil.withAlpha(Theme.getAccentColor(), 255));
        }

        float cy = py + pad + titleH;
        for (int i = 0; i < scores.size(); i++) {
            float avail = inner - (valW[i] > 0.0F ? nameValGap + valW[i] : 0.0F);
            String shown = ellipsize(plainNames.get(i), avail, nameSize);
            if (shown.equals(plainNames.get(i))) {
                drawColoredString(rawNames.get(i), px + pad, cy + 2.0F, nameSize,
                        ColorUtil.rgba(235, 238, 245, 255));
            } else {
                Fonts.drawString(Fonts.SF_MEDIUM, shown,
                        px + pad, cy + 2.0F, nameSize, ColorUtil.rgba(235, 238, 245, 255));
            }
            if (valW[i] > 0.0F) {
                String vShown = ellipsize(plainVals.get(i), valW[i], nameSize);
                float vx = px + panelW - pad - valW[i];
                if (vShown.equals(plainVals.get(i))) {
                    drawColoredString(rawVals.get(i), vx, cy + 2.0F, nameSize,
                            ColorUtil.withAlpha(Theme.getAccentColor(), 255));
                } else {
                    Fonts.drawString(Fonts.SF_MEDIUM, vShown,
                            vx, cy + 2.0F, nameSize, ColorUtil.withAlpha(Theme.getAccentColor(), 255));
                }
            }
            cy += rowH;
        }
    }
}
