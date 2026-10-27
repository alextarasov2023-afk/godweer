package pub.godweer.auxiliary.client.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stores API credentials for the music providers.
 *
 * <p>Kept in a separate file ({@code ~/femboy/music.json}) instead of the shared
 * {@code .ww} config, because tokens are secrets and configs are meant to be
 * copied between accounts.
 */
public final class MusicCredentials {

    private static final MusicCredentials INSTANCE = new MusicCredentials();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    // Spotify
    private String spotifyClientId = "";
    private String spotifyClientSecret = "";
    private String spotifyRefreshToken = "";

    // Yandex
    private String yandexToken = "";

    private MusicCredentials() {
        File home = new File(System.getProperty("user.home", "."), "femboy");
        this.file = home.toPath().resolve("music.json");
        load();
    }

    public static MusicCredentials getInstance() {
        return INSTANCE;
    }

    public String getSpotifyClientId() {
        return spotifyClientId;
    }

    public String getSpotifyClientSecret() {
        return spotifyClientSecret;
    }

    public String getSpotifyRefreshToken() {
        return spotifyRefreshToken;
    }

    public boolean hasSpotify() {
        return !spotifyClientId.isBlank()
                && !spotifyClientSecret.isBlank()
                && !spotifyRefreshToken.isBlank();
    }

    public String getYandexToken() {
        return yandexToken;
    }

    public boolean hasYandex() {
        return !yandexToken.isBlank();
    }

    public void setSpotify(String clientId, String clientSecret, String refreshToken) {
        this.spotifyClientId = nz(clientId);
        this.spotifyClientSecret = nz(clientSecret);
        this.spotifyRefreshToken = nz(refreshToken);
        save();
    }

    public void setYandexToken(String token) {
        this.yandexToken = nz(token);
        save();
    }

    public File getFile() {
        return file.toFile();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();

            if (obj.has("spotify")) {
                JsonObject sp = obj.getAsJsonObject("spotify");
                spotifyClientId = str(sp, "clientId");
                spotifyClientSecret = str(sp, "clientSecret");
                spotifyRefreshToken = str(sp, "refreshToken");
            }
            if (obj.has("yandex")) {
                JsonObject ya = obj.getAsJsonObject("yandex");
                yandexToken = str(ya, "token");
            }
        } catch (Exception e) {
            System.err.println("[GodWeer] Failed to read music credentials: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();

            JsonObject sp = new JsonObject();
            sp.addProperty("clientId", spotifyClientId);
            sp.addProperty("clientSecret", spotifyClientSecret);
            sp.addProperty("refreshToken", spotifyRefreshToken);
            root.add("spotify", sp);

            JsonObject ya = new JsonObject();
            ya.addProperty("token", yandexToken);
            root.add("yandex", ya);

            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[GodWeer] Failed to save music credentials: " + e.getMessage());
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
