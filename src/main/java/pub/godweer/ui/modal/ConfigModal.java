package pub.godweer.ui.modal;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.Client;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.hud.Notifications;
import pub.godweer.hud.ServerGui;
import pub.godweer.ui.clickgui.PanelRefractions;
import pub.godweer.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

import static pub.godweer.IMinecraft.mc;

/**
 * Окно конфигураций: список, загрузка, сохранение под новым именем, переименование и удаление.
 * Удаление требует второго клика по той же строке — один случайный клик не должен
 * стирать профиль, который пользователь настраивал недели.
 */
public class ConfigModal implements Modal {

    private static final float W = 400.0F;
    private static final float H = 250.0F;
    private static final float PAD = 14.0F;
    private static final float ROW = 24.0F;
    private static final float GAP = 4.0F;
    private static final float BTN = 18.0F;
    private static final float FIELD_H = 20.0F;

    private final List<String> configs = new ArrayList<>();
    private final ModalShell.Field nameField = new ModalShell.Field();

    private String selected = null;
    private String armedDelete = null;
    private int scroll = 0;
    private int visibleRows = 1;
    private long lastScan = 0L;
    private boolean finished = false;

    public ConfigModal() {
        refresh();
    }

    private ConfigManager manager() {
        return Client.INSTANCE == null ? null : Client.INSTANCE.configManager;
    }

    /** Список перечитываем не каждый кадр: папка маленькая, но сканирование всё же файловое. */
    private void rescan() {
        long now = System.currentTimeMillis();
        if (now - lastScan > 500L) {
            lastScan = now;
            refresh();
        }
    }

    private void refresh() {
        lastScan = System.currentTimeMillis();
        configs.clear();
        ConfigManager manager = manager();
        if (manager != null) {
            configs.addAll(manager.getAvailableConfigs());
        }
        if (selected != null && !configs.contains(selected)) {
            selected = null;
        }
        if (scroll > 0 && visibleRows > 0) {
            scroll = Math.min(scroll, Math.max(0, configs.size() - visibleRows));
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float screenWidth, float screenHeight, float alpha) {
        if (alpha <= 0.01F) return;

        ModalShell.Window win = ModalShell.centered(screenWidth, screenHeight, W, H);
        ConfigManager manager = manager();
        String current = manager == null ? "" : manager.getCurrentConfig();
        rescan();

        ModalShell.drawWindow(win, "Конфигурации", "· " + current, alpha, mouseX, mouseY);

        float innerX = win.x() + PAD;
        float innerW = win.w() - PAD * 2.0F;

        // ----- служебная строка: количество и постраничная навигация -----
        float headerY = win.y() + 30.0F;
        String counter = configs.size() + " шт.";
        Fonts.drawString(Fonts.SF_MEDIUM, counter, innerX, headerY + 4.0F, ModalShell.SMALL,
                ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));

        float headerBtnY = headerY - 1.0F;
        float downX = win.x() + win.w() - PAD - BTN;
        float upX = downX - BTN - 4.0F;
        boolean canUp = scroll > 0;
        boolean canDown = scroll + visibleRows < configs.size();
        ModalShell.iconButton(upX, headerBtnY, BTN, ModalShell.Icon.UP,
                canUp && ModalShell.over(upX, headerBtnY, BTN, BTN, mouseX, mouseY), false, alpha);
        ModalShell.iconButton(downX, headerBtnY, BTN, ModalShell.Icon.DOWN,
                canDown && ModalShell.over(downX, headerBtnY, BTN, BTN, mouseX, mouseY), false, alpha);

        // ----- список -----
        float listTop = win.y() + 52.0F;
        float listBottom = win.y() + win.h() - 46.0F;
        visibleRows = Math.max(1, (int) ((listBottom - listTop + GAP) / (ROW + GAP)));
        int maxScroll = Math.max(0, configs.size() - visibleRows);
        scroll = Math.min(scroll, maxScroll);

        Render2D.pushScissor(innerX, listTop, innerW, listBottom - listTop);
        for (int i = scroll; i < Math.min(configs.size(), scroll + visibleRows + 1); i++) {
            String cfg = configs.get(i);
            float rowY = listTop + (i - scroll) * (ROW + GAP);
            boolean isCurrent = cfg.equalsIgnoreCase(current);
            boolean isSelected = cfg.equals(selected);
            boolean hovered = ModalShell.over(innerX, rowY, innerW, ROW, mouseX, mouseY);

            if (isCurrent) {
                Render2D.drawRoundedRect(innerX, rowY, innerW, ROW, 4.0F,
                        ColorUtil.multiplyAlpha(ColorUtil.lerp(0xFF171A26, Theme.getAccentColor(), 0.22F), alpha));
                Render2D.drawRoundedRect(innerX + 1.5F, rowY + 5.0F, 2.0F, ROW - 10.0F, 1.0F,
                        ColorUtil.withAlpha(Theme.getAccentColor(), Math.round(255 * alpha)));
            } else {
                Render2D.drawRoundedRect(innerX, rowY, innerW, ROW, 4.0F,
                        ColorUtil.multiplyAlpha(hovered ? 0xFF1D2030 : (isSelected ? 0xFF191C28 : 0xFF15171F), alpha));
            }
            if (isSelected && !isCurrent) {
                Render2D.drawRoundedOutline(innerX, rowY, innerW, ROW, 4.0F, 0.7F,
                        ColorUtil.withAlpha(Theme.getAccentColor(), Math.round(120 * alpha)));
            }

            float rowBtnDeleteX = innerX + innerW - BTN - 2.0F;
            float rowBtnLoadX = rowBtnDeleteX - BTN - 4.0F;
            String shown = ServerGui.ellipsize(cfg, rowBtnLoadX - innerX - 14.0F, ModalShell.LABEL);
            Fonts.drawString(Fonts.SF_MEDIUM, shown, innerX + 10.0F,
                    rowY + (ROW - Fonts.SF_MEDIUM.textHeight(ModalShell.LABEL)) / 2.0F + 0.35F,
                    ModalShell.LABEL,
                    ColorUtil.multiplyAlpha(isCurrent ? Theme.TEXT_MAIN : Theme.TEXT_MUTED, alpha));

            ModalShell.iconButton(rowBtnLoadX, rowY + (ROW - BTN) / 2.0F, BTN, ModalShell.Icon.LOAD,
                    ModalShell.over(rowBtnLoadX, rowY, BTN, ROW, mouseX, mouseY), false, alpha);
            ModalShell.iconButton(rowBtnDeleteX, rowY + (ROW - BTN) / 2.0F, BTN, ModalShell.Icon.DELETE,
                    ModalShell.over(rowBtnDeleteX, rowY, BTN, ROW, mouseX, mouseY),
                    cfg.equals(armedDelete), alpha);
        }
        Render2D.popScissor();

        if (configs.isEmpty()) {
            Fonts.drawString(Fonts.SF_MEDIUM, "Нет сохранённых конфигураций", innerX, listTop + 6.0F,
                    ModalShell.LABEL, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));
        }

