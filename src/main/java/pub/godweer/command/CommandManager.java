package pub.godweer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import pub.godweer.command.impl.*;
import pub.godweer.util.game.ChatUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Create by daun kvass
 */
public class CommandManager {
    private final String prefix = ".";
    private final List<Command> commands = new ArrayList<>();
    private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
    private final Object dummySource = new Object();

    public CommandManager() {
        register(new HelpCommand());
        register(new ConfigCommand());
        register(new GpsCommand());
        register(new BuilderCommand());
        register(new FriendCommand());
        register(new MusicCommand());
    }

    public void register(Command command) {
        commands.add(command);

        LiteralArgumentBuilder<Object> builder = LiteralArgumentBuilder.literal(command.name());
        command.build(builder);
        LiteralCommandNode<Object> node = dispatcher.register(builder);

        for (String alias : command.aliases()) {
            dispatcher.register(LiteralArgumentBuilder.<Object>literal(alias).redirect(node));
        }
    }

    public boolean execute(String message) {
        if (!message.startsWith(prefix)) {
            return false;
        }

        String commandLine = message.substring(prefix.length()).trim();
        if (commandLine.isEmpty()) {
            ChatUtil.error("Вы не ввели команду! Используйте " + prefix + "help");
            return true;
        }

        try {
            dispatcher.execute(commandLine, dummySource);
        } catch (CommandSyntaxException e) {
            ChatUtil.error("Ошибка синтаксиса: " + e.getMessage());
        } catch (Exception e) {
            ChatUtil.error("Неизвестная ошибка при выполнении: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    public Command find(String name) {
        for (Command cmd : commands) {
            if (cmd.name().equalsIgnoreCase(name) ||
                    cmd.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(name))) {
                return cmd;
            }
        }
        return null;
    }

    public List<Command> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public String getPrefix() {
        return prefix;
    }
}