package pub.godweer.account;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.services.ProfileResult;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import pub.godweer.mixin.accessor.MinecraftAccessor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Create by daun kvass
 */
@Getter
public class AccountManager {
    private static final AccountManager INSTANCE = new AccountManager();
    public static AccountManager getInstance() { return INSTANCE; }

    private final List<String> accounts = new ArrayList<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    @Setter
    private String activeAccount = "";

    private final File accountsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ScheduledExecutorService autoSaveExecutor = Executors.newSingleThreadScheduledExecutor();

    public AccountManager() {
        File dir = new File(new File(System.getProperty("user.home"), "godweer"), "configs");
        if (!dir.exists()) dir.mkdirs();
        this.accountsFile = new File(dir, "accounts.json");

        load();

        autoSaveExecutor.scheduleAtFixedRate(() -> {
            try {
                save();
            } catch (Exception ignored) {}
        }, 2, 2, TimeUnit.MINUTES);

        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
    }

    public void addAccount(String name) {
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        if (!accounts.contains(name)) {
            accounts.add(name);
            if (activeAccount.isEmpty()) {
                activeAccount = name;
            }
            save();
        }
    }

    public void removeAccount(String name) {
        accounts.remove(name);
        favorites.remove(name);
        if (activeAccount.equalsIgnoreCase(name)) {
            activeAccount = accounts.isEmpty() ? "" : accounts.get(0);
        }
        save();
    }

    public boolean isFavorite(String name) {
        return favorites.contains(name);
    }

    public void toggleFavorite(String name) {
        if (favorites.contains(name)) {
            favorites.remove(name);
        } else {
            favorites.add(name);
        }
        save();
    }

    public List<String> getSortedAccounts() {
        List<String> list = new ArrayList<>(accounts);
        list.sort((a, b) -> {
            boolean favA = favorites.contains(a);
            boolean favB = favorites.contains(b);
            if (favA != favB) return favA ? -1 : 1;
            return a.compareToIgnoreCase(b);
        });
        return list;
    }

    public boolean setSession(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            User newUser = new User(username, uuid, "0", Optional.empty(), Optional.empty());

            MinecraftAccessor accessor = (MinecraftAccessor) mc;
            accessor.setUser(newUser);
            accessor.setProfileFuture(CompletableFuture.completedFuture(
                    new ProfileResult(new GameProfile(uuid, username))
            ));

            mc.updateTitle();
            this.activeAccount = username;
            save();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public void relogin(String username) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            ServerData server = mc.getCurrentServer();
            setSession(username);

            if (mc.level == null) return;

            mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);

            if (server != null && !server.isLan() && !server.isRealm()) {
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(server.ip), server, false, null);
            } else {
                mc.gui.setScreen(new TitleScreen());
            }
        });
    }

    public void applyActiveSession() {
        if (activeAccount == null || activeAccount.trim().isEmpty()) {
            if (!accounts.isEmpty()) {
                activeAccount = accounts.get(0);
            }
        }
        if (activeAccount != null && !activeAccount.trim().isEmpty()) {
            setSession(activeAccount);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", this.activeAccount != null ? this.activeAccount : "");

        JsonArray accArray = new JsonArray();
        for (String acc : accounts) {
            accArray.add(acc);
        }
        root.add("accounts", accArray);

        JsonArray favArray = new JsonArray();
        for (String fav : favorites) {
            favArray.add(fav);
        }
        root.add("favorites", favArray);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(accountsFile), StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!accountsFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(accountsFile), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            accounts.clear();
            favorites.clear();

            if (root.has("accounts")) {
                for (JsonElement el : root.getAsJsonArray("accounts")) {
                    accounts.add(el.getAsString());
                }
            }

            if (root.has("favorites")) {
                for (JsonElement el : root.getAsJsonArray("favorites")) {
                    favorites.add(el.getAsString());
                }
            }

            if (root.has("active")) {
                this.activeAccount = root.get("active").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}