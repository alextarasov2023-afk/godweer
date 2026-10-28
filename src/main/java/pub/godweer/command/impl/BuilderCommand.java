package pub.godweer.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import pub.godweer.Client;
import pub.godweer.util.game.ChatUtil;
import pub.godweer.rotation.builder.RotationBuilderManager;
import pub.godweer.command.Command;

import java.util.List;

public class BuilderCommand extends Command {

    public BuilderCommand() {
        super("builder", "Обучение и запись кастомных ротаций");
    }

    @Override
    public List<String> aliases() {
        return List.of("rotbuild", "aimbuild");
    }

    private String getPrefix() {
        if (Client.INSTANCE != null && Client.INSTANCE.commandManager != null) {
            return Client.INSTANCE.commandManager.getPrefix();
        }
        return ".";
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        SuggestionProvider<Object> rawSessionSuggestions = (context, suggestionsBuilder) -> {
            for (String session : RotationBuilderManager.INSTANCE.getAvailableRawSessions()) {
                if (session.toLowerCase().startsWith(suggestionsBuilder.getRemaining().toLowerCase())) {
                    suggestionsBuilder.suggest(session);
                }
            }
            return suggestionsBuilder.buildFuture();
        };

        SuggestionProvider<Object> modelSuggestions = (context, suggestionsBuilder) -> {
            for (String model : RotationBuilderManager.INSTANCE.getAvailableProfiles()) {
                if (model.toLowerCase().startsWith(suggestionsBuilder.getRemaining().toLowerCase())) {
                    suggestionsBuilder.suggest(model);
                }
            }
            return suggestionsBuilder.buildFuture();
        };

        builder.executes(context -> {
            String p = getPrefix();
            ChatUtil.info("Управление нейро-ротацией Builder:");
            ChatUtil.entry(p + "builder start [название]", "Начать/продолжить запись сессии");
            ChatUtil.entry(p + "builder stop", "Остановить запись и сохранить данные");
            ChatUtil.entry(p + "builder train", "Обучить нейросеть по собранным данным");
            ChatUtil.entry(p + "builder load <профиль>", "Загрузить обученный профиль");
            ChatUtil.entry(p + "builder dir", "Открыть папку с файлами");
            return 1;
        });

        builder.then(LiteralArgumentBuilder.literal("start")
                .executes(context -> {
                    RotationBuilderManager.INSTANCE.start("default");
                    return 1;
                })
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(rawSessionSuggestions)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            RotationBuilderManager.INSTANCE.start(name);
                            return 1;
                        }))
        );

        builder.then(LiteralArgumentBuilder.literal("stop")
                .executes(context -> {
                    RotationBuilderManager.INSTANCE.stop();
                    return 1;
                })
        );

        builder.then(LiteralArgumentBuilder.literal("train")
                .executes(context -> {
                    RotationBuilderManager.INSTANCE.train();
                    return 1;
                })
        );

        builder.then(LiteralArgumentBuilder.literal("load")
                .then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                        .suggests(modelSuggestions)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            RotationBuilderManager.INSTANCE.load(name);
                            return 1;
                        }))
        );

        builder.then(LiteralArgumentBuilder.literal("dir")
                .executes(context -> {
                    RotationBuilderManager.INSTANCE.openFolder();
                    ChatUtil.info("Папка с датасетами открыта.");
                    return 1;
                })
        );
    }
}