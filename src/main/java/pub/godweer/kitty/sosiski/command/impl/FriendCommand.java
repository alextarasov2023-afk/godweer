package pub.godweer.kitty.sosiski.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import pub.godweer.Client;
import pub.godweer.kitty.sosiski.command.Command;
import pub.godweer.kitty.sosiski.friend.FriendManager;
import pub.godweer.auxiliary.client.persiki.ChatUtil;

import java.util.List;
import java.util.Set;

public class FriendCommand extends Command {

    public FriendCommand() {
        super("friend", "Управление списком друзей");
    }

    @Override
    public List<String> aliases() {
        return List.of("friends", "f");
    }

    private String getPrefix() {
        if (Client.INSTANCE != null && Client.INSTANCE.commandManager != null) {
            return Client.INSTANCE.commandManager.getPrefix();
        }
        return ".";
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        SuggestionProvider<Object> playerSuggestions = (context, suggestionsBuilder) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    String name = info.getProfile().name();
                    if (!FriendManager.getInstance().isFriend(name) &&
                            name.toLowerCase().startsWith(suggestionsBuilder.getRemaining().toLowerCase())) {
                        suggestionsBuilder.suggest(name);
                    }
                }
            }
            return suggestionsBuilder.buildFuture();
        };

        SuggestionProvider<Object> friendSuggestions = (context, suggestionsBuilder) -> {
            for (String friend : FriendManager.getInstance().getFriends()) {
                if (friend.toLowerCase().startsWith(suggestionsBuilder.getRemaining().toLowerCase())) {
                    suggestionsBuilder.suggest(friend);
                }
            }
            return suggestionsBuilder.buildFuture();
        };

        builder.executes(context -> {
            String prefix = getPrefix();
            ChatUtil.info("Управление списком друзей:");
            ChatUtil.entry(prefix + "friend add <ник>", "Добавить игрока в друзья");
            ChatUtil.entry(prefix + "friend remove <ник>", "Удалить игрока из друзей");
            ChatUtil.entry(prefix + "friend list", "Список всех сохраненных друзей");
            ChatUtil.entry(prefix + "friend clear", "Очистить список друзей");
            return 1;
        });

        builder.then(LiteralArgumentBuilder.literal("add")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(playerSuggestions)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (FriendManager.getInstance().isFriend(name)) {
                                ChatUtil.info("Игрок '§b" + name + "§r' уже находится в списке друзей!");
                                return 0;
                            }
                            FriendManager.getInstance().addFriend(name);
                            ChatUtil.success("Игрок '§a" + name + "§r' успешно добавлен в друзья!");
                            return 1;
                        }))
        );

        LiteralArgumentBuilder<Object> removeCommand = LiteralArgumentBuilder.literal("remove")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(friendSuggestions)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (!FriendManager.getInstance().isFriend(name)) {
                                ChatUtil.error("Игрок '§c" + name + "§r' не найден в списке друзей!");
                                return 0;
                            }
                            FriendManager.getInstance().removeFriend(name);
                            ChatUtil.success("Игрок '§c" + name + "§r' успешно удален из друзей!");
                            return 1;
                        }));
        builder.then(removeCommand);

        builder.then(LiteralArgumentBuilder.literal("del")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(friendSuggestions)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (!FriendManager.getInstance().isFriend(name)) {
                                ChatUtil.error("Игрок '§c" + name + "§r' не найден в списке друзей!");
                                return 0;
                            }
                            FriendManager.getInstance().removeFriend(name);
                            ChatUtil.success("Игрок '§c" + name + "§r' успешно удален из друзей!");
                            return 1;
                        }))
        );

        builder.then(LiteralArgumentBuilder.literal("list")
                .executes(context -> {
                    Set<String> friends = FriendManager.getInstance().getFriends();
                    if (friends.isEmpty()) {
                        ChatUtil.info("Список друзей пуст.");
                    } else {
                        ChatUtil.info("Список друзей (" + friends.size() + "):");
                        for (String friend : friends) {
                            ChatUtil.entry(friend, "");
                        }
                    }
                    return 1;
                })
        );

        builder.then(LiteralArgumentBuilder.literal("clear")
                .executes(context -> {
                    Set<String> friends = FriendManager.getInstance().getFriends();
                    if (friends.isEmpty()) {
                        ChatUtil.info("Список друзей уже пуст.");
                    } else {
                        friends.clear();
                        FriendManager.getInstance().save();
                        ChatUtil.success("Список друзей успешно очищен!");
                    }
                    return 1;
                })
        );
    }
}