package pub.godweer.friend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.entity.player.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Create by daun kvass
 */
public class FriendManager {
    private static final FriendManager INSTANCE = new FriendManager();
    private final Set<String> friends = new HashSet<>();
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FriendManager() {
        File dir = new File(System.getProperty("user.home"), "femboy");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "friends.json");
    }

    public static FriendManager getInstance() {
        return INSTANCE;
    }

    public void addFriend(String name) {
        friends.add(name.toLowerCase());
        save();
    }

    public void removeFriend(String name) {
        friends.remove(name.toLowerCase());
        save();
    }

    public boolean isFriend(String name) {
        if (name == null) return false;
        return friends.contains(name.toLowerCase());
    }

    public boolean isFriend(Player player) {
        if (player == null || player.getGameProfile() == null) return false;
        return isFriend(player.getGameProfile().name());
    }

    public boolean toggle(String name) {
        if (isFriend(name)) {
            removeFriend(name);
            return false;
        } else {
            addFriend(name);
            return true;
        }
    }

    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(friends, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!file.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Set<String> loaded = gson.fromJson(reader, new TypeToken<Set<String>>(){}.getType());
            if (loaded != null) {
                friends.clear();
                for (String s : loaded) {
                    friends.add(s.toLowerCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Set<String> getFriends() {
        return friends;
    }
}