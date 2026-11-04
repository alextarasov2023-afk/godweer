package pub.godweer.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.music.MusicAction;
import pub.godweer.util.music.MusicArtwork;
import pub.godweer.util.music.MusicService;
import pub.godweer.util.music.LyricsProvider;
import pub.godweer.util.music.MusicSnapshot;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.MouseInputEvent;
import pub.godweer.event.list.Render2DEvent;

import java.util.List;
import java.util.Locale;

/**
 * Music HUD:
 * Clean header ("Music" + note icon + divider), album art on the left,
 * track / artist on the right, progress bar, time labels and playback
 * buttons at the bottom.
 *
 * <p>Follows the same visual language as the other HUD elements - glass card,
 * accent rim, MSDF font, soft dividers.
 */
public final class MusicHud extends HudElement implements IMinecraft {

    private static final float PAD_X = 8.0F;
    private static final float PAD_BOTTOM = 6.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ART_SIZE = 30.0F;
    private static final float ART_RADIUS = 6.0F;
    private static final float GAP = 8.0F;
    private static final float BADGE_GAP = 6.0F;
    private static final float PROGRESS_H = 2.5F;
    private static final float PROGRESS_TRACK_H = 2.0F;
    private static final float RADIUS = 7.0F;

    private static final float TITLE_SIZE = 8.0F;
    private static final float ARTIST_SIZE = 7.0F;
    private static final float TIME_SIZE = 6.5F;
    private static final float BADGE_SIZE = 6.0F;

    /** Playback buttons below the progress bar. */
    private static final float BTN_SIZE = 12.0F;
    private static final float BTN_GAP = 8.0F;
    private static final String[] BTN_ICONS = {"images/music/prev.png", "images/music/play.png", "images/music/next.png"};
    private static final MusicAction[] BTN_ACTIONS = {MusicAction.PREVIOUS, MusicAction.PLAY_PAUSE, MusicAction.NEXT};

    /** Maximum width of the card and of the text column, in GUI pixels. */
    private static final float MAX_W = 230.0F;
    private static final float MAX_TEXT_W = 170.0F;

    private float animatedW = -1.0F;
    private float animatedH = -1.0F;
    private float shownProgress;
    private String lastTitle = "";
    private String lastArtist = "";

    /** Click pulse animation for the playback buttons. */
    private int pulseButton = -1;
    private long pulseStart;

    /** Hit box of the album art (used for play/pause). */
    private float artX, artY;

    /** Row where the playback buttons are drawn (card coordinates). */
    private float btnRowY;

    /** Index of the button currently under the cursor, or -1. */
    private int hoveredButton = -1;

    public MusicHud() {
        super("music", "Music", 12.0F, 78.0F, 180.0F, 78.0F);
    }

    private static MusicService service() {
        return MusicService.getInstance();
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        return !service().current().isEmpty();
    }

