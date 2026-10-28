package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.list.Render2DEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Staff list HUD:
 * - Multi-source staff detection:
 *   1) Online tab-list players (prefix/suffix/tab display name)
 *   2) Scoreboard teams (detects vanished staff whose nick remains in a staff team while hidden from tab)
 *   3) Session cache of known staff who went into vanish mid-session
 * - Rich status state: ACTIVE (online & playing), NEAR (in loaded chunk range), SPEC (spectator), VANISH (hidden from tab)
 * - Smooth enter & exit row animations, role badge, player head, and glowing status indicator dot.
 */
public final class StaffHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 5.0F;
    private static final float HEADER_H = 19.0F;
    private static final float ROW_H = 15.0F;
    private static final float HEAD = 10.5F;
    private static final float RADIUS = 8.0F;

    private static final Pattern VALID_NICK = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private enum Status {
        VANISH("V", ColorUtil.rgba(255, 82, 88, 255), 0),
        SPEC("GM3", ColorUtil.rgba(255, 196, 68, 255), 1),
        NEAR("NEAR", ColorUtil.rgba(110, 230, 145, 255), 2),
        ACTIVE("", ColorUtil.rgba(105, 225, 135, 255), 3);

        final String label;
        final int color;
        final int sortOrder;

        Status(String label, int color, int sortOrder) {
            this.label = label;
            this.color = color;
            this.sortOrder = sortOrder;
        }
    }

    private record RoleDef(String tag, int color, int priority, String... keywords) {
    }

    private static final List<RoleDef> ROLE_DEFS = List.of(
            new RoleDef("OWNER", ColorUtil.rgba(255, 78, 88, 255), 0,
                    "owner", "гл.адм", "главный", "создатель", "founder", "владелец"),
            new RoleDef("ADMIN", ColorUtil.rgba(255, 88, 92, 255), 1,
                    "admin", "админ", "администратор", "tech", "тех.адм"),
            new RoleDef("DEV", ColorUtil.rgba(115, 175, 255, 255), 2,
                    "dev", "разраб", "developer", "coder", "кодер"),
            new RoleDef("CURATOR", ColorUtil.rgba(192, 125, 255, 255), 3,
                    "curator", "куратор", "st.moder", "ст.модер", "sr.moder", "srmoder", "гл.модер"),
            new RoleDef("MODER", ColorUtil.rgba(98, 168, 255, 255), 4,
                    "moder", "модер", "мл.модер", "jrmoder", "warden", "staff"),
            new RoleDef("HELPER", ColorUtil.rgba(98, 226, 142, 255), 5,
                    "helper", "хелпер", "хэлпер", "ml.helper", "мл.хелпер", "intern", "стажер", "стажёр"),
            new RoleDef("DESIGN", ColorUtil.rgba(138, 146, 255, 255), 6,
                    "design", "дизайн", "builder", "билдер", "строитель"),
            new RoleDef("MEDIA", ColorUtil.rgba(255, 118, 195, 255), 7,
                    "youtube", "yt", "media", "медиа", "twitch", "tiktok"),
            new RoleDef("TITAN", ColorUtil.rgba(232, 182, 96, 255), 8,
                    "titan", "титан", "d.moder", "д.модер", "d.helper", "д.хелпер", "overlord", "dragon", "imperator", "magister")
    );

    private static final class StaffRow {
        final String key;
        String name;
        String tag;
        int tagColor;
        int rolePriority;
        Status status;
        Identifier skin;
        float anim;
        boolean present;

        StaffRow(String key, String name, String tag, int tagColor, int rolePriority, Status status, Identifier skin) {
            this.key = key;
            this.name = name;
            this.tag = tag;
            this.tagColor = tagColor;
            this.rolePriority = rolePriority;
            this.status = status;
            this.skin = skin;
            this.anim = 0.0F;
            this.present = true;
        }
    }

    private final Map<String, StaffRow> rows = new LinkedHashMap<>();
    private final Map<String, RoleDef> sessionKnownStaff = new LinkedHashMap<>();
    private String lastWorldKey = "";
    private float animatedW = -1.0F;
    private float animatedH = -1.0F;

    public StaffHud() {
        super("staff", "Staff", 495.0F, 68.0F, 152.0F, 74.0F);
    }

    private static RoleDef detectRole(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String clean = ServerGui.stripCodes(raw).toLowerCase(Locale.ROOT);
        for (RoleDef def : ROLE_DEFS) {
            for (String kw : def.keywords) {
                if (containsWordOrPrefix(clean, kw)) {
                    return def;
                }
            }
        }
        return null;
    }

    private static boolean containsWordOrPrefix(String clean, String kw) {
        int idx = clean.indexOf(kw);
        while (idx != -1) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(clean.charAt(idx - 1));
            if (leftOk) return true;
            idx = clean.indexOf(kw, idx + 1);
        }
        return false;
    }

    private static RoleDef detectFromTeam(PlayerTeam team) {
        if (team == null) return null;
        try {
            RoleDef hit = detectRole(team.getPlayerPrefix().getString());
            if (hit != null) return hit;
            hit = detectRole(team.getDisplayName().getString());
            if (hit != null) return hit;
            hit = detectRole(team.getName());
            if (hit != null) return hit;
            hit = detectRole(team.getPlayerSuffix().getString());
            if (hit != null) return hit;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static RoleDef detectFromPlayerInfo(PlayerInfo info, String name) {
        if (info == null) return null;
        try {
            PlayerTeam team = info.getTeam();
            RoleDef hit = detectFromTeam(team);
            if (hit != null) return hit;
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(name);
                RoleDef hit = detectFromTeam(team);
                if (hit != null) return hit;
            }
        } catch (Exception ignored) {
        }
        try {
            var tab = info.getTabListDisplayName();
            if (tab != null) {
                String full = ServerGui.stripCodes(tab.getString());
                int nickIdx = name != null ? full.toLowerCase(Locale.ROOT).indexOf(name.toLowerCase(Locale.ROOT)) : -1;
                String prefixPart = nickIdx > 0 ? full.substring(0, nickIdx) : full;
                RoleDef hit = detectRole(prefixPart);
                if (hit != null) return hit;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isNearInWorld(String name, PlayerInfo info) {
        if (mc.level == null || mc.player == null) return false;
        try {
            if (info != null && info.getProfile().id() != null) {
                var e = mc.level.getPlayerByUUID(info.getProfile().id());
                if (e != null && e != mc.player) return true;
            }
        } catch (Exception ignored) {
        }
        try {
            if (name != null) {
                for (AbstractClientPlayer p : mc.level.players()) {
                    if (p != mc.player && p.getGameProfile().name().equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isSpectatorMode(PlayerInfo info, String name) {
        try {
            if (info != null && info.getGameMode() == GameType.SPECTATOR) return true;
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && info != null && info.getProfile().id() != null) {
                var e = mc.level.getPlayerByUUID(info.getProfile().id());
                if (e != null && e.isSpectator()) return true;
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                for (AbstractClientPlayer p : mc.level.players()) {
                    if (p.getGameProfile().name().equalsIgnoreCase(name)) {
                        return p.isSpectator();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Identifier resolveSkin(PlayerInfo info, String name) {
        try {
            if (info != null && info.getSkin() != null && info.getSkin().body() != null) {
                return info.getSkin().body().texturePath();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && info != null && info.getProfile().id() != null) {
                var e = mc.level.getPlayerByUUID(info.getProfile().id());
                if (e instanceof AbstractClientPlayer ap) return ap.getSkin().body().texturePath();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                for (AbstractClientPlayer p : mc.level.players()) {
                    if (p.getGameProfile().name().equalsIgnoreCase(name)) {
                        return p.getSkin().body().texturePath();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void syncStaffList() {
        String worldKey = mc.level != null ? String.valueOf(System.identityHashCode(mc.level)) : "";
        if (!worldKey.equals(lastWorldKey)) {
            lastWorldKey = worldKey;
            sessionKnownStaff.clear();
        }

        for (StaffRow r : rows.values()) {
            r.present = false;
        }

        boolean populatedReal = false;
        Set<String> onlineLower = new HashSet<>();

        try {
            if (mc.getConnection() != null && mc.player != null) {
                String self = mc.player.getGameProfile().name();
                Collection<PlayerInfo> online = mc.getConnection().getOnlinePlayers();

                // 1) Scan online tab list
                for (PlayerInfo info : online) {
                    String name;
                    try {
                        name = info.getProfile().name();
                    } catch (Exception e) {
                        continue;
                    }
                    if (name == null || name.isEmpty()) continue;
                    String low = name.toLowerCase(Locale.ROOT);
                    onlineLower.add(low);
                    if (name.equalsIgnoreCase(self)) continue;

                    RoleDef role = detectFromPlayerInfo(info, name);
                    if (role == null) continue;

                    sessionKnownStaff.put(low, role);
                    Status st;
                    if (isSpectatorMode(info, name)) {
                        st = Status.SPEC;
                    } else if (isNearInWorld(name, info)) {
                        st = Status.NEAR;
                    } else {
                        st = Status.ACTIVE;
                    }
                    upsertRow(low, name, role.tag, role.color, role.priority, st, resolveSkin(info, name));
                    populatedReal = true;
                }

                // 2) Scan scoreboard teams for vanished staff not in online tab list
                if (mc.level != null) {
                    Scoreboard board = mc.level.getScoreboard();
                    for (PlayerTeam team : board.getPlayerTeams()) {
                        RoleDef teamRole = detectFromTeam(team);
                        if (teamRole == null) continue;
                        for (String member : team.getPlayers()) {
                            if (member == null || member.equalsIgnoreCase(self)) continue;
                            String cleanMember = ServerGui.stripCodes(member).trim();
                            if (!VALID_NICK.matcher(cleanMember).matches()) continue;
                            String low = cleanMember.toLowerCase(Locale.ROOT);
                            if (onlineLower.contains(low)) continue;

                            sessionKnownStaff.put(low, teamRole);
                            upsertRow(low, cleanMember, teamRole.tag, teamRole.color, teamRole.priority,
                                    Status.VANISH, resolveSkin(null, cleanMember));
                            populatedReal = true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Demo preview when chat edit mode is open and no staff are around
        if (!populatedReal && HudManager.editMode()) {
            upsertRow("opuser", "opUser", "TITAN", ColorUtil.rgba(232, 182, 96, 255), 8, Status.SPEC, null);
            upsertRow("s3ko", "S3ko", "ADMIN", ColorUtil.rgba(255, 88, 92, 255), 1, Status.VANISH, null);
            upsertRow("devax", "devax", "DESIGN", ColorUtil.rgba(138, 146, 255, 255), 6, Status.ACTIVE, null);
        }

        // Animate rows & prune exited ones
        Iterator<Map.Entry<String, StaffRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            StaffRow r = it.next().getValue();
            float target = r.present ? 1.0F : 0.0F;
            r.anim += (target - r.anim) * 0.22F;
            if (!r.present && r.anim < 0.02F) {
                it.remove();
            }
        }
    }

    private void upsertRow(String key, String name, String tag, int tagColor, int priority, Status status, Identifier skin) {
        StaffRow row = rows.get(key);
        if (row == null) {
            if (rows.size() >= 14) return;
            row = new StaffRow(key, name, tag, tagColor, priority, status, skin);
            rows.put(key, row);
        } else {
            row.name = name;
            row.tag = tag;
            row.tagColor = tagColor;
            row.rolePriority = priority;
            row.status = status;
            if (skin != null) row.skin = skin;
            row.present = true;
        }
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        if (mc.player == null || mc.getConnection() == null) return false;
        syncStaffList();
        for (StaffRow r : rows.values()) {
            if (r.present || r.anim > 0.03F) return true;
        }
        return false;
    }

    @Override
    public void draw(Render2DEvent event) {
        syncStaffList();

        List<StaffRow> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator
                .comparingInt((StaffRow r) -> r.status.sortOrder)
                .thenComparingInt(r -> r.rolePriority)
                .thenComparing(r -> r.name.toLowerCase(Locale.ROOT)));

        float listH = 0.0F;
        float maxRowW = Fonts.SF_MEDIUM.getWidth("Staff", 8.5F) + 32.0F;

        for (StaffRow r : sorted) {
            listH += ROW_H * r.anim;
            if (r.anim > 0.05F) {
                maxRowW = Math.max(maxRowW, measureRowWidth(r));
            }
        }

        if (sorted.isEmpty() && !HudManager.editMode()) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        float targetW = Math.max(122.0F, maxRowW + PAD_X * 2.0F);
        float targetH = HEADER_H + 4.0F + Math.max(ROW_H, listH) + PAD_BOTTOM;

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawHeader(x, y, width, HEADER_H, "Staff", IconUse.STAFF);

        Render2D.pushScissor(x, y + HEADER_H + 1.0F, width, Math.max(0.0F, height - HEADER_H - 1.0F));

        float cy = y + HEADER_H + 4.0F;
        for (StaffRow r : sorted) {
            float a = r.anim;
            if (a <= 0.03F) continue;
            float rh = ROW_H * a;
            float midY = cy + rh / 2.0F;
            float slide = (1.0F - a) * -6.0F;

            float cx = x + PAD_X + slide;

            // Player head (if skin available)
            if (r.skin != null) {
                Render2D.drawHead(r.skin, cx, midY - HEAD / 2.0F, HEAD, 2.5F,
                        ColorUtil.rgba(255, 255, 255, Math.round(255 * a)));
                cx += HEAD + 4.5F;
            }

            // Role badge (e.g. TITAN / ADMIN / DESIGN)
            float tagSize = 6.5F;
            float tagTextW = Fonts.SF_MEDIUM.getWidth(r.tag, tagSize);
            float tagPadX = 4.0F;
            float tagW = tagTextW + tagPadX * 2.0F;
            float tagH = 9.5F;
            float tagY = midY - tagH / 2.0F;

            Render2D.drawRoundedRectWithOutline(cx, tagY, tagW, tagH, 3.0F,
                    ColorUtil.withAlpha(r.tagColor, Math.round(36 * a)),
                    0.65F,
                    ColorUtil.withAlpha(r.tagColor, Math.round(115 * a)));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, r.tag, cx + tagW / 2.0F,
                    midY - Fonts.SF_MEDIUM.textHeight(tagSize) / 2.0F + 0.35F,
                    tagSize, ColorUtil.withAlpha(r.tagColor, Math.round(255 * a)));

            cx += tagW + 5.0F;

            // Right side: status dot + optional short status badge (V / GM3 / NEAR)
            float dotR = 2.4F;
            float dotCx = x + width - PAD_X - dotR;
            HudStyle.drawStatusDot(dotCx, midY, dotR, r.status.color, a);

            float rightLimit = dotCx - dotR - 4.5F;
            if (!r.status.label.isEmpty()) {
                float stSize = 6.0F;
                float stW = Fonts.SF_MEDIUM.getWidth(r.status.label, stSize) + 5.0F;
                float stH = 8.5F;
                float stX = rightLimit - stW;
                float stY = midY - stH / 2.0F;
                Render2D.drawRoundedRect(stX, stY, stW, stH, 2.5F,
                        ColorUtil.withAlpha(r.status.color, Math.round(34 * a)));
                Fonts.drawCenteredString(Fonts.SF_MEDIUM, r.status.label, stX + stW / 2.0F,
                        midY - Fonts.SF_MEDIUM.textHeight(stSize) / 2.0F + 0.3F,
                        stSize, ColorUtil.withAlpha(r.status.color, Math.round(240 * a)));
                rightLimit = stX - 4.0F;
            }

            // Staff nickname
            float nameSize = 7.5F;
            float availNameW = Math.max(18.0F, rightLimit - cx);
            String shownName = ServerGui.ellipsize(r.name, availNameW, nameSize);
            Fonts.drawString(Fonts.SF_MEDIUM, shownName, cx,
                    midY - Fonts.SF_MEDIUM.textHeight(nameSize) / 2.0F + 0.35F,
                    nameSize, ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, a));

            cy += rh;
        }

        Render2D.popScissor();
    }

    private static float measureRowWidth(StaffRow r) {
        float w = 0.0F;
        if (r.skin != null) w += HEAD + 4.5F;
        w += Fonts.SF_MEDIUM.getWidth(r.tag, 6.5F) + 8.0F + 5.0F;
        w += Fonts.SF_MEDIUM.getWidth(r.name, 7.5F) + 10.0F;
        if (!r.status.label.isEmpty()) {
            w += Fonts.SF_MEDIUM.getWidth(r.status.label, 6.0F) + 9.0F;
        }
        w += 8.0F; // status dot
        return w;
    }
}
