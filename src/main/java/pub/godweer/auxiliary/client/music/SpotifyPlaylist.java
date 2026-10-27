package pub.godweer.auxiliary.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A Spotify playlist entry returned by the Web API.
 */
public final class SpotifyPlaylist {
    public final String id;
    public final String name;
    public final String uri;
    public final int trackCount;
    public final String imageUrl;

    public SpotifyPlaylist(String id, String name, String uri, int trackCount, String imageUrl) {
        this.id = id;
        this.name = name;
        this.uri = uri;
        this.trackCount = trackCount;
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return name + " (" + trackCount + ")";
    }
}
