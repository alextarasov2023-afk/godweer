package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import pub.godweer.IMinecraft;
import pub.godweer.util.RenderExtend;
import pub.godweer.util.ServerTags;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
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
 * - Staff detection by tag only: the tab-list tag (or scoreboard team prefix)
 *   must contain one of STAFF_KEYWORDS; nicknames are never matched.
 * - The server tag itself is shown (vanilla font and its own colors), same as NameTags.
 * - Status: ACTIVE (online), NEAR (in loaded chunk range), SPEC (spectator), VANISH (hidden from tab).
 * - Spectator/playing badge instead of a status dot, smooth enter & exit row animations.
 */
public final class StaffHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 4.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ROW_H = 14.0F;
    private static final float HEAD = 10.0F;
    private static final float RADIUS = 7.0F;
    private static final float TAG_SCALE = 0.72F;

    private static final Pattern VALID_NICK = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private enum Status {
        VANISH("V", ColorUtil.rgba(255, 82, 88, 255), 0),
        SPEC("", ColorUtil.rgba(255, 196, 68, 255), 1),
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

    private static final List<String> STAFF_KEYWORDS = List.of(
            "модер", "moder", "сотрудник", "куратор", "curator",
            "админ", "admin", "helper", "хелпер", "овнер", "owner",
            "стажер", "стажёр"
    );

    private static final class StaffRow {
        final String key;
        String name;
        ServerTags.Tag tag;
        Boolean spectator;
        Status status;
        Identifier skin;
        float anim;
        boolean present;

        StaffRow(String key, String name, ServerTags.Tag tag, Boolean spectator, Status status, Identifier skin) {
            this.key = key;
            this.name = name;
            this.tag = tag;
            this.spectator = spectator;
            this.status = status;
            this.skin = skin;
            this.anim = 0.0F;
            this.present = true;
        }
    }

    private final Map<String, StaffRow> rows = new LinkedHashMap<>();
    private final Map<String, ServerTags.Tag> sessionKnownStaff = new LinkedHashMap<>();
    private String lastWorldKey = "";
    private float animatedW = -1.0F;
    private float animatedH = -1.0F;

    public StaffHud() {
        super("staff", "Staff", 495.0F, 68.0F, 152.0F, 74.0F);
    }

    private static boolean isStaffTag(ServerTags.Tag tag) {
        if (tag == null) return false;
        String clean = ServerTags.clean(tag.name()).toLowerCase(Locale.ROOT);
        for (String kw : STAFF_KEYWORDS) {
            if (containsWordOrPrefix(clean, kw)) return true;
        }
        return false;
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

    private static ServerTags.Tag teamTag(PlayerTeam team) {
        ServerTags.Tag tag = ServerTags.team(team);
        return isStaffTag(tag) ? tag : null;
    }

    /** Тег staff-игрока: ключевые слова ищем только в теге, ник не участвует. */
    private static ServerTags.Tag staffTag(PlayerInfo info, String name) {
        if (info == null) return null;
        try {
            ServerTags.Tag tag = ServerTags.tab(info, name);
            if (isStaffTag(tag)) return tag;
        } catch (Exception ignored) {
        }
        try {
            ServerTags.Tag tag = teamTag(info.getTeam());
            if (tag != null) return tag;
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                return teamTag(mc.level.getScoreboard().getPlayersTeam(name));
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
        if (!beginSyncFrame()) return;
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

                    ServerTags.Tag tag = staffTag(info, name);
                    if (tag == null) continue;

                    boolean spec = isSpectatorMode(info, name);
                    Status st;
                    if (spec) {
                        st = Status.SPEC;
                    } else if (isNearInWorld(name, info)) {
                        st = Status.NEAR;
                    } else {
                        st = Status.ACTIVE;
                    }
                    sessionKnownStaff.put(low, tag);
                    upsertRow(low, name, tag, spec, st, resolveSkin(info, name));
                    populatedReal = true;
                }

                // 2) Scan scoreboard teams for vanished staff not in online tab list
                if (mc.level != null) {
                    Scoreboard board = mc.level.getScoreboard();
                    for (PlayerTeam team : board.getPlayerTeams()) {
                        ServerTags.Tag vanishedTag = teamTag(team);
                        if (vanishedTag == null) continue;
                        for (String member : team.getPlayers()) {
                            if (member == null || member.equalsIgnoreCase(self)) continue;
                            String cleanMember = ServerGui.stripCodes(member).trim();
                            if (!VALID_NICK.matcher(cleanMember).matches()) continue;
                            String low = cleanMember.toLowerCase(Locale.ROOT);
                            if (onlineLower.contains(low)) continue;

                            sessionKnownStaff.put(low, vanishedTag);
                            upsertRow(low, cleanMember, vanishedTag, null,
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
            upsertRow("opuser", "opUser", sampleTag("МОДЕР", ColorUtil.rgba(98, 168, 255, 255)),
                    true, Status.SPEC, null);
            upsertRow("s3ko", "S3ko", sampleTag("АДМИН", ColorUtil.rgba(255, 88, 92, 255)),
                    null, Status.VANISH, null);
            upsertRow("devax", "devax", sampleTag("ХЕЛПЕР", ColorUtil.rgba(98, 226, 142, 255)),
                    false, Status.ACTIVE, null);
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

    private static ServerTags.Tag sampleTag(String text, int color) {
        return new ServerTags.Tag(ServerTags.clean(text), color, List.of(new ServerTags.TagRun(text, color)));
    }

    private void upsertRow(String key, String name, ServerTags.Tag tag, Boolean spectator, Status status, Identifier skin) {
        StaffRow row = rows.get(key);
        if (row == null) {
            if (rows.size() >= 14) return;
            row = new StaffRow(key, name, tag, spectator, status, skin);
            rows.put(key, row);
        } else {
            row.name = name;
            row.tag = tag;
            row.spectator = spectator;
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
                .thenComparing(r -> r.name.toLowerCase(Locale.ROOT)));

        float listH = 0.0F;
        float maxRowW = Fonts.SF_MEDIUM.getWidth("Staff", 7.0F) + 26.0F;

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

        float targetW = Math.max(118.0F, maxRowW + PAD_X * 2.0F);
        float targetH = HEADER_H + 2.0F + Math.max(ROW_H, listH) + PAD_BOTTOM;

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawLabel(x, y, width, HEADER_H, "Staff", IconUse.STAFF);

        Render2D.pushScissor(x, y + HEADER_H + 1.0F, width, Math.max(0.0F, height - HEADER_H - 1.0F));

        float cy = y + HEADER_H + 2.0F;
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

            // Server tag exactly as it appears in the server tab list
            if (r.tag != null) {
                cx = drawRowTag(r.tag, cx, midY, a);
            }

            // Right side: spectator/playing badge + optional status label (V / NEAR)
            float rightLimit = x + width - PAD_X;
            if (r.spectator != null) {
                String mode = r.spectator ? "SPEC" : "SURV";
                int modeColor = r.spectator
                        ? ColorUtil.rgba(255, 196, 68, 255)
                        : ColorUtil.rgba(110, 230, 145, 255);
                float mSize = 6.0F;
                float mW = Fonts.SF_MEDIUM.getWidth(mode, mSize);
                float mX = rightLimit - mW;
                Fonts.drawString(Fonts.SF_MEDIUM, mode, mX,
                        midY - Fonts.SF_MEDIUM.textHeight(mSize) / 2.0F + 0.3F,
                        mSize, ColorUtil.withAlpha(modeColor, Math.round(240 * a)));
                rightLimit = mX - 5.0F;
            }

            if (!r.status.label.isEmpty()) {
                float stSize = 6.0F;
                float stW = Fonts.SF_MEDIUM.getWidth(r.status.label, stSize);
                float stX = rightLimit - stW;
                Fonts.drawString(Fonts.SF_MEDIUM, r.status.label, stX,
                        midY - Fonts.SF_MEDIUM.textHeight(stSize) / 2.0F + 0.3F,
                        stSize, ColorUtil.withAlpha(r.status.color, Math.round(240 * a)));
                rightLimit = stX - 5.0F;
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

    private static float tagWidth(ServerTags.Tag tag) {
        float w = 0.0F;
        for (ServerTags.TagRun run : tag.runs()) {
            w += mc.font.width(run.text());
        }
        return w * TAG_SCALE;
    }

    /**
     * Тег рисуем ванильным шрифтом (юникод-фолбэк как в табе): small caps
     * и прочие серверные глифы отсутствуют в нашем MSDF. Куски — своими цветами.
     */
    private static float drawRowTag(ServerTags.Tag tag, float cx, float midY, float alpha) {
        var extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null) return cx;

        float top = midY - mc.font.lineHeight * TAG_SCALE / 2.0F;
        // Карточка и предыдущие строки уже в очереди — сбрасываем, чтобы текст лёг сверху.
        Render2DUtil.flush();
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(cx, top);
        pose.scale(TAG_SCALE, TAG_SCALE);

        float pen = 0.0F;
        for (ServerTags.TagRun run : tag.runs()) {
            int color = run.color() != 0 ? run.color() : Theme.getAccentColor();
            extractor.text(mc.font, run.text(), Math.round(pen), 0,
                    ColorUtil.multiplyAlpha(color, alpha), true);
            pen += mc.font.width(run.text());
        }

        pose.popMatrix();
        return cx + pen * TAG_SCALE + 5.0F;
    }

    private static float measureRowWidth(StaffRow r) {
        float w = 0.0F;
        if (r.skin != null) w += HEAD + 4.5F;
        if (r.tag != null) w += tagWidth(r.tag) + 5.0F;
        w += Fonts.SF_MEDIUM.getWidth(r.name, 7.5F) + 10.0F;
        if (r.spectator != null) {
            w += Fonts.SF_MEDIUM.getWidth("SPEC", 6.0F) + 5.0F;
        }
        if (!r.status.label.isEmpty()) {
            w += Fonts.SF_MEDIUM.getWidth(r.status.label, 6.0F) + 5.0F;
        }
        return w;
    }
}
