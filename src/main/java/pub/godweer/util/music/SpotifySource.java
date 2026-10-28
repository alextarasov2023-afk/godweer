package pub.godweer.util.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Reads the currently playing track from the Spotify Web API.
 *
 * <p>Endpoint: {@code GET /v1/me/player/currently-playing} with the
 * {@code user-read-currently-playing} scope. Requires OAuth credentials that are
 * stored in {@link MusicCredentials}.
 *
 * <p>All requests run on the polling thread, never on the render thread.
 */
public final class SpotifySource implements MusicSource {

    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String NOW_PLAYING_URL = "https://api.spotify.com/v1/me/player/currently-playing";
    private static final String IMAGE_URL = "https://i.scdn.co/image/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile String accessToken = "";
    private volatile long tokenExpiry;

    /** The Web API allows ~180 calls per minute, so poll it less often. */
    private static final long POLL_INTERVAL_MS = 2500L;
    private volatile MusicSnapshot cached = MusicSnapshot.EMPTY;
    private volatile long cachedAt;

    /** Cached playlists, refreshed every 5 minutes. */
    private volatile List<SpotifyPlaylist> playlists = new ArrayList<>();
    private volatile long playlistsCachedAt;

    @Override
    public String id() {
        return "spotify";
    }

    @Override
    public String displayName() {
        return "Spotify";
    }

    @Override
    public boolean isAvailable() {
        return MusicCredentials.getInstance().hasSpotify();
    }

    @Override
    public MusicSnapshot poll() {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return MusicSnapshot.EMPTY;

        long now = System.currentTimeMillis();
        if (now - cachedAt < POLL_INTERVAL_MS) {
            return cached;
        }

        MusicSnapshot result = query();
        cached = result;
        cachedAt = now;
        return result;
    }

