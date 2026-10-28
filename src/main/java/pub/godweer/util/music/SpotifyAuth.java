package pub.godweer.util.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import pub.godweer.util.game.ChatUtil;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Built-in Spotify OAuth login.
 *
 * <p>Starts a tiny local HTTP server, opens the browser to the Spotify
 * authorisation page and waits for the redirect. The resulting refresh token is
 * stored in {@link MusicCredentials}, so this only has to be done once.
 *
 * <p>The redirect URI {@code http://127.0.0.1:8899/callback} has to be added
 * to the app in the Spotify developer dashboard.
 *
 * <p>A plain {@link ServerSocket} is used instead of {@code com.sun.net.httpserver}
 * so it does not depend on the {@code jdk.httpserver} module being present in
 * the game runtime.
 */
public final class SpotifyAuth {

    private static final int PORT = 8899;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + PORT + "/callback";
    private static final String SCOPE = "user-read-currently-playing user-read-playback-state";
    private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private SpotifyAuth() {
    }

    /**
     * Runs the whole login flow on a background thread and reports the result
     * back into the chat.
     */
    public static void login(String clientId, String clientSecret) {
        Thread thread = new Thread(() -> run(clientId, clientSecret), "Spotify-Auth");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(String clientId, String clientSecret) {
        String state = randomState();

        AtomicReference<String> codeRef = new AtomicReference<>(null);
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        try (ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(180_000);

            String url = AUTHORIZE_URL
                    + "?client_id=" + enc(clientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&scope=" + enc(SCOPE)
                    + "&state=" + enc(state);

            ChatUtil.info("Открываю браузер для входа в Spotify...");
            ChatUtil.entry(url, "Если браузер не открылся - открой ссылку вручную");

            if (!openBrowser(url)) {
                ChatUtil.error("Не удалось открыть браузер. Скопируй ссылку из чата вручную.");
            }

            // Wait for the redirect.
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(15_000);
                String requestLine = readRequestLine(socket.getInputStream());
                String query = "";
                if (requestLine != null) {
                    int idx = requestLine.indexOf('?');
                    if (idx >= 0) {
                        int end = requestLine.indexOf(' ', idx);
                        query = end > idx ? requestLine.substring(idx + 1, end) : requestLine.substring(idx + 1);
                    }
                }
                drainHeaders(socket.getInputStream());

                Map<String, String> params = parseQuery(query);
                if (state.equals(params.get("state"))) {
                    if (params.get("error") != null) {
                        errorRef.set(params.get("error"));
                    } else {
                        codeRef.set(params.get("code"));
                    }
                } else {
                    errorRef.set("state mismatch");
                }

                boolean ok = errorRef.get() == null;
                String html = ok
                        ? "<html><body style='font-family:sans-serif;background:#12141a;color:#eee;padding:40px'>"
                          + "<h2>Login successful</h2><p>You can close this window and go back to the game.</p></body></html>"
                        : "<html><body style='font-family:sans-serif;background:#12141a;color:#eee;padding:40px'>"
                          + "<h2>Login failed</h2><p>You can close this window.</p></body></html>";
                respond(socket, html);
            }

            if (errorRef.get() != null) {
                ChatUtil.error("Ошибка авторизации: " + errorRef.get());
                return;
            }

            String code = codeRef.get();
            if (code == null || code.isEmpty()) {
                ChatUtil.error("Spotify не вернул код авторизации.");
                return;
            }

            String body = "grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret);

            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                ChatUtil.error("Spotify вернул ошибку: HTTP " + response.statusCode());
                return;
            }

            JsonElement root = JsonParser.parseString(response.body());
            if (root == null || !root.isJsonObject()) {
                ChatUtil.error("Не удалось разобрать ответ Spotify.");
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            String refreshToken = obj.has("refresh_token") ? obj.get("refresh_token").getAsString() : "";
            if (refreshToken.isEmpty()) {
                ChatUtil.error("Spotify не вернул refresh token.");
                return;
            }

            MusicCredentials.getInstance().setSpotify(clientId, clientSecret, refreshToken);
            ChatUtil.success("Spotify подключен! Треки появятся в Music HUD.");
        } catch (java.net.SocketTimeoutException e) {
            ChatUtil.error("Время ожидания входа истекло.");
        } catch (Exception e) {
            ChatUtil.error("Ошибка входа в Spotify: " + e.getMessage());
        }
    }

    private static String readRequestLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void drainHeaders(InputStream in) throws IOException {
        String line;
        while ((line = readRequestLine(in)) != null && !line.isEmpty()) {
            // discard
        }
    }

    private static void respond(Socket socket, String html) {
        try {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private static String randomState() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split("&")) {
            int idx = part.indexOf('=');
            if (idx < 0) continue;
            try {
                map.put(URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