    @Override
    public void draw(Render2DEvent event) {
        MusicSnapshot track = service().current();

        // Placeholder data while the HUD is being edited, so the element is visible.
        if (track.isEmpty()) {
            if (!HudManager.editMode()) {
                this.width = 0.0F;
                this.height = 0.0F;
                shownProgress = 0.0F;
                lastTitle = "";
                lastArtist = "";
                return;
            }
            track = new MusicSnapshot("Spotify", "Never Gonna Give You Up", "Rick Astley",
                    new byte[0], 62_000L, 212_000L, true, "", "");
        }

        boolean trackChanged = !track.title().equals(lastTitle) || !track.artist().equals(lastArtist);
        if (trackChanged) {
            lastTitle = track.title();
            lastArtist = track.artist();
            shownProgress = 0.0F;
        }

        int accent = Theme.getAccentColor();

        String lyricText = "";
        String nextLyricText = "";
        List<LyricsProvider.LyricLine> lyricLines =
                LyricsProvider.getInstance().getSyncedLyrics(track.title(), track.artist());
        if (lyricLines != null && !lyricLines.isEmpty()) {
            int li = LyricsProvider.activeLine(lyricLines, track.positionMillis());
            if (li >= 0 && !lyricLines.get(li).text().isBlank()) {
                lyricText = lyricLines.get(li).text();
            }
            for (int i = Math.max(0, li + 1); i < lyricLines.size(); i++) {
                if (!lyricLines.get(i).text().isBlank()) {
                    nextLyricText = lyricLines.get(i).text();
                    break;
                }
            }
        }
        if (HudManager.editMode() && lyricText.isEmpty()) {
            lyricText = "Never gonna give you up";
            nextLyricText = "Never gonna let you down";
        }

        // ----- layout -----
        float artX = x + PAD_X;
        float artY = y + HEADER_H + 3.0F;
        this.artX = artX;
        this.artY = artY;

        String badgeText = track.playing() ? track.source() : "Paused";
        boolean showBadge = badgeText != null && !badgeText.isBlank();
        float badgeW = showBadge
                ? Fonts.SF_MEDIUM.getWidth(badgeText, BADGE_SIZE) + 7.0F
                : 0.0F;
        float badgeH = 9.0F;

        float titleW = Fonts.SF_MEDIUM.getWidth(track.title(), TITLE_SIZE);
        float artistW = track.artist().isEmpty()
                ? 0.0F
                : Fonts.SF_MEDIUM.getWidth(track.artist(), ARTIST_SIZE);

        float desiredTextW = Math.min(MAX_TEXT_W, Math.max(titleW, artistW));

        float textRoom = desiredTextW;
        if (showBadge) {
            float total = ART_SIZE + GAP + desiredTextW + BADGE_GAP + badgeW + PAD_X * 2.0F;
            if (total > MAX_W) {
                float room = MAX_W - PAD_X * 2.0F - ART_SIZE - GAP - BADGE_GAP - badgeW;
                if (room >= 24.0F) {
                    textRoom = Math.min(desiredTextW, room);
                } else {
                    showBadge = false;
                    badgeW = 0.0F;
                    textRoom = Math.min(desiredTextW, MAX_W - PAD_X * 2.0F - ART_SIZE - GAP);
                }
            }
        }

        float contentW = ART_SIZE + GAP + textRoom + (showBadge ? BADGE_GAP + badgeW : 0.0F);
        float targetW = Math.max(132.0F, contentW + PAD_X * 2.0F);
        float lyricsWindowH = lyricText.isEmpty() ? 0.0F : (nextLyricText.isEmpty() ? 16.0F : 29.0F);
        float lyricsExtra = lyricsWindowH > 0.0F ? lyricsWindowH + 4.0F : 0.0F;
        float targetH = HEADER_H + 4.0F + ART_SIZE + 5.0F + PROGRESS_TRACK_H + 1.5F + BTN_SIZE + PAD_BOTTOM + lyricsExtra;

        if (animatedW < 0.0F) animatedW = targetW;
        if (animatedH < 0.0F) animatedH = targetH;
        animatedW += (targetW - animatedW) * 0.26F;
        animatedH += (targetH - animatedH) * 0.26F;

        this.width = animatedW;
        this.height = animatedH;

        // ----- card -----
        HudStyle.drawCard(x, y, width, height, RADIUS);
        HudStyle.drawLabel(x, y, width, HEADER_H, "Music", IconUse.MUSIC);

        // ----- playing indicator (pulsing dot in the header) -----
        if (track.playing()) {
            float dotX = x + width - PAD_X - 11.0F;
            float dotY = y + HEADER_H / 2.0F;
            float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 250.0);
            Render2D.drawCircle(dotX, dotY, 2.6F, ColorUtil.withAlpha(accent, (int) (120 * pulse)));
            Render2D.drawCircle(dotX, dotY, 1.6F, ColorUtil.withAlpha(accent, (int) (240 * pulse)));
        }

        // ----- album art -----
        Identifier artworkTexture = MusicArtwork.textureFor(track.artwork());
        if (artworkTexture != null) {
            Render2D.drawTexture(artworkTexture, artX, artY, ART_SIZE, ART_SIZE, ART_RADIUS, 0xFFFFFFFF);
        } else {
            Render2D.drawRoundedRect(artX, artY, ART_SIZE, ART_SIZE, ART_RADIUS,
                    HudStyle.ROW_BG_ACTIVE);
            Fonts.drawIconFittedCentered(IconUse.MUSIC, artX + ART_SIZE / 2.0F, artY + ART_SIZE / 2.0F,
                    ART_SIZE * 0.44F, ColorUtil.withAlpha(accent, 220));
        }

