package pub.godweer.auxiliary.client.music;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Background poller that aggregates all {@link MusicSource}s and exposes the
 * current track as an immutable {@link MusicSnapshot}.
 *
 * <p>All native / network calls happen on a dedicated daemon thread. The render
 * thread only reads the cached snapshot, so the HUD can never be stalled by a
 * slow backend.
 */
public final class MusicService {

    private static final MusicService INSTANCE = new MusicService();

    private static final long POLL_INTERVAL_MS = 700L;

    public static MusicService getInstance() {
        return INSTANCE;
    }

    private final List<MusicSource> sources = new CopyOnWriteArrayList<>();

    private volatile MusicSnapshot snapshot = MusicSnapshot.EMPTY;
    private volatile boolean running;

    /** Interpolated playback position, kept in sync with the polled position. */
    private long interpolatedPosition;
    private long lastPollTime;
    private boolean lastPlaying;

    private Thread worker;

    public MusicService() {
        SystemMediaSource system = new SystemMediaSource();
        sources.add(system);
        sources.add(new SpotifySource());
        sources.add(new YandexSource(system));
    }

    public List<MusicSource> getSources() {
        return new ArrayList<>(sources);
    }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "GodWeer-Music-Poll");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        Thread t = worker;
        if (t != null) t.interrupt();
        worker = null;
    }

    private void loop() {
        while (running) {
            try {
                pollOnce();
            } catch (Throwable t) {
                System.err.println("[GodWeer] Music poll failed: " + t);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void pollOnce() {
        MusicSnapshot best = MusicSnapshot.EMPTY;
        int bestPriority = Integer.MAX_VALUE;

        for (MusicSource source : sources) {
            if (!source.isAvailable()) continue;
            MusicSnapshot current;
            try {
                current = source.poll();
            } catch (Throwable t) {
                continue;
            }
            if (current == null || current.isEmpty()) continue;

            int priority = priorityOf(source, current);
            if (priority < bestPriority) {
                bestPriority = priority;
                best = current;
            }
        }

        if (best.isEmpty()) {
            snapshot = MusicSnapshot.EMPTY;
            lastPlaying = false;
            interpolatedPosition = 0L;
            lastPollTime = System.currentTimeMillis();
            return;
        }

        // Reset interpolation when the track changed.
        if (!best.title().equals(snapshot.title()) || !best.artist().equals(snapshot.artist())) {
            interpolatedPosition = best.positionMillis();
            lastPollTime = System.currentTimeMillis();
        } else {
            // Trust the backend but avoid jumping backwards.
            interpolatedPosition = Math.max(interpolatedPosition, best.positionMillis());
            lastPollTime = System.currentTimeMillis();
        }
        lastPlaying = best.playing();
        snapshot = best;
    }

    /**
     * Lower is better. An explicit API source always wins over the generic
     * system session, because it carries exact metadata.
     */
    private static int priorityOf(MusicSource source, MusicSnapshot snapshot) {
        return switch (source.id()) {
            case "spotify" -> 0;
            case "yandex" -> 1;
            default -> 10;
        };
    }

    /**
     * Current track with a smoothly interpolated playback position.
     * Must be called from the render thread.
     */
    public MusicSnapshot current() {
        MusicSnapshot s = snapshot;
        if (s.isEmpty()) return s;

        long position = s.positionMillis();
        if (lastPlaying && s.durationMillis() > 0L) {
            long elapsed = System.currentTimeMillis() - lastPollTime;
            if (elapsed > 0L && elapsed < 5000L) {
                position = Math.min(s.durationMillis(), interpolatedPosition + elapsed);
            }
        }
        return new MusicSnapshot(
                s.source(), s.title(), s.artist(), s.artwork(),
                position, s.durationMillis(), s.playing(),
                s.contextUri(), s.contextType()
        );
    }

    /**
     * Triggers a playback action on the source that currently provides the
     * track. Returns false when no source could handle it.
     */
    public boolean control(MusicAction action) {
        if (action == null) return false;

        // Prefer the same source that is currently feeding the HUD.
        String activeSource = snapshot.source();
        for (MusicSource source : sources) {
            if (!source.isAvailable()) continue;
            if (source.id().equals("spotify") || source.id().equals("yandex")) {
                if (!activeSource.equalsIgnoreCase(source.displayName())) continue;
            }
            try {
                if (source.control(action)) return true;
            } catch (Throwable ignored) {
            }
        }

        // Fall back to any source that accepts the action.
        for (MusicSource source : sources) {
            if (!source.isAvailable()) continue;
            try {
                if (source.control(action)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
