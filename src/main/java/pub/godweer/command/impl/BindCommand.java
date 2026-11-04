package pub.godweer.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import pub.godweer.Client;
import pub.godweer.command.Command;
import pub.godweer.module.Module;
import pub.godweer.module.settings.Binds;
import pub.godweer.module.settings.impl.BindSetting;
import pub.godweer.util.game.ChatUtil;
import pub.godweer.util.game.KeyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create by daun kvass
 */
public class BindCommand extends Command {

    public BindCommand() {
        super("bind", "Назначает клавишу модулю");
    }

    private String getPrefix() {
        if (Client.INSTANCE != null && Client.INSTANCE.commandManager != null) {
            return Client.INSTANCE.commandManager.getPrefix();
        }
        return ".";
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        SuggestionProvider<Object> moduleSuggestions = (context, suggestionsBuilder) -> {
            String remaining = normalize(suggestionsBuilder.getRemaining());
            for (Module module : modules()) {
                if (normalize(module.getName()).startsWith(remaining)) {
                    suggestionsBuilder.suggest(module.getName());
                }
            }
            return suggestionsBuilder.buildFuture();
        };

        builder.executes(context -> {
            String prefix = getPrefix();
            ChatUtil.info("Назначение клавиши:");
            ChatUtil.entry(prefix + "bind \"модуль\" \"клавиша\"", "Например: " + prefix + "bind KillAura R");
            ChatUtil.entry(prefix + "unbind \"клавиша\"", "Снять клавишу со всех модулей");
            ChatUtil.entry("Клавиши", "R, F1, SPACE, TAB, M1, NUM 5, NUMPAD0");
            return 1;
        });

        builder.then(RequiredArgumentBuilder.<Object, String>argument("module", StringArgumentType.string())
                .suggests(moduleSuggestions)
                .then(RequiredArgumentBuilder.<Object, String>argument("key", StringArgumentType.string())
                        .executes(context -> {
                            String moduleName = StringArgumentType.getString(context, "module");
                            String keyName = StringArgumentType.getString(context, "key");

                            Module module = findModule(moduleName);
                            if (module == null) {
                                ChatUtil.error("Модуль '" + moduleName + "' не найден.");
                                return 0;
                            }

                            int code = KeyUtil.parseKey(keyName);
                            if (!BindSetting.isValidKey(code)) {
                                ChatUtil.error("Клавиша '" + keyName + "' не распознана. Примеры: R, F1, SPACE, M1.");
                                return 0;
                            }

                            if (!Binds.assign(module.getBind(), code)) {
                                ChatUtil.error("Не удалось назначить клавишу " + KeyUtil.getKeyName(code) + ".");
                                return 0;
                            }

                            ChatUtil.success(module.getName() + " → " + module.getBind().getDisplayValue());
                            return 1;
                        }))
        );
    }

    private Module findModule(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return null;
        }

        List<Module> partial = new ArrayList<>();
        for (Module module : modules()) {
            String moduleName = normalize(module.getName());
            if (moduleName.equals(normalized)) {
                return module;
            }
            if (moduleName.contains(normalized)) {
                partial.add(module);
            }
        }
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private List<Module> modules() {
        if (Client.INSTANCE == null || Client.INSTANCE.moduleManager == null) {
            return List.of();
        }
        return Client.INSTANCE.moduleManager.getModules();
    }

    private String normalize(String name) {
        return name == null ? "" : name.replace("\"", "").trim().replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
