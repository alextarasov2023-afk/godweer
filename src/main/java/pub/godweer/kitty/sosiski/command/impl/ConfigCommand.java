package pub.godweer.kitty.sosiski.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import pub.godweer.Client;
import pub.godweer.kitty.sosiski.command.Command;
import pub.godweer.auxiliary.client.persiki.ChatUtil;

import java.util.List;

public class ConfigCommand extends Command {

    public ConfigCommand() {
        super("cfg", "Управление конфигурациями");
    }

    @Override
    public List<String> aliases() {
        return List.of("config", "configs");
    }

    private String getPrefix() {
        if (Client.INSTANCE != null && Client.INSTANCE.commandManager != null) {
            return Client.INSTANCE.commandManager.getPrefix();
        }
        return ".";
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        SuggestionProvider<Object> configSuggestions = (context, suggestionsBuilder) -> {
            if (Client.INSTANCE != null && Client.INSTANCE.configManager != null) {
                for (String cfg : Client.INSTANCE.configManager.getAvailableConfigs()) {
                    if (cfg.toLowerCase().startsWith(suggestionsBuilder.getRemaining().toLowerCase())) {
                        suggestionsBuilder.suggest(cfg);
                    }
                }
            }
            return suggestionsBuilder.buildFuture();
        };

        builder.executes(context -> {
            String prefix = getPrefix();
            ChatUtil.info("Конфигурации:");
            ChatUtil.entry(prefix + "cfg save <название>", "Сохранить текущую конфигурацию");
            ChatUtil.entry(prefix + "cfg load <название>", "Загрузить конфигурацию");
            ChatUtil.entry(prefix + "cfg delete <название>", "Удалить конфигурацию");
            ChatUtil.entry(prefix + "cfg list", "Список всех сохраненных конфигураций");
            ChatUtil.entry(prefix + "cfg dir", "Открыть папку с конфигурациями");
            return 1;
        });

        builder.then(LiteralArgumentBuilder.literal("save")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .executes(context -> {
                            if (Client.INSTANCE.configManager == null) {
                                ChatUtil.error("ConfigManager не инициализирован!");
                                return 0;
                            }
                            String name = StringArgumentType.getString(context, "name");
                            Client.INSTANCE.configManager.saveConfig(name, true);
                            return 1;
                        }))
        );

        builder.then(LiteralArgumentBuilder.literal("load")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(configSuggestions)
                        .executes(context -> {
                            if (Client.INSTANCE.configManager == null) {
                                ChatUtil.error("ConfigManager не инициализирован!");
                                return 0;
                            }
                            String name = StringArgumentType.getString(context, "name");
                            Client.INSTANCE.configManager.loadConfig(name, true);
                            return 1;
                        }))
        );

        builder.then(LiteralArgumentBuilder.literal("delete")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(configSuggestions)
                        .executes(context -> {
                            if (Client.INSTANCE.configManager == null) {
                                ChatUtil.error("ConfigManager не инициализирован!");
                                return 0;
                            }
                            String name = StringArgumentType.getString(context, "name");
                            Client.INSTANCE.configManager.deleteConfig(name, true);
                            return 1;
                        }))
        );

        builder.then(LiteralArgumentBuilder.literal("list")
                .executes(context -> {
                    if (Client.INSTANCE.configManager == null) {
                        ChatUtil.error("ConfigManager не инициализирован!");
                        return 0;
                    }
                    List<String> configs = Client.INSTANCE.configManager.getAvailableConfigs();
                    if (configs.isEmpty()) {
                        ChatUtil.info("Сохраненных конфигураций не найдено.");
                    } else {
                        ChatUtil.info("Список конфигураций (" + configs.size() + "):");
                        for (String cfg : configs) {
                            ChatUtil.entry(cfg, "");
                        }
                    }
                    return 1;
                })
        );

        builder.then(LiteralArgumentBuilder.literal("dir")
                .executes(context -> {
                    if (Client.INSTANCE.configManager == null) {
                        ChatUtil.error("ConfigManager не инициализирован!");
                        return 0;
                    }
                    Client.INSTANCE.configManager.openFolder();
                    ChatUtil.info("Папка с конфигурациями открыта.");
                    return 1;
                })
        );
    }
}