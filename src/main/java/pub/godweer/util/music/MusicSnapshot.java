package pub.godweer.util.music;

/**
 * Immutable snapshot of the currently playing track.
 *
 * <p>All time values are normalised to <b>milliseconds</b>, because the underlying
 * platform backends disagree: the Windows SMTC backend reports seconds while the
 * Linux MPRIS backend reports milliseconds. See {@link #normalizeMillis}.
 */
public record MusicSnapshot(
        String source,
        String title,
        String artist,
        byte[] artwork,
        long positionMillis,
        long durationMillis,
        boolean playing,
        String contextUri,
        String contextType
) {

    public static final MusicSnapshot EMPTY = new MusicSnapshot("", "", "", new byte[0], 0L, 0L, false, "", "");

    public boolean isEmpty() {
        return title == null || title.isBlank();
    }

    public boolean hasArtwork() {
        return artwork != null && artwork.length > 0;
    }

    public boolean hasContext() {
        return contextUri != null && !contextUri.isBlank();
    }

    /**
     * Normalises a raw platform time value to milliseconds.
     *
     * <p>Windows SMTC reports whole seconds, Linux MPRIS reports milliseconds.
     * We distinguish them heuristically: a value below 1000 cannot be a
     * millisecond duration of a real track, so it must be seconds.
     */
    public static long normalizeMillis(long rawValue, long rawDuration) {
        if (rawValue <= 0) return 0L;
        // If the duration is large enough to be milliseconds, everything is milliseconds.
        if (rawDuration >= 1000L) return rawValue;
        // Otherwise the backend is reporting seconds.
        return rawValue * 1000L;
    }

    public float progress() {
        if (durationMillis <= 0L) return 0.0F;
        return Math.min(1.0F, Math.max(0.0F, (float) positionMillis / (float) durationMillis));
    }
}