        // ----- text -----
        float textX = artX + ART_SIZE + GAP;
        float titleY = artY + 2.0F;

        int titleColor = track.playing() ? HudStyle.TEXT_PRIMARY : HudStyle.TEXT_DIM;
        int artistColor = track.playing() ? HudStyle.TEXT_SECONDARY : HudStyle.TEXT_DIM;

        String title = ServerGui.ellipsize(track.title(), textRoom, TITLE_SIZE);
        Fonts.drawString(Fonts.SF_MEDIUM, title, textX, titleY, TITLE_SIZE, titleColor);

        if (!track.artist().isEmpty()) {
            float artistY = titleY + Fonts.SF_MEDIUM.textHeight(TITLE_SIZE) + 1.0F;
            String artist = ServerGui.ellipsize(track.artist(), textRoom, ARTIST_SIZE);
            Fonts.drawString(Fonts.SF_MEDIUM, artist, textX, artistY, ARTIST_SIZE, artistColor);
        }

        // ----- source badge (right-aligned on the title line) -----
        if (showBadge) {
            float badgeX = x + width - PAD_X - badgeW;
            float badgeY = artY + 2.0F;

            Render2D.drawRoundedRect(badgeX, badgeY, badgeW, badgeH, 3.0F,
                    HudStyle.PILL_BG);
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, badgeText, badgeX + badgeW / 2.0F,
                    badgeY + (badgeH - Fonts.SF_MEDIUM.textHeight(BADGE_SIZE)) / 2.0F + 0.3F,
                    BADGE_SIZE, ColorUtil.withAlpha(accent, track.playing() ? 235 : 160));
        }

        // ----- progress bar -----
        float barY = artY + ART_SIZE + 5.0F;
        float barW = width - PAD_X * 2.0F;

        float target = track.progress();
        if (trackChanged) shownProgress = target;
        shownProgress += (target - shownProgress) * 0.18F;

        Render2D.drawRoundedRect(x + PAD_X, barY, barW, PROGRESS_TRACK_H, PROGRESS_TRACK_H / 2.0F,
                ColorUtil.rgba(255, 255, 255, 26));

        float fillW = barW * Math.max(0.0F, Math.min(1.0F, shownProgress));
        if (fillW > 0.5F) {
            Render2D.drawRoundedRect(x + PAD_X, barY, fillW, PROGRESS_H, PROGRESS_H / 2.0F,
                    ColorUtil.withAlpha(accent, track.playing() ? 235 : 120));
        }

        // ----- bottom row: time on the left, buttons in the middle, duration on the right -----
        float rowY = barY + PROGRESS_TRACK_H + 2.0F;
        btnRowY = rowY;

        if (track.durationMillis() > 0L) {
            String pos = formatTime(track.positionMillis());
            String dur = formatTime(track.durationMillis());

            float durW = Fonts.SF_MEDIUM.getWidth(dur, TIME_SIZE);
            float timeY = rowY + (BTN_SIZE - Fonts.SF_MEDIUM.textHeight(TIME_SIZE)) / 2.0F;

            Fonts.drawString(Fonts.SF_MEDIUM, pos, x + PAD_X, timeY, TIME_SIZE, HudStyle.TEXT_DIM);
            Fonts.drawString(Fonts.SF_MEDIUM, dur, x + width - PAD_X - durW, timeY, TIME_SIZE, HudStyle.TEXT_DIM);
        }

        // ----- playback buttons -----
        drawButtons(track, accent, rowY);

        // ----- lyrics window -----
        if (!lyricText.isEmpty()) {
            float lyricsY = rowY + BTN_SIZE + 4.0F;
            float lyricsW = width - PAD_X * 2.0F;
            Render2D.drawRoundedRect(x + PAD_X, lyricsY, lyricsW, lyricsWindowH, 4.0F,
                    HudStyle.ROW_BG);

            String shown = ServerGui.ellipsize(lyricText, lyricsW - 10.0F, 7.0F);
            float textY = lyricsY + (nextLyricText.isEmpty()
                    ? (lyricsWindowH - Fonts.SF_MEDIUM.textHeight(7.0F)) / 2.0F + 0.3F
                    : 4.0F);
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, shown, x + width / 2.0F, textY, 7.0F,
                    HudStyle.TEXT_PRIMARY);

            if (!nextLyricText.isEmpty()) {
                String next = ServerGui.ellipsize(nextLyricText, lyricsW - 10.0F, 6.2F);
                Fonts.drawCenteredString(Fonts.SF_MEDIUM, next, x + width / 2.0F,
                        lyricsY + 17.0F, 6.2F, HudStyle.TEXT_DIM);
            }
        }
    }

    /** Draws the prev / play-pause / next buttons and records their hit boxes. */
    private void drawButtons(MusicSnapshot track, int accent, float rowY) {
        hoveredButton = hitTest(HudManager.mouseX(), HudManager.mouseY());

        float rowW = BTN_SIZE * 3.0F + BTN_GAP * 2.0F;
        float startX = x + width / 2.0F - rowW / 2.0F;

        for (int i = 0; i < 3; i++) {
            String icon = BTN_ICONS[i];
            if (i == 1) {
                icon = track.playing() ? "images/music/pause.png" : "images/music/play.png";
            }

            boolean hover = hoveredButton == i;

            // Click pulse: briefly enlarge the clicked button.
            float scale = 1.0F;
            if (pulseButton == i) {
                long elapsed = System.currentTimeMillis() - pulseStart;
                if (elapsed < 180L) {
                    float t = elapsed / 180.0F;
                    scale = 1.0F + 0.35F * (float) Math.sin(t * Math.PI);
                } else {
                    pulseButton = -1;
                }
            }

            int color = ColorUtil.withAlpha(accent, hover ? 255 : (track.playing() ? 210 : 130));

            if (hover) {
                Render2D.drawRoundedRect(startX + i * (BTN_SIZE + BTN_GAP) - 2.0F, rowY - 2.0F,
                        BTN_SIZE + 4.0F, BTN_SIZE + 4.0F, 3.0F, HudStyle.ROW_BG_ACTIVE);
            }

            float dx = startX + i * (BTN_SIZE + BTN_GAP) + (BTN_SIZE * (1.0F - scale)) / 2.0F;
            float dy = rowY + (BTN_SIZE * (1.0F - scale)) / 2.0F;
            Render2D.drawTexture(icon, dx, dy, BTN_SIZE * scale, BTN_SIZE * scale, 0.0F, color);
        }
    }

    /**
     * Handles clicks on the playback buttons.
     */
    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (!enabled) return;
        if (event.getAction() != InputConstants.PRESS) return;
        if (HudManager.editMode()) return;

        MusicSnapshot track = service().current();
        if (track.isEmpty()) return;

        double mx = HudManager.mouseX();
        double my = HudManager.mouseY();

        int hit = hitTest(mx, my);
        if (hit < 0) return;

        if (service().control(BTN_ACTIONS[hit])) {
            pulseButton = hit;
            pulseStart = System.currentTimeMillis();
            event.cancel();
        }
    }

    /**
     * @return index of the button under the cursor, or -1
     */
    private int hitTest(double mouseX, double mouseY) {
        float s = getScale();
        if (s <= 0.0F) return -1;

        // The HUD is drawn with a scale around its own origin.
        float px = x + (float) ((mouseX - x) / s);
        float py = y + (float) ((mouseY - y) / s);

        // Recompute button positions from the card geometry.
        float rowW = BTN_SIZE * 3.0F + BTN_GAP * 2.0F;
        float startX = x + width / 2.0F - rowW / 2.0F;
        float rowY = btnRowY;

        for (int i = 0; i < 3; i++) {
            float bx = startX + i * (BTN_SIZE + BTN_GAP);
            if (px >= bx && px <= bx + BTN_SIZE
                    && py >= rowY && py <= rowY + BTN_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /** Formats milliseconds as {@code m:ss}. */
    private static String formatTime(long millis) {
        if (millis < 0L) millis = 0L;
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
