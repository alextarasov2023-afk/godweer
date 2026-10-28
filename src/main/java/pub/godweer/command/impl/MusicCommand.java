package pub.godweer.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import pub.godweer.util.music.MusicCredentials;
import pub.godweer.util.music.MusicService;
import pub.godweer.util.music.MusicSource;
import pub.godweer.util.music.MusicSnapshot;
import pub.godweer.util.music.SpotifyAuth;
import pub.godweer.util.music.SpotifyPlaylist;
import pub.godweer.util.music.SpotifySource;
import pub.godweer.util.music.SpotifyTrack;
import pub.godweer.util.game.ChatUtil;
import pub.godweer.command.Command;

import java.util.List;

/**
 * Configures the music HUD sources.
 *
 * <pre>
 *   .music                       - current status
 *   .music spotify login &lt;clientId&gt; &lt;clientSecret&gt;
 *   .music spotify playlists     - list playlists
 *   .music spotify tracks &lt;index&gt;
 *   .music spotify play &lt;playlistIndex&gt; [trackIndex]
 *   .music spotify search &lt;query&gt;
 *   .music spotify playtrack &lt;uri&gt;
 *   .music spotify clear
 *   .music yandex &lt;token&gt;
 *   .music clear
 * </pre>
 */
public class MusicCommand extends Command {

    public MusicCommand() {
        super("music", "Настройка Music HUD");
    }

    @Override
    public List<String> aliases() {
        return List.of("musichud");
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            MusicSnapshot track = MusicService.getInstance().current();
            MusicCredentials creds = MusicCredentials.getInstance();

            if (track.isEmpty()) {
                ChatUtil.info("Сейчас ничего не играет.");
            } else {
                ChatUtil.info("Сейчас играет:");
                ChatUtil.entry(track.title(), track.artist().isEmpty() ? track.source() : track.artist());
                ChatUtil.entry(track.source(), track.durationMillis() > 0
                        ? (track.positionMillis() / 1000) + " / " + (track.durationMillis() / 1000) + " сек"
                        : "длительность неизвестна");
            }

            ChatUtil.info("Источники:");
            ChatUtil.entry("Spotify API", creds.hasSpotify() ? "настроен" : "не настроен");
            ChatUtil.entry("Yandex API", creds.hasYandex() ? "настроен" : "не настроен");
            ChatUtil.entry("Файл с данными", creds.getFile().getAbsolutePath());
            return 1;
        });

        builder.then(LiteralArgumentBuilder.literal("spotify")
                .then(LiteralArgumentBuilder.literal("login")
                        .then(RequiredArgumentBuilder.<Object, String>argument("clientId", StringArgumentType.word())
                                .executes(context -> {
                                    ChatUtil.error("Использование: .music spotify login <clientId> <clientSecret>");
                                    return 0;
                                })
                                .then(RequiredArgumentBuilder.<Object, String>argument("clientSecret", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String clientId = StringArgumentType.getString(context, "clientId");
                                            String clientSecret = StringArgumentType.getString(context, "clientSecret");
                                            ChatUtil.info("Открываю Spotify, подтверди доступ...");
                                            SpotifyAuth.login(clientId, clientSecret);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(LiteralArgumentBuilder.literal("playlists")
                        .executes(context -> {
                            SpotifySource sp = spotify(MusicService.getInstance());
                            if (sp == null) { ChatUtil.error("Spotify не настроен."); return 0; }
                            List<SpotifyPlaylist> list = sp.getPlaylists();
                            if (list.isEmpty()) { ChatUtil.info("Плейлисты не найдены."); return 1; }
                            ChatUtil.info("Твои плейлисты:");
                            for (int i = 0; i < list.size(); i++) {
                                SpotifyPlaylist p = list.get(i);
                                ChatUtil.entry(i + "  " + p.name, p.trackCount + " треков");
                            }
                            ChatUtil.info("Запуск: .music spotify play <номер>");
                            return 1;
                        })
                )
                .then(LiteralArgumentBuilder.literal("tracks")
                        .then(RequiredArgumentBuilder.<Object, String>argument("index", StringArgumentType.word())
                                .executes(context -> {
                                    SpotifySource sp = spotify(MusicService.getInstance());
                                    if (sp == null) { ChatUtil.error("Spotify не настроен."); return 0; }
                                    List<SpotifyPlaylist> list = sp.getPlaylists();
                                    int idx;
                                    try { idx = Integer.parseInt(StringArgumentType.getString(context, "index")); }
                                    catch (Exception e) { ChatUtil.error("Неверный номер."); return 0; }
                                    if (idx < 0 || idx >= list.size()) { ChatUtil.error("Нет такого плейлиста."); return 0; }
                                    List<SpotifyTrack> tracks = sp.getPlaylistTracks(list.get(idx).id);
                                    ChatUtil.info(list.get(idx).name + ":");
                                    for (int i = 0; i < tracks.size(); i++) {
                                        SpotifyTrack t = tracks.get(i);
                                        ChatUtil.entry(i + "  " + t.name, t.artist + " (" + (t.durationMs / 1000) + "s)");
                                    }
                                    ChatUtil.info("Запуск: .music spotify play <плейлист> <трек>");
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.literal("play")
                        .then(RequiredArgumentBuilder.<Object, String>argument("index", StringArgumentType.word())
                                .executes(context -> {
                                    SpotifySource sp = spotify(MusicService.getInstance());
                                    if (sp == null) { ChatUtil.error("Spotify не настроен."); return 0; }
                                    List<SpotifyPlaylist> list = sp.getPlaylists();
                                    int idx;
                                    try { idx = Integer.parseInt(StringArgumentType.getString(context, "index")); }
                                    catch (Exception e) { ChatUtil.error("Неверный номер плейлиста."); return 0; }
                                    if (idx < 0 || idx >= list.size()) { ChatUtil.error("Нет такого плейлиста."); return 0; }
                                    if (sp.playPlaylist(list.get(idx).id, 0)) {
                                        ChatUtil.success("Запущен: " + list.get(idx).name);
                                    } else {
                                        ChatUtil.error("Не удалось запустить (Spotify должен быть активен).");
                                    }
                                    return 1;
                                })
                                .then(RequiredArgumentBuilder.<Object, String>argument("track", StringArgumentType.word())
                                        .executes(context -> {
                                            SpotifySource sp = spotify(MusicService.getInstance());
                                            if (sp == null) { ChatUtil.error("Spotify не настроен."); return 0; }
                                            List<SpotifyPlaylist> list = sp.getPlaylists();
                                            int pIdx;
                                            try { pIdx = Integer.parseInt(StringArgumentType.getString(context, "index")); }
                                            catch (Exception e) { ChatUtil.error("Неверный номер плейлиста."); return 0; }
                                            int tIdx;
                                            try { tIdx = Integer.parseInt(StringArgumentType.getString(context, "track")); }
                                            catch (Exception e) { ChatUtil.error("Неверный номер трека."); return 0; }
                                            if (pIdx < 0 || pIdx >= list.size()) { ChatUtil.error("Нет такого плейлиста."); return 0; }
                                            List<SpotifyTrack> tracks = sp.getPlaylistTracks(list.get(pIdx).id);
                                            if (tIdx < 0 || tIdx >= tracks.size()) { ChatUtil.error("Нет такого трека."); return 0; }
                                            if (sp.playTrack(tracks.get(tIdx).uri)) {
                                                ChatUtil.success("Играет: " + tracks.get(tIdx).name);
                                            } else {
                                                ChatUtil.error("Не удалось запустить.");
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .then(LiteralArgumentBuilder.literal("search")
                        .then(RequiredArgumentBuilder.<Object, String>argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    SpotifySource sp = spotify(MusicService.getInstance());
                                    if (sp == null) { ChatUtil.error("Spotify не настроен."); return 0; }
                                    String query = StringArgumentType.getString(context, "query");
                                    List<SpotifyTrack> tracks = sp.search(query);
                                    if (tracks.isEmpty()) { ChatUtil.info("Ничего не найдено."); return 1; }
                                    ChatUtil.info("Результаты поиска:");
                                    for (int i = 0; i < tracks.size(); i++) {
                                        SpotifyTrack t = tracks.get(i);
                                        ChatUtil.entry(i + "  " + t.name, t.artist);
                                    }
                                    ChatUtil.info("Запуск: .music spotify playtrack <uri>");
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.literal("playtrack")
                        .then(RequiredArgumentBuilder.<Object, String>argument("uri", StringArgumentType.greedyString())
                                .executes(context -> {
                                    SpotifySource sp = spotify(MusicService.getInstance());
                                    if (sp == null) { ChatUtil.error("Spotify не настроен."); return 0; }
                                    String uri = StringArgumentType.getString(context, "uri");
                                    if (sp.playTrack(uri)) {
                                        ChatUtil.success("Запущено.");
                                    } else {
                                        ChatUtil.error("Не удалось запустить (Spotify должен быть активен).");
                                    }
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.literal("clear")
                        .executes(context -> {
                            MusicCredentials.getInstance().setSpotify("", "", "");
                            ChatUtil.success("Spotify API отключен.");
                            return 1;
                        })
                )
        );

        builder.then(LiteralArgumentBuilder.literal("yandex")
                .then(RequiredArgumentBuilder.<Object, String>argument("token", StringArgumentType.greedyString())
                        .executes(context -> {
                            String token = StringArgumentType.getString(context, "token");
                            MusicCredentials.getInstance().setYandexToken(token);
                            ChatUtil.success("Yandex API настроен!");
                            return 1;
                        })
                )
        );

        builder.then(LiteralArgumentBuilder.literal("clear")
                .executes(context -> {
                    MusicCredentials creds = MusicCredentials.getInstance();
                    creds.setSpotify("", "", "");
                    creds.setYandexToken("");
                    ChatUtil.success("Все ключи удалены.");
                    return 1;
                })
        );
    }

    private static SpotifySource spotify(MusicService service) {
        for (MusicSource s : service.getSources()) {
            if (s instanceof SpotifySource sp) return sp;
        }
        return null;
    }
}
