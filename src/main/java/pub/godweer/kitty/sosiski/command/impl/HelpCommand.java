package pub.godweer.kitty.sosiski.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import pub.godweer.Client;
import pub.godweer.kitty.sosiski.command.Command;
import pub.godweer.auxiliary.client.persiki.ChatUtil;

import java.util.List;

/**
 * Create by daun kvass
 */
public class HelpCommand extends Command {

    public HelpCommand() {
        super("help", "Показывает список всех доступных команд");
    }

    @Override
    public List<String> aliases() {
        return List.of("h", "commands");
    }

    private String getPrefix() {
        if (Client.INSTANCE != null && Client.INSTANCE.commandManager != null) {
            return Client.INSTANCE.commandManager.getPrefix();
        }
        return ".";
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            ChatUtil.info("Список доступных команд:");

            String prefix = getPrefix();
            for (Command cmd : Client.INSTANCE.commandManager.getCommands()) {
                String aliasStr = cmd.aliases().isEmpty()
                        ? ""
                        : " (" + String.join(", ", cmd.aliases()) + ")";

                ChatUtil.entry(prefix + cmd.name() + aliasStr, cmd.description());
            }
            return 1;
        });

        builder.then(RequiredArgumentBuilder.<Object, String>argument("command", StringArgumentType.word())
                .executes(context -> {
                    String targetName = StringArgumentType.getString(context, "command");
                    Command cmd = Client.INSTANCE.commandManager.find(targetName);

                    if (cmd == null) {
                        ChatUtil.error("Команда '" + targetName + "' не найдена.");
                        return 0;
                    }

                    String prefix = getPrefix();
                    ChatUtil.info("Команда " + prefix + cmd.name() + ":");
                    ChatUtil.entry(prefix + cmd.name(), cmd.description());
                    if (!cmd.aliases().isEmpty()) {
                        ChatUtil.info("Алиасы: " + String.join(", ", cmd.aliases()));
                    }
                    return 1;
                })
        );
    }
}