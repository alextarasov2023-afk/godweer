package pub.godweer.auxiliary.client.music;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfoKt;

import java.util.List;

/**
 * Reads the currently playing track from the operating system media session.
 *
 * <p>On Windows this uses SMTC ({@code GlobalSystemMediaTransportControlsSessionManager}),
 * which every media player registers with - Spotify, Yandex.Music, YouTube Music,
 * browsers, VLC. On Linux the same library uses MPRIS over D-Bus.
 *
 * <p>The native backend reports <b>seconds</b> on Windows and <b>milliseconds</b>
 * on Linux, so raw values are normalised by {@link MusicSnapshot#normalizeMillis}.
 */
public final class SystemMediaSource implements MusicSource {

    /** Applications that are known to report a usable media session. */
    private static final String[] PRIORITY_OWNERS = {
            "spotify.exe",
            "yandexmusic.exe",
            "yandex music.exe",
            "music.exe",
            "youtube music.exe",
            "chrome.exe",
            "msedge.exe",
            "firefox.exe",
            "vlc.exe",
            "aimp.exe",
            "foobar2000.exe",
            "itunes.exe",
            "musicbee.exe"
    };

    /** Friendly names for well known players, keyed by the lower case process name. */
    private static final String[][] DISPLAY_NAMES = {
            {"spotify.exe", "Spotify"},
            {"yandexmusic.exe", "Яндекс Музыка"},
            {"yandex music.exe", "Яндекс Музыка"},
            {"youtube music.exe", "YouTube Music"},
            {"music.exe", "Windows Media"},
            {"chrome.exe", "Chrome"},
            {"msedge.exe", "Edge"},
            {"firefox.exe", "Firefox"},
            {"vlc.exe", "VLC"},
            {"aimp.exe", "AIMP"},
            {"foobar2000.exe", "foobar2000"},
            {"itunes.exe", "iTunes"},
            {"musicbee.exe", "MusicBee"},
            {"deezer.exe", "Deezer"},
            {"tidal.exe", "TIDAL"},
            {"winamp.exe", "Winamp"},
    };

    private volatile MediaPlayerInfo playerInfo;
    private volatile boolean initFailed;

    /** Cached result of the last native call, reused for a short while. */
    private volatile MusicSnapshot cached = MusicSnapshot.EMPTY;
    private volatile long cachedAt;

    /** The session behind the cached snapshot, used for playback control. */
    private volatile IMediaSession currentSession;

    private static final long CACHE_MS = 250L;

    @Override
    public String id() {
        return "system";
    }

    @Override
    public String displayName() {
        return "System";
    }

    @Override
    public boolean isAvailable() {
        if (initFailed) return false;
        ensureInit();
        return playerInfo != null;
    }

    private void ensureInit() {
        if (playerInfo != null || initFailed) return;
        synchronized (this) {
            if (playerInfo != null || initFailed) return;
            try {
                // Touching the Kotlin object triggers the native DLL load.
                playerInfo = MediaPlayerInfoKt.getSystemMediaPlayerInfo();
            } catch (Throwable t) {
                initFailed = true;
                System.err.println("[GodWeer] System media session unavailable: " + t);
            }
        }
    }

    @Override
    public MusicSnapshot poll() {
        ensureInit();
        MediaPlayerInfo info = playerInfo;
        if (info == null) return MusicSnapshot.EMPTY;

        // The native call is expensive, so a second consumer within the same
        // poll cycle reuses the previous result.
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS) {
            return cached;
        }

        MusicSnapshot result = query(info);
        cached = result;
        cachedAt = now;
        return result;
    }

    @Override
    public boolean control(MusicAction action) {
        IMediaSession session = currentSession;
        if (session == null || action == null) return false;
        try {
            switch (action) {
                case PLAY_PAUSE -> session.playPause();
                case NEXT -> session.next();
                case PREVIOUS -> session.previous();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private MusicSnapshot query(MediaPlayerInfo info) {
        try {
            List<IMediaSession> sessions = info.getMediaSessions();
            if (sessions == null || sessions.isEmpty()) return MusicSnapshot.EMPTY;

            IMediaSession best = pickSession(sessions);
            if (best == null) {
                currentSession = null;
                return MusicSnapshot.EMPTY;
            }
            currentSession = best;

            MediaInfo media = best.getMedia();
            if (media == null) return MusicSnapshot.EMPTY;

            String title = safe(media.getTitle());
            String artist = safe(media.getArtist());

            // Some players (notably browsers) put everything into the title field.
            if (artist.isEmpty() && title.contains(" - ")) {
                int sep = title.indexOf(" - ");
                artist = title.substring(0, sep).trim();
                title = title.substring(sep + 3).trim();
            }
            if (title.isEmpty()) return MusicSnapshot.EMPTY;

            long rawPosition = media.getPosition();
            long rawDuration = media.getDuration();

            return new MusicSnapshot(
                    displayName(best.getOwner()),
                    title,
                    artist,
                    media.getArtworkPng() == null ? new byte[0] : media.getArtworkPng(),
                    MusicSnapshot.normalizeMillis(rawPosition, rawDuration),
                    MusicSnapshot.normalizeMillis(rawDuration, rawDuration),
                    media.getPlaying(),
                    "",
                    ""
            );
        } catch (Throwable t) {
            // A failing media session must never break the game.
            return MusicSnapshot.EMPTY;
        }
    }

    /**
     * Turns a process name like {@code Spotify.exe} into a readable name like
     * {@code Spotify}. Unknown processes keep their raw name.
     */
    static String displayName(String owner) {
        if (owner == null || owner.isBlank()) return "";
        String lower = owner.toLowerCase(java.util.Locale.ROOT);

        for (String[] entry : DISPLAY_NAMES) {
            if (lower.contains(entry[0])) return entry[1];
        }

        // Unknown process: strip the extension and tidy it up.
        String name = lower.endsWith(".exe") ? lower.substring(0, lower.length() - 4) : lower;
        if (name.isEmpty()) return owner;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Picks the most relevant session: playing ones first, then known players by
     * priority, then anything else that has a title.
     */
    private static IMediaSession pickSession(List<IMediaSession> sessions) {
        IMediaSession fallback = null;
        for (IMediaSession session : sessions) {
            try {
                MediaInfo media = session.getMedia();
                if (media == null) continue;
                if (safe(media.getTitle()).isEmpty()) continue;

                if (media.getPlaying()) {
                    int prio = ownerPriority(session.getOwner());
                    if (prio >= 0) return session;
                    if (fallback == null) fallback = session;
                } else if (fallback == null) {
                    fallback = session;
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    /** @return index in {@link #PRIORITY_OWNERS}, or -1 when unknown */
    private static int ownerPriority(String owner) {
        if (owner == null) return -1;
        String lower = owner.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < PRIORITY_OWNERS.length; i++) {
            if (lower.contains(PRIORITY_OWNERS[i])) return i;
        }
        return -1;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
