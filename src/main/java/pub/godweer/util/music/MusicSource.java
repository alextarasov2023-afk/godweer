package pub.godweer.util.music;

/**
 * A source of "now playing" information.
 *
 * <p>Implementations are polled from a background thread, never from the render
 * thread, because some backends (Windows SMTC) perform blocking native calls.
 */
public interface MusicSource {

    /** Stable id used for priority ordering and diagnostics. */
    String id();

    /** Human readable name shown in the UI. */
    String displayName();

    /** Whether this source is currently able to produce data. */
    boolean isAvailable();

    /**
     * Polls the backend.
     *
     * @return the current track, or {@link MusicSnapshot#EMPTY} when nothing is playing
     */
    MusicSnapshot poll();

    /**
     * Performs a playback action on the currently active session.
     *
     * @return true when the action was handled
     */
    default boolean control(MusicAction action) {
        return false;
    }
}
