package pub.godweer.config;

import com.google.gson.*;
import lombok.Getter;
import lombok.Setter;
import pub.godweer.Client;
import pub.godweer.hud.HudElement;
import pub.godweer.hud.HudManager;
import pub.godweer.hud.Notifications;
import pub.godweer.module.Module;
import pub.godweer.module.settings.Setting;
import pub.godweer.module.settings.impl.*;
import pub.godweer.util.game.ChatUtil;
import pub.godweer.util.style.Theme;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Create by daun kvass
 */
@Getter
public class ConfigManager {

    private final File configFolder;
    /** ~/femboy/state.json — что за конфиг считать текущим между запусками. */
    private final File stateFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** Схема конфига: 2 — бинды пишутся кодами + режимами (Hold/Toggle). */
    private static final int CONFIG_VERSION = 2;
    private static final String DEFAULT_CONFIG = "default";
    private final ScheduledExecutorService autoSaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Config-AutoSave-Thread");
        thread.setDaemon(true);
        return thread;
    });

    @Setter
    private String currentConfig = "default";

    public ConfigManager() {
        this.configFolder = new File(new File(System.getProperty("user.home"), "femboy"), "configs");
        this.stateFile = new File(configFolder.getParentFile(), "state.json");
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        String last = readStateLastConfig();
        if (!last.isEmpty()) {
            this.currentConfig = last;
        }

        autoSaveExecutor.scheduleAtFixedRate(() -> {
            try {
                // Автосейв пишет только когда содержимое реально изменилось: файл,
                // тронутый каждые две минуты, ломает сравнение бэкапов и лишает
                // смысла «конфиг не менялся».
                saveConfig(this.currentConfig, false);
            } catch (Throwable t) {
                System.err.println("[ConfigManager] Ошибка при фоновом автосохранении:");
                t.printStackTrace();
            }
        }, 2, 2, TimeUnit.MINUTES);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveConfig(this.currentConfig, false);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "Config-Shutdown-Save"));
    }

    /**
     * Имя конфига из ввода GUI: символы пути вырезаны, .ww снят, длина ограничена.
     * Команды раньше принимали только word-аргумент, теперь имя может прийти из поля ввода.
     */
    public String safeName(String raw) {
        if (raw == null) return "";
        String name = raw.trim().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "").replaceAll("\\.+", ".");
        if (name.toLowerCase().endsWith(".ww")) {
            name = name.substring(0, name.length() - 3);
        }
        name = name.replaceAll("\\s+", " ").trim();
        if (name.length() > 48) {
            name = name.substring(0, 48).trim();
        }
        return name;
    }

    /** Файл конфига или null, если имя пустое либо уходит за пределы папки. */
    private File resolve(String raw) {
        String name = safeName(raw);
        if (name.isEmpty()) return null;

        File file = new File(configFolder, name + ".ww");
        try {
            String root = configFolder.getCanonicalPath();
            if (!file.getCanonicalPath().equalsIgnoreCase(root)) {
                if (!file.getCanonicalPath().startsWith(root + File.separator)) return null;
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    public boolean configExists(String name) {
        File file = resolve(name);
        return file != null && file.exists();
    }

    public synchronized boolean saveConfig(String name, boolean notify) {
        String safe = safeName(name);
        if (safe.isEmpty()) {
            safe = "default";
        }

        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        File targetFile = resolve(safe);
        if (targetFile == null) {
            if (notify) {
                ChatUtil.error("Недопустимое имя конфигурации: " + name);
            }
            return false;
        }
        File tempFile = new File(configFolder, safe + ".ww.tmp");

        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", CONFIG_VERSION);
            root.addProperty("savedAt", System.currentTimeMillis() / 1000L);

            JsonObject themeJson = new JsonObject();
            themeJson.addProperty("accentColor", Theme.getAccentColor());
            themeJson.addProperty("backgroundMode", Theme.getBackgroundMode());
            themeJson.addProperty("bgColor1", Theme.getBgColor1());
            themeJson.addProperty("bgColor2", Theme.getBgColor2());
            themeJson.addProperty("panelAlpha", Theme.getPanelAlpha());
            root.add("theme", themeJson);

            JsonObject modulesJson = new JsonObject();
            if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
                for (Module module : Client.INSTANCE.moduleManager.getModules()) {
                    if (module == null) continue;

                    JsonObject moduleData = new JsonObject();
                    moduleData.addProperty("enabled", module.isEnabled());

                    BindSetting moduleBind = module.getBind();
                    if (moduleBind != null && moduleBind.getValue() != null) {
                        moduleData.add("bind", bindToJson(moduleBind));
                    }

                    JsonObject settingsJson = new JsonObject();
                    for (Setting<?> setting : module.getSettings()) {
                        if (setting == null || setting instanceof HeaderSetting || setting.getName() == null) continue;

                        try {
                            if (setting instanceof CheckBox cb && cb.getValue() != null) {
                                settingsJson.addProperty(cb.getName(), cb.getValue());
                            } else if (setting instanceof SliderSetting slider && slider.getValue() != null) {
                                settingsJson.addProperty(slider.getName(), slider.getValue());
                            } else if (setting instanceof ModeSetting mode && mode.getValue() != null) {
                                settingsJson.addProperty(mode.getName(), mode.getValue());
                            } else if (setting instanceof ColorSetting color && color.getValue() != null) {
                                settingsJson.addProperty(color.getName(), color.getValue());
                            } else if (setting instanceof BindSetting bind && bind.getValue() != null) {
                                settingsJson.add(bind.getName(), bindToJson(bind));
                            } else if (setting instanceof MultiModeSetting mm && mm.getValue() != null) {
                                JsonArray array = new JsonArray();
                                for (String m : mm.getValue()) {
                                    if (m != null) array.add(m);
                                }
                                settingsJson.add(mm.getName(), array);
                            }
                        } catch (Exception e) {
                            System.err.println("[ConfigManager] Ошибка сохранения настройки " + setting.getName() + " в модуле " + module.getName());
                        }
                    }

                    moduleData.add("settings", settingsJson);
                    modulesJson.add(module.getName(), moduleData);
                }
            }
            root.add("modules", modulesJson);

            JsonObject hudJson = new JsonObject();
            if (HudManager.getInstance() != null && HudManager.getInstance().getElements() != null) {
                for (HudElement element : HudManager.getInstance().getElements()) {
                    if (element == null) continue;
                    JsonObject elementData = new JsonObject();
                    elementData.addProperty("enabled", element.isEnabled());
                    elementData.addProperty("x", element.getX());
                    elementData.addProperty("y", element.getY());
                    elementData.addProperty("scale", element.getScale());

                    hudJson.add(element.getId(), elementData);
                }
            }
            root.add("hud", hudJson);

            // savedAt меняется каждый раз, поэтому сравниваем только полезную часть:
            // иначе «ничего не изменилось» не наступит никогда.
            root.remove("savedAt");
            String body = gson.toJson(root);
            root.addProperty("savedAt", System.currentTimeMillis() / 1000L);

            if (targetFile.exists() && body.equals(normalizedBody(targetFile))) {
                this.currentConfig = safe;
                writeState();
                if (notify) {
                    ChatUtil.info("Конфигурация '" + safe + "' уже актуальна.");
                }
                return true;
            }

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
                writer.flush();
            }

            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            this.currentConfig = safe;
            writeState();

            if (notify) {
                ChatUtil.success("Конфигурация '" + safe + "' успешно сохранена!");
            }
            return true;
        } catch (Exception e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (notify) {
                ChatUtil.error("Не удалось сохранить конфигурацию: " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized boolean loadConfig(String name, boolean notify) {
        File file = resolve(name);
        if (file == null) {
            if (notify) {
                ChatUtil.error("Недопустимое имя конфигурации: " + name);
            }
            return false;
        }
        if (!file.exists()) {
            if (notify) {
                ChatUtil.error("Конфигурация '" + safeName(name) + "' не найдена!");
            }
            return false;
        }

        Notifications.setMuted(true);
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            // Hold-модули, включённые удержанием клавиши, снимаем до загрузки: иначе
            // состояние клавиши переживет смену конфига и модуль останется висеть.
            if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
                Client.INSTANCE.moduleManager.releaseHeld();
            }

            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("theme")) {
                JsonObject themeJson = root.getAsJsonObject("theme");
                if (themeJson.has("accentColor")) Theme.setAccentColor(themeJson.get("accentColor").getAsInt());
                if (themeJson.has("backgroundMode")) Theme.setBackgroundMode(themeJson.get("backgroundMode").getAsString());
                if (themeJson.has("bgColor1")) Theme.setBgColor1(themeJson.get("bgColor1").getAsInt());
                if (themeJson.has("bgColor2")) Theme.setBgColor2(themeJson.get("bgColor2").getAsInt());
                if (themeJson.has("panelAlpha")) Theme.setPanelAlpha(themeJson.get("panelAlpha").getAsFloat());
            }

            if (root.has("modules") && Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
                JsonObject modulesJson = root.getAsJsonObject("modules");
                for (Module module : Client.INSTANCE.moduleManager.getModules()) {
                    if (module != null && modulesJson.has(module.getName())) {
                        JsonObject moduleData = modulesJson.getAsJsonObject(module.getName());

                        if (moduleData.has("enabled")) {
                            boolean enabled = moduleData.get("enabled").getAsBoolean();
                            if (module.isEnabled() != enabled) {
                                module.setEnabled(enabled);
                            }
                        }

                        if (moduleData.has("bind") && module.getBind() != null) {
                            readBind(module.getBind(), moduleData.get("bind"));
                        }

                        if (moduleData.has("settings")) {
                            JsonObject settingsJson = moduleData.getAsJsonObject("settings");
                            for (Setting<?> setting : module.getSettings()) {
                                if (setting != null && settingsJson.has(setting.getName())) {
                                    JsonElement elem = settingsJson.get(setting.getName());

                                    try {
                                        if (setting instanceof CheckBox cb) {
                                            cb.setValue(elem.getAsBoolean());
                                        } else if (setting instanceof SliderSetting slider) {
                                            slider.setValue(elem.getAsFloat());
                                        } else if (setting instanceof ModeSetting mode) {
                                            // Режимов, которых больше нет в списке (старый конфиг),
                                            // не касаемся: иначе модуль висел бы в пустом значении.
                                            String value = elem.getAsString();
                                            boolean known = mode.getModes().stream()
                                                    .anyMatch(m -> m.equalsIgnoreCase(value));
                                            if (known) mode.setValue(value);
                                        } else if (setting instanceof ColorSetting color) {
                                            color.setValue(elem.getAsInt());
                                        } else if (setting instanceof BindSetting bind) {
                                            readBind(bind, elem);
                                        } else if (setting instanceof MultiModeSetting mm && elem.isJsonArray()) {
                                            List<String> list = new ArrayList<>();
                                            for (JsonElement e : elem.getAsJsonArray()) {
                                                list.add(e.getAsString());
                                            }
                                            mm.setValue(list);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[ConfigManager] Ошибка чтения настройки: " + setting.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (root.has("hud") && HudManager.getInstance() != null) {
                JsonObject hudJson = root.getAsJsonObject("hud");
                for (HudElement element : HudManager.getInstance().getElements()) {
                    if (element == null) continue;
                    String key = element.getId();
                    if (hudJson.has(key)) {
                        JsonObject elementData = hudJson.getAsJsonObject(key);
                        if (elementData.has("enabled")) {
                            element.setEnabled(elementData.get("enabled").getAsBoolean());
                        }
                        if (elementData.has("x")) {
                            element.setX(elementData.get("x").getAsFloat());
                        }
                        if (elementData.has("y")) {
                            element.setY(elementData.get("y").getAsFloat());
                        }
                        if (elementData.has("scale")) {
                            element.setScale(elementData.get("scale").getAsFloat());
                        }
                    }
                }
            }

            this.currentConfig = safeName(file.getName());
            writeState();

            if (notify) {
                ChatUtil.success("Конфигурация '" + safeName(file.getName()) + "' успешно загружена!");
            }
            return true;
        } catch (Exception e) {
            if (notify) {
                ChatUtil.error("Ошибка при чтении конфигурации: " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        } finally {
            Notifications.setMuted(false);
        }
    }

    public synchronized boolean deleteConfig(String name, boolean notify) {
        File file = resolve(name);
        String label = file == null ? safeName(name) : safeName(file.getName());
        if (file == null || !file.exists()) {
            if (notify) {
                ChatUtil.error("Конфигурация '" + label + "' не найдена!");
            }
            return false;
        }

        try {
            Files.delete(file.toPath());
            if (notify) {
                ChatUtil.success("Конфигурация '" + label + "' удалена!");
            }
            return true;
        } catch (IOException e) {
            if (notify) {
                ChatUtil.error("Не удалось удалить конфигурацию: " + e.getMessage());
            }
            return false;
        }
    }

    public synchronized boolean renameConfig(String from, String to, boolean notify) {
        File source = resolve(from);
        File target = resolve(to);
        if (source == null || !source.exists() || target == null) {
            if (notify) {
                ChatUtil.error("Не удалось переименовать: имя пустое или недопустимое.");
            }
            return false;
        }
        if (target.exists()) {
            if (notify) {
                ChatUtil.error("Конфигурация '" + safeName(to) + "' уже существует!");
            }
            return false;
        }

        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            if (safeName(source.getName()).equalsIgnoreCase(this.currentConfig)) {
                this.currentConfig = safeName(target.getName());
                writeState();
            }
            if (notify) {
                ChatUtil.success("Конфигурация переименована в '" + safeName(target.getName()) + "'.");
            }
            return true;
        } catch (IOException e) {
            if (notify) {
                ChatUtil.error("Ошибка при переименовании: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Какой конфиг загружать на старте: последний использованный, если он ещё
     * существует, иначе default. Раньше старт всегда читал «default», и работа в
     * другом конфиге терялась после перезапуска.
     */
    public String startupConfig() {
        String last = readStateLastConfig();
        if (!last.isEmpty() && configExists(last)) return last;
        return configExists(DEFAULT_CONFIG) ? DEFAULT_CONFIG : "";
    }

    private String readStateLastConfig() {
        if (stateFile == null || !stateFile.exists()) return "";
        try (Reader reader = new InputStreamReader(new FileInputStream(stateFile), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("lastConfig") && json.get("lastConfig").isJsonPrimitive()) {
                return safeName(json.get("lastConfig").getAsString());
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void writeState() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("version", CONFIG_VERSION);
            json.addProperty("lastConfig", this.currentConfig);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(stateFile), StandardCharsets.UTF_8)) {
                gson.toJson(json, writer);
            }
        } catch (Exception e) {
            System.err.println("[ConfigManager] Не удалось записать state.json: " + e.getMessage());
        }
    }

    /** Конфиг без метки времени — так сравнение «изменилось или нет» имеет смысл. */
    private String normalizedBody(File file) {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject parsed = JsonParser.parseReader(reader).getAsJsonObject();
            parsed.remove("savedAt");
            return gson.toJson(parsed);
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonObject bindToJson(BindSetting bind) {
        JsonObject json = new JsonObject();
        JsonArray codes = new JsonArray();
        for (int code : bind.getValue()) {
            codes.add(code);
        }
        json.add("codes", codes);
        json.add("modes", bind.writeModes().getAsJsonArray());
        return json;
    }

    /** Принимает все три формата: один код (v0), список кодов (v1), коды + режимы (v2). */
    private static void readBind(BindSetting bind, JsonElement element) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonPrimitive()) {
            bind.setSingle(element.getAsInt());
            return;
        }

        if (element.isJsonArray()) {
            bind.setValue(codesOf(element.getAsJsonArray()));
            return;
        }

        JsonObject json = element.getAsJsonObject();
        bind.setValue(json.has("codes") && json.get("codes").isJsonArray()
                ? codesOf(json.getAsJsonArray("codes"))
                : List.of());
        bind.readModes(json.get("modes"));
    }

    private static List<Integer> codesOf(JsonArray array) {
        List<Integer> codes = new ArrayList<>();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive()) {
                codes.add(item.getAsInt());
            }
        }
        return codes;
    }

    public List<String> getAvailableConfigs() {
        List<String> list = new ArrayList<>();
        File[] files = configFolder.listFiles((dir, name) -> name.endsWith(".ww"));
        if (files != null) {
            for (File file : files) {
                list.add(file.getName().replace(".ww", ""));
            }
        }
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public void openFolder() {
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(configFolder);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", configFolder.getAbsolutePath()});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", configFolder.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", configFolder.getAbsolutePath()});
                }
            }
        } catch (Exception e) {
            ChatUtil.error("Не удалось открыть папку: " + e.getMessage());
        }
    }
}