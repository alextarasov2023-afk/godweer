package pub.godweer.auxiliary.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A Spotify track entry returned by the Web API.
 */
public final class SpotifyTrack {
    public final String name;
    public final String artist;
    public final String uri;
    public final long durationMs;

    public SpotifyTrack(String name, String artist, String uri, long durationMs) {
        this.name = name;
        this.artist = artist;
        this.uri = uri;
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return name + " - " + artist;
    }
}
