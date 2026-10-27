package pub.godweer.auxiliary.client.music;

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
import java.util.Locale;

/**
 * Enriches Yandex.Music playback information.
 *
 * <p>Yandex.Music has no public "now playing" REST endpoint - the only official
 * real-time protocol is Ynison (WebSocket + protobuf), which is far too heavy
 * for a HUD. Instead we combine two working pieces:
 *
 * <ol>
 *   <li>The operating system media session (SMTC) tells us <i>that</i> something
 *       is playing and roughly which track - this is what
 *       {@link SystemMediaSource} already provides.</li>
 *   <li>The public Yandex search endpoint resolves the exact track metadata
 *       (real title, artists, duration, cover art). It works without a token.</li>
 * </ol>
 *
 * <p>The result is that a Yandex.Music track played in the desktop app or in the
 * web player shows up with clean metadata and album art.
 */
public final class YandexSource implements MusicSource {

    private static final String SEARCH_URL = "https://api.music.yandex.net/search?text=%s&type=track&page=0";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Shared system session source - reused so the native backend is queried once. */
    private final SystemMediaSource system;

    public YandexSource(SystemMediaSource system) {
        this.system = system;
    }

    /** Caches the last resolved track so we do not hammer the search endpoint. */
    private volatile String cachedQuery = "";
    private volatile MusicSnapshot cachedResult = MusicSnapshot.EMPTY;
    private volatile long cachedAt;

    @Override
    public String id() {
        return "yandex";
    }

    @Override
    public String displayName() {
        return "Yandex.Music";
    }

    @Override
    public boolean isAvailable() {
        return system.isAvailable();
    }

    @Override
    public MusicSnapshot poll() {
        MusicSnapshot raw = system.poll();
        if (raw.isEmpty()) return MusicSnapshot.EMPTY;

        // Only enrich sessions that actually belong to Yandex.Music.
        if (!isYandex(raw.source())) {
            return MusicSnapshot.EMPTY;
        }

        String query = buildQuery(raw);
        if (query.isEmpty()) {
            return raw;
        }

        // Reuse the cached result for a short while to avoid hammering the API.
        long now = System.currentTimeMillis();
        if (query.equals(cachedQuery) && now - cachedAt < 20_000L) {
            MusicSnapshot cached = cachedResult;
            if (!cached.isEmpty()) {
                return new MusicSnapshot(
                        cached.source(), cached.title(), cached.artist(), cached.artwork(),
                        raw.positionMillis(), cached.durationMillis(), raw.playing(),
                        cached.contextUri(), cached.contextType()
                );
            }
        }

        MusicSnapshot resolved = search(query);
        cachedQuery = query;
        cachedAt = now;
        cachedResult = resolved;

        if (resolved.isEmpty()) {
            return raw;
        }

        return new MusicSnapshot(
                resolved.source(), resolved.title(), resolved.artist(), resolved.artwork(),
                raw.positionMillis(), resolved.durationMillis(), raw.playing(),
                resolved.contextUri(), resolved.contextType()
        );
    }

    private static boolean isYandex(String owner) {
        if (owner == null) return false;
        String lower = owner.toLowerCase(Locale.ROOT);
        return lower.contains("яндекс") || lower.contains("yandex");
    }

    private static String buildQuery(MusicSnapshot raw) {
        String title = raw.title();
        String artist = raw.artist();
        if (title.isEmpty()) return "";
        if (artist.isEmpty()) return title;
        return artist + " " + title;
    }

    /** Resolves the track through the public Yandex search endpoint. */
    private MusicSnapshot search(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL.replace("%s", encoded)))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return MusicSnapshot.EMPTY;

            JsonElement root = JsonParser.parseString(response.body());
            if (root == null || !root.isJsonObject()) return MusicSnapshot.EMPTY;
            JsonObject result = root.getAsJsonObject().getAsJsonObject("result");
            if (result == null || !result.has("tracks")) return MusicSnapshot.EMPTY;

            JsonObject tracks = result.getAsJsonObject("tracks");
            if (!tracks.has("results") || !tracks.get("results").isJsonArray()) return MusicSnapshot.EMPTY;

            JsonArray results = tracks.getAsJsonArray("results");
            if (results.isEmpty()) return MusicSnapshot.EMPTY;

            JsonObject track = results.get(0).getAsJsonObject();
            String title = optString(track, "title");
            if (title.isEmpty()) return MusicSnapshot.EMPTY;

            StringBuilder artists = new StringBuilder();
            if (track.has("artists") && track.get("artists").isJsonArray()) {
                for (JsonElement element : track.getAsJsonArray("artists")) {
                    if (!element.isJsonObject()) continue;
                    String name = optString(element.getAsJsonObject(), "name");
                    if (name.isEmpty()) continue;
                    if (artists.length() > 0) artists.append(", ");
                    artists.append(name);
                }
            }

            long duration = track.has("durationMs") ? track.get("durationMs").getAsLong() : 0L;

            String coverUri = optString(track, "coverUri");
            if (coverUri.isEmpty() && track.has("albums") && track.get("albums").isJsonArray()) {
                JsonArray albums = track.getAsJsonArray("albums");
                if (!albums.isEmpty() && albums.get(0).isJsonObject()) {
                    coverUri = optString(albums.get(0).getAsJsonObject(), "coverUri");
                }
            }

            byte[] artwork = downloadCover(coverUri);

            return new MusicSnapshot("Yandex.Music", title, artists.toString(), artwork, 0L, duration, true, "", "");
        } catch (Throwable t) {
            return MusicSnapshot.EMPTY;
        }
    }

    /** Downloads the album art and returns raw bytes (JPEG or PNG). */
    private byte[] downloadCover(String coverUri) {
        if (coverUri == null || coverUri.isEmpty()) return new byte[0];
        try {
            // The URI ends with "/%%" which is a size placeholder.
            String url = coverUri.endsWith("/%%")
                    ? coverUri.substring(0, coverUri.length() - 3) + "300x300"
                    : coverUri;

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) return new byte[0];
            return response.body();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }
}