        // ----- нижняя строка: имя + действия -----
        float rowY = win.y() + win.h() - 34.0F;
        float saveW = 68.0F;
        float renameW = 92.0F;
        float folderW = 20.0F;
        float fieldW = innerW - (saveW + renameW + folderW + GAP * 3.0F);

        nameField.render(innerX, rowY, fieldW, FIELD_H, "Имя конфигурации", alpha);

        float cursor = innerX + fieldW + GAP;
        boolean saveHover = ModalShell.over(cursor, rowY, saveW, FIELD_H, mouseX, mouseY);
        ModalShell.button(cursor, rowY, saveW, FIELD_H, "Сохранить", true, saveHover, alpha);
        cursor += saveW + GAP;

        boolean renameHover = ModalShell.over(cursor, rowY, renameW, FIELD_H, mouseX, mouseY);
        ModalShell.button(cursor, rowY, renameW, FIELD_H, "Переименовать", false, renameHover, alpha);
        cursor += renameW + GAP;

        ModalShell.iconButton(cursor, rowY + (FIELD_H - folderW) / 2.0F, folderW, ModalShell.Icon.FOLDER,
                ModalShell.over(cursor, rowY, folderW, FIELD_H, mouseX, mouseY), false, alpha);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        ModalShell.Window win = ModalShell.centered(screenW, screenH, W, H);
        float innerX = win.x() + PAD;
        float innerW = win.w() - PAD * 2.0F;

        if (ModalShell.overClose(win, mouseX, mouseY)) {
            close();
            return true;
        }

        ConfigManager manager = manager();
        if (manager == null) {
            close();
            return true;
        }

        float headerBtnY = win.y() + 29.0F;
        float downX = win.x() + win.w() - PAD - BTN;
        float upX = downX - BTN - 4.0F;
        if (ModalShell.over(upX, headerBtnY, BTN, BTN, mouseX, mouseY)) {
            scroll = Math.max(0, scroll - 1);
            armedDelete = null;
            return true;
        }
        if (ModalShell.over(downX, headerBtnY, BTN, BTN, mouseX, mouseY)) {
            scroll = Math.min(Math.max(0, configs.size() - visibleRows), scroll + 1);
            armedDelete = null;
            return true;
        }

