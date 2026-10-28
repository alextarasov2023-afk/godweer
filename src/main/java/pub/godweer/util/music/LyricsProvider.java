package pub.godweer.util.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synced lyrics provider backed by the public LRCLIB API
 * (https://lrclib.net), the same open database that powers
 * Spotify-style line-by-line lyrics.
 *
 * <p>Fetched lyrics are cached per track and looked up by the current
 * playback position of {@link MusicService}. All network calls happen on a
 * caller-provided background thread — the render thread only reads the
 * cached result.
 */
public final class LyricsProvider {

    public record LyricLine(long timeMillis, long durationMillis, String text) {
    }

    private static final LyricsProvider INSTANCE = new LyricsProvider();

    public static LyricsProvider getInstance() {
        return INSTANCE;
    }

    private static final String API_BASE = "https://lrclib.net/api";
    private static final int MAX_CACHE = 48;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Map<String, List<LyricLine>> cache = new ConcurrentHashMap<>();
    private volatile String fetchKey;
    private volatile boolean fetching;

    private LyricsProvider() {
    }

    /**
     * Current synced lyrics for the given track, or {@code null} when not
     * (yet) available. Triggers a background fetch when the track is new.
     */
    public List<LyricLine> getSyncedLyrics(String title, String artist) {
        if (title == null || title.isBlank()) return null;
        String key = key(title, artist);
        List<LyricLine> cached = cache.get(key);
        if (cached != null) return cached;

        if (!fetching && !key.equals(fetchKey)) {
            fetching = true;
            fetchKey = key;
            Thread worker = new Thread(() -> {
                List<LyricLine> fetched = fetch(title, artist);
                if (fetched != null && !fetched.isEmpty()) {
                    if (cache.size() > MAX_CACHE) cache.clear();
                    cache.put(key, fetched);
                }
                fetching = false;
            }, "GodWeer-Lyrics-Fetch");
            worker.setDaemon(true);
            worker.start();
        }
        return null;
    }

    /** Index of the line active at the given position, or -1. */
    public static int activeLine(List<LyricLine> lines, long positionMillis) {
        if (lines == null || lines.isEmpty()) return -1;
        int lo = 0, hi = lines.size() - 1, result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMillis() <= positionMillis) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    private List<LyricLine> fetch(String title, String artist) {
        try {
            String query = "track_name=" + enc(title)
                    + (artist != null && !artist.isBlank() ? "&artist_name=" + enc(artist) : "");
            JsonObject body = getJson(API_BASE + "/get?" + query);
            if (body == null) {
                // Exact lookup failed — fall back to fuzzy search.
                JsonArray results = getJsonArray(API_BASE + "/search?q=" + enc(title));
                if (results != null && !results.isEmpty()) {
                    body = results.get(0).getAsJsonObject();
                }
            }
            if (body == null) return List.of();

            JsonElement synced = body.get("syncedLyrics");
            if (synced == null || !synced.isJsonPrimitive()) return List.of();
            List<LyricLine> parsed = parseLrc(synced.getAsString());
            return parsed == null ? List.of() : parsed;
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Parses standard LRC text: {@code [mm:ss.xx] line text}. */
    private static List<LyricLine> parseLrc(String lrc) {
        if (lrc == null || lrc.isBlank()) return null;
        List<long[]> times = new ArrayList<>();
        List<String> pairs = new ArrayList<>();
        for (String rawLine : lrc.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            // [00:12.34][00:15.00]text — multiple stamps per line are allowed.
            java.util.regex.Matcher head2 = java.util.regex.Pattern
                    .compile("^((?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\])+)(.*)$")
                    .matcher(line);
            if (!head2.find()) continue;
            String text = head2.group(2).trim();
            java.util.regex.Matcher stamp2 = java.util.regex.Pattern
                    .compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
                    .matcher(head2.group(1));
            while (stamp2.find()) {
                long minutes = Long.parseLong(stamp2.group(1));
                long seconds = Long.parseLong(stamp2.group(2));
                String fracRaw = stamp2.group(3);
                long fracMillis = 0L;
                if (fracRaw != null) {
                    String padded = (fracRaw + "00").substring(0, 3);
                    fracMillis = Long.parseLong(padded);
                }
                times.add(new long[]{(minutes * 60L + seconds) * 1000L + fracMillis});
                pairs.add(text);
            }
        }
        Integer[] order = new Integer[times.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(times.get(a)[0], times.get(b)[0]));
        List<LyricLine> result = new ArrayList<>(order.length);
        for (int idx : order) {
            result.add(new LyricLine(times.get(idx)[0], 0L, pairs.get(idx)));
        }
        for (int i = 0; i < result.size(); i++) {
            LyricLine cur = result.get(i);
            long next = (i + 1 < result.size()) ? result.get(i + 1).timeMillis() : cur.timeMillis() + 5000L;
            result.set(i, new LyricLine(cur.timeMillis(), Math.max(500L, next - cur.timeMillis()), cur.text()));
        }
        return result;
    }

    private JsonObject getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "GodWeer/1.0 (https://godweer.pub)")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonElement element = JsonParser.parseString(response.body());
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private JsonArray getJsonArray(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "GodWeer/1.0 (https://godweer.pub)")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonElement element = JsonParser.parseString(response.body());
            return element.isJsonArray() ? element.getAsJsonArray() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String key(String title, String artist) {
        return (title.trim() + "||" + (artist == null ? "" : artist.trim())).toLowerCase(Locale.ROOT);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