    private MusicSnapshot query() {
        try {            String token = validToken();
            if (token == null) return MusicSnapshot.EMPTY;

            HttpRequest request = HttpRequest.newBuilder(URI.create(NOW_PLAYING_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                return MusicSnapshot.EMPTY;
            }
            if (response.statusCode() != 200) {
                return MusicSnapshot.EMPTY;
            }

            JsonElement root = JsonParser.parseString(response.body());
            if (root == null || !root.isJsonObject()) return MusicSnapshot.EMPTY;
            JsonObject obj = root.getAsJsonObject();

            JsonObject item = obj.has("item") && obj.get("item").isJsonObject()
                    ? obj.getAsJsonObject("item") : null;
            if (item == null) return MusicSnapshot.EMPTY;

            String title = optString(item, "name");
            if (title.isEmpty()) return MusicSnapshot.EMPTY;

            String artist = "";
            if (item.has("artists") && item.get("artists").isJsonArray()) {
                JsonArray artists = item.getAsJsonArray("artists");
                StringBuilder sb = new StringBuilder();
                for (JsonElement element : artists) {
                    if (!element.isJsonObject()) continue;
                    String name = optString(element.getAsJsonObject(), "name");
                    if (name.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(name);
                }
                artist = sb.toString();
            }

            long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0L;
            long progress = obj.has("progress_ms") && !obj.get("progress_ms").isJsonNull()
                    ? obj.get("progress_ms").getAsLong() : 0L;
            boolean playing = obj.has("is_playing") && obj.get("is_playing").getAsBoolean();

            byte[] artwork = downloadArtwork(item);

            // Context: playlist/album/artist the track is being played from.
            String contextUri = "";
            String contextType = "";
            if (obj.has("context") && obj.get("context").isJsonObject()) {
                JsonObject context = obj.getAsJsonObject("context");
                contextUri = optString(context, "uri");
                contextType = optString(context, "type");
            }

            return new MusicSnapshot("Spotify", title, artist, artwork, progress, duration, playing,
                    contextUri, contextType);
        } catch (Throwable t) {
            return MusicSnapshot.EMPTY;
        }
    }

    /**
     * Controls playback through the Spotify Web API.
     *
     * <ul>
     *   <li>{@code PUT /v1/me/player/play} - resume</li>
     *   <li>{@code PUT /v1/me/player/pause} - pause</li>
     *   <li>{@code POST /v1/me/player/next} - skip</li>
     *   <li>{@code POST /v1/me/player/previous} - previous</li>
     * </ul>
     */
    @Override
    public boolean control(MusicAction action) {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify() || action == null) return false;

        try {
            String token = validToken();
            if (token == null) return false;

            String path;
            String method;
            switch (action) {
                case PLAY_PAUSE -> {
                    // Resume when paused, pause when playing.
                    path = cached.playing() ? "/v1/me/player/pause" : "/v1/me/player/play";
                    method = "PUT";
                }
                case NEXT -> {
                    path = "/v1/me/player/next";
                    method = "POST";
                }
                case PREVIOUS -> {
                    path = "/v1/me/player/previous";
                    method = "POST";
                }
                default -> {
                    return false;
                }
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.spotify.com" + path))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(8));
            builder.method(method, HttpRequest.BodyPublishers.noBody());

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Fetches the user's playlists, cached for 5 minutes.
     */
    public java.util.List<SpotifyPlaylist> getPlaylists() {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return new java.util.ArrayList<>();

        long now = System.currentTimeMillis();
        if (!playlists.isEmpty() && now - playlistsCachedAt < 300_000L) {
            return new java.util.ArrayList<>(playlists);
        }

        try {
            String token = validToken();
            if (token == null) return new java.util.ArrayList<>();

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/playlists?limit=50"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new java.util.ArrayList<>();

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();

            List<SpotifyPlaylist> result = new ArrayList<>();
            for (JsonElement element : items) {
                if (!element.isJsonObject()) continue;
                JsonObject p = element.getAsJsonObject();
                String id = optString(p, "id");
                String name = optString(p, "name");
                String uri = optString(p, "uri");
                int total = 0;
                if (p.has("tracks") && p.get("tracks").isJsonObject()) {
                    JsonObject t = p.getAsJsonObject("tracks");
                    if (t.has("total")) total = t.get("total").getAsInt();
                }
                String imageUrl = "";
                if (p.has("images") && p.get("images").isJsonArray()) {
                    JsonArray imgs = p.getAsJsonArray("images");
                    if (!imgs.isEmpty() && imgs.get(0).isJsonObject()) {
                        imageUrl = optString(imgs.get(0).getAsJsonObject(), "url");
                    }
                }
                result.add(new SpotifyPlaylist(id, name, uri, total, imageUrl));
            }

            playlists = result;
            playlistsCachedAt = now;
            return new ArrayList<>(result);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /**
     * Fetches the tracks of a playlist.
     */
    public List<SpotifyTrack> getPlaylistTracks(String playlistId) {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return new ArrayList<>();

        try {
            String token = validToken();
            if (token == null) return new ArrayList<>();

            List<SpotifyTrack> result = new ArrayList<>();
            String url = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?limit=50";
            while (url != null) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) break;

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
                for (JsonElement element : items) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    if (!entry.has("track") || !entry.get("track").isJsonObject()) continue;
                    JsonObject tr = entry.getAsJsonObject("track");
                    if (tr.has("is_local") && tr.get("is_local").getAsBoolean()) continue;

                    String name = optString(tr, "name");
                    String uri = optString(tr, "uri");
                    long dur = tr.has("duration_ms") ? tr.get("duration_ms").getAsLong() : 0L;

                    StringBuilder artists = new StringBuilder();
                    if (tr.has("artists") && tr.get("artists").isJsonArray()) {
                        for (JsonElement art : tr.getAsJsonArray("artists")) {
                            if (!art.isJsonObject()) continue;
                            String n = optString(art.getAsJsonObject(), "name");
                            if (n.isEmpty()) continue;
                            if (artists.length() > 0) artists.append(", ");
                            artists.append(n);
                        }
                    }
                    result.add(new SpotifyTrack(name, artists.toString(), uri, dur));
                }

                String next = root.has("next") && !root.get("next").isJsonNull()
                        ? root.get("next").getAsString() : null;
                url = next;
            }
            return result;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /**
     * Plays a playlist from a given track index.
     */
    public boolean playPlaylist(String playlistId, int offset) {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return false;

        try {
            String token = validToken();
            if (token == null) return false;

            JsonObject body = new JsonObject();
            body.addProperty("context_uri", "spotify:playlist:" + playlistId);
            if (offset > 0) {
                JsonObject o = new JsonObject();
                o.addProperty("position", offset);
                body.add("offset", o);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/play"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Plays a single track by URI.
     */
    public boolean playTrack(String uri) {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return false;

        try {
            String token = validToken();
            if (token == null) return false;

            JsonObject body = new JsonObject();
            JsonArray uris = new JsonArray();
            uris.add(uri);
            body.add("uris", uris);

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/play"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Searches tracks by query and returns the first 10 results.
     */
    public List<SpotifyTrack> search(String query) {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return new ArrayList<>();

        try {
            String token = validToken();
            if (token == null) return new ArrayList<>();

            String url = "https://api.spotify.com/v1/search?q=" + urlEncode(query) + "&type=track&limit=10";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new ArrayList<>();

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject tracks = root.has("tracks") ? root.getAsJsonObject("tracks") : null;
            if (tracks == null || !tracks.has("items")) return new ArrayList<>();

            List<SpotifyTrack> result = new ArrayList<>();
            for (JsonElement element : tracks.getAsJsonArray("items")) {
                if (!element.isJsonObject()) continue;
                JsonObject tr = element.getAsJsonObject();
                String name = optString(tr, "name");
                String uri = optString(tr, "uri");
                long dur = tr.has("duration_ms") ? tr.get("duration_ms").getAsLong() : 0L;

                StringBuilder artists = new StringBuilder();
                if (tr.has("artists") && tr.get("artists").isJsonArray()) {
                    for (JsonElement art : tr.getAsJsonArray("artists")) {
                        if (!art.isJsonObject()) continue;
                        String n = optString(art.getAsJsonObject(), "name");
                        if (n.isEmpty()) continue;
                        if (artists.length() > 0) artists.append(", ");
                        artists.append(n);
                    }
                }
                result.add(new SpotifyTrack(name, artists.toString(), uri, dur));
            }
            return result;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** Extracts the largest album image URL and downloads it. */
    private byte[] downloadArtwork(JsonObject item) {
        try {
            if (!item.has("album")) return new byte[0];
            JsonObject album = item.getAsJsonObject("album");
            if (!album.has("images") || !album.get("images").isJsonArray()) return new byte[0];

            JsonArray images = album.getAsJsonArray("images");
            String best = null;
            int bestArea = -1;
            for (JsonElement element : images) {
                if (!element.isJsonObject()) continue;
                JsonObject image = element.getAsJsonObject();
                String url = optString(image, "url");
                if (url.isEmpty()) continue;
                int area = 0;
                if (image.has("width") && image.has("height")) {
                    area = image.get("width").getAsInt() * image.get("height").getAsInt();
                }
                if (area > bestArea) {
                    bestArea = area;
                    best = url;
                }
            }
            if (best == null) return new byte[0];

            HttpRequest request = HttpRequest.newBuilder(URI.create(best))
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

    /** Returns a valid access token, refreshing it when needed. */
    private String validToken() {
        long now = System.currentTimeMillis();
        String token = accessToken;
        if (!token.isEmpty() && now < tokenExpiry - 60_000L) {
            return token;
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        MusicCredentials creds = MusicCredentials.getInstance();
        if (!creds.hasSpotify()) return null;

        try {
            String form = "grant_type=refresh_token"
                    + "&refresh_token=" + urlEncode(creds.getSpotifyRefreshToken())
                    + "&client_id=" + urlEncode(creds.getSpotifyClientId())
                    + "&client_secret=" + urlEncode(creds.getSpotifyClientSecret());

            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("[GodWeer] Spotify token refresh failed: HTTP " + response.statusCode());
                return null;
            }

            JsonElement root = JsonParser.parseString(response.body());
            if (root == null || !root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();

            String newToken = optString(obj, "access_token");
            if (newToken.isEmpty()) return null;

            long expiresIn = obj.has("expires_in") ? obj.get("expires_in").getAsLong() : 3600L;
            accessToken = newToken;
            tokenExpiry = System.currentTimeMillis() + expiresIn * 1000L;
            return accessToken;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
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