        float listTop = win.y() + 52.0F;
        float listBottom = win.y() + win.h() - 46.0F;
        for (int i = scroll; i < Math.min(configs.size(), scroll + visibleRows); i++) {
            float rowY = listTop + (i - scroll) * (ROW + GAP);
            if (!ModalShell.over(innerX, rowY, innerW, ROW, mouseX, mouseY)) continue;

            String cfg = configs.get(i);
            float deleteX = innerX + innerW - BTN - 2.0F;
            float loadX = deleteX - BTN - 4.0F;

            if (ModalShell.over(deleteX, rowY, BTN, ROW, mouseX, mouseY)) {
                if (cfg.equals(armedDelete)) {
                    if (manager.deleteConfig(cfg, false)) {
                        refresh();
                        Notifications.push("Конфиг '" + cfg + "' удалён");
                        if (cfg.equalsIgnoreCase(manager.getCurrentConfig())) {
                            Notifications.push("Активный конфиг больше не на диске");
                        }
                    }
                    armedDelete = null;
                    if (cfg.equals(selected)) selected = null;
                } else {
                    armedDelete = cfg;
                    selected = cfg;
                    nameField.set(cfg);
                }
                return true;
            }
            if (ModalShell.over(loadX, rowY, BTN, ROW, mouseX, mouseY)) {
                if (manager.loadConfig(cfg, false)) {
                    Notifications.push("Конфиг '" + cfg + "' загружен");
                }
                armedDelete = null;
                return true;
            }

            selected = cfg;
            armedDelete = null;
            nameField.set(cfg);
            nameField.blur();
            return true;
        }

        float rowY = win.y() + win.h() - 34.0F;
        float saveW = 68.0F;
        float renameW = 92.0F;
        float folderW = 20.0F;
        float fieldW = innerW - (saveW + renameW + folderW + GAP * 3.0F);

        if (nameField.mouseClicked(mouseX, mouseY, innerX, rowY, fieldW, FIELD_H)) return true;

        float cursor = innerX + fieldW + GAP;
        if (ModalShell.over(cursor, rowY, saveW, FIELD_H, mouseX, mouseY)) {
            save(manager);
            return true;
        }
        cursor += saveW + GAP;
        if (ModalShell.over(cursor, rowY, renameW, FIELD_H, mouseX, mouseY)) {
            rename(manager);
            return true;
        }
        cursor += renameW + GAP;
        if (ModalShell.over(cursor, rowY, folderW, FIELD_H, mouseX, mouseY)) {
            manager.openFolder();
            return true;
        }

        armedDelete = null;
        nameField.blur();
        return true;
    }

    private void save(ConfigManager manager) {
        String name = manager.safeName(nameField.value());
        if (name.isEmpty()) {
            Notifications.push("Введите имя конфигурации");
            return;
        }
        if (manager.saveConfig(name, false)) {
            nameField.set(name);
            selected = name;
            refresh();
            Notifications.push("Конфиг '" + name + "' сохранён");
        }
    }

    private void rename(ConfigManager manager) {
        String target = manager.safeName(nameField.value());
        if (selected == null || selected.isEmpty()) {
            Notifications.push("Выберите конфиг из списка");
            return;
        }
        if (target.isEmpty()) {
            Notifications.push("Введите новое имя");
            return;
        }
        if (manager.renameConfig(selected, target, false)) {
            selected = target;
            nameField.set(target);
            refresh();
            Notifications.push("Конфиг '" + target + "' готов");
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (armedDelete != null) {
                armedDelete = null;
                return true;
            }
            if (nameField.isFocused()) {
                nameField.blur();
                return true;
            }
            close();
            return true;
        }
        if (nameField.keyPressed(keyCode)) return true;
        if (keyCode == InputConstants.KEY_RETURN) {
            ConfigManager manager = manager();
            if (manager != null) {
                save(manager);
            }
            return true;
        }
        return true;
    }

    @Override
    public void charTyped(int codePoint) {
        nameField.charTyped(codePoint);
    }

    @Override
    public boolean isTextFocused() {
        return nameField.isFocused();
    }

    @Override
    public void mouseScroll(double delta) {
        int max = Math.max(0, configs.size() - visibleRows);
        scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? -1 : 1)));
        armedDelete = null;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    private void close() {
        this.finished = true;
        PanelRefractions.openModal(null);
    }
}
