package pub.godweer.ui.overlay;

import com.mojang.blaze3d.platform.InputConstants;
import hh.ru.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import pub.godweer.Client;
import pub.godweer.ui.modal.ConfigModal;
import pub.godweer.ui.modal.FriendsModal;
import pub.godweer.ui.modal.Modal;
import pub.godweer.ui.modal.ModalShell;
import pub.godweer.ui.modal.SettingsPopup;
import pub.godweer.hud.Notifications;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.BindMode;
import pub.godweer.module.settings.Binds;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.localization.Localization;
import pub.godweer.util.game.KeyUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fresh NightX-style ClickGUI renderer.
 */
public final class NightRenderer {
    private final SettingsPopup settingsPopup = new SettingsPopup();
    private final Map<String, Float> switchAnim = new HashMap<>();

    private static final int CARD_OFF = 0xFF14161D;
    private int cardOn() {
        return ColorUtil.lerp(0xFF171A26, Theme.getAccentColor(), 0.22F);
    }

    private static IconUse catIcon(Category c) {
        return switch (c) {
            case COMBAT -> IconUse.FIGHT;
            case MOVEMENT -> IconUse.MOVEMENT;
            case RENDER -> IconUse.RENDER;
            case PLAYER -> IconUse.PLAYER;
            case MISC -> IconUse.MISC;
        };
    }

    private float sideW(NightState s) {
        return 132.0F;
    }

    public void render(Minecraft mc, GuiGraphicsExtractor extractor, NightState s,
                       int screenW, int screenH, int mouseX, int mouseY) {
        s.update();
        float open = s.getOpenAnim().getValue();
        if (open <= 0.01F) return;

        if (!s.isPlaced()) {
            s.setPanelX((screenW - s.getPanelW()) / 2.0F);
            s.setPanelY((screenH - s.getPanelH()) / 2.0F);
            s.setPlaced(true);
        }
        s.fitToScreen(screenW, screenH);
        if (s.isPanelDragging()) {
            s.setPanelX(s.getPanelX() + (mouseX - s.getDragLastX()));
            s.setPanelY(s.getPanelY() + (mouseY - s.getDragLastY()));
            s.setDragLastX(mouseX);
            s.setDragLastY(mouseY);
            s.fitToScreen(screenW, screenH);
        }

        float x = s.getPanelX();
        float y = s.getPanelY();
        float w = s.getFitW();
        float h = s.getFitH();

        float cx = x + w / 2.0F;
        float cy = y + h / 2.0F;
        float scale = 0.95F + 0.05F * open;

        // Dim behind
        Render2D.drawRect(0, 0, screenW, screenH, ColorUtil.rgba(0, 0, 0, (int) (90 * open)));

        extractor.pose().pushMatrix();
        extractor.pose().translate(cx, cy);
        extractor.pose().scale(scale, scale);
        extractor.pose().translate(-cx, -cy);

        float alpha = open;
        int bg = ColorUtil.rgba(13, 14, 19, (int) (245 * alpha));
        Render2D.drawBlur(x, y, w, h, 10.0F, bg, alpha);
        Render2D.drawRoundedRect(x, y, w, h, 10.0F, ColorUtil.multiplyAlpha(0xE60D0E13, alpha));

        float sw = sideW(s);
        Render2D.drawRect(x + sw, y + 12, 1.0F, h - 24, ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.05F * alpha));

        drawSidebar(s, x, y, h, sw, mouseX, mouseY, alpha);
        drawContent(s, x + sw, y, w - sw, h, mouseX, mouseY, alpha);

        extractor.pose().popMatrix();

        if (s.getModal() != null) {
            s.getModal().render(mouseX, mouseY, screenW, screenH, open);
            if (s.getModal().isFinished()) s.setModal(null);
        }
    }

    private void drawSidebar(NightState s, float x, float y, float h, float sw,
                             int mouseX, int mouseY, float alpha) {
        int accent = Theme.getAccentColor();
        // Logo
        Fonts.drawString(Fonts.SF_MEDIUM, Info.NAME, x + 12, y + 10.0F, 12.0F,
                ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha));
        Fonts.drawString(Fonts.SF_MEDIUM, Info.Version, x + 12, y + 24.0F, 8.0F,
                ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));

        float catY = y + 46.0F;
        for (Category c : Category.values()) {
            boolean active = s.getCategory() == c;
            if (active) {
                Render2D.drawRoundedRect(x + 6, catY, sw - 12, 22, 4.0F,
                        ColorUtil.multiplyAlpha(Theme.getAccentWithAlpha(45), alpha));
            }
            int col = active ? accent : Theme.TEXT_MUTED;
            Fonts.drawIcon(catIcon(c), x + 12, catY + 6.0F, 10.0F,
                    ColorUtil.multiplyAlpha(col, alpha));
            Fonts.drawString(Fonts.SF_MEDIUM, Localization.get(c.getDisplayName()),
                    x + 28, catY + 6.0F, 10.0F,
                    ColorUtil.multiplyAlpha(active ? Theme.TEXT_MAIN : Theme.TEXT_MUTED, alpha));
            // Enabled/total counter at the right edge of the category row
            int total = Client.INSTANCE.moduleManager.getByCategory(c).size();
            int on = 0;
            for (Module m : Client.INSTANCE.moduleManager.getByCategory(c)) {
                if (m.isState()) on++;
            }
            String counter = on + "/" + total;
            Fonts.drawString(Fonts.SF_MEDIUM, counter, x + sw - 14.0F - Fonts.SF_MEDIUM.getWidth(counter, 7.0F),
                    catY + 7.5F, 7.0F,
                    ColorUtil.multiplyAlpha(on > 0 ? accent : Theme.TEXT_MUTED, alpha * 0.85F));
            catY += 26.0F;
        }

        // Gear -> settings popup
        float gearY = y + h - 24;
        Fonts.drawIcon(IconUse.GEAR, x + 12, gearY, 11.0F,
                ColorUtil.multiplyAlpha(s.isSettingsOpen() ? accent : Theme.TEXT_MUTED, alpha));
        float popA = alpha * (s.isSettingsOpen() ? 1.0F : 0.0F);
        if (popA > 0.02F) {
            settingsPopup.render(x + 8, gearY - settingsPopup.getHeight() - 8, mouseX, mouseY, popA);
        }

        // Конфиги и друзья рядом с шестерёнкой: без них доступ остаётся только через чат-команды.
        drawSideIcon(IconUse.SCRIPT, x + 42, gearY, s.getModal() instanceof ConfigModal, mouseX, mouseY, alpha);
        drawSideIcon(IconUse.GROUP, x + 64, gearY, s.getModal() instanceof FriendsModal, mouseX, mouseY, alpha);
    }

    private void drawSideIcon(IconUse icon, float ix, float iy, boolean active,
                              int mouseX, int mouseY, float alpha) {
        boolean hovered = mouseX >= ix - 3 && mouseX <= ix + 17 && mouseY >= iy - 4 && mouseY <= iy + 16;
        if (active || hovered) {
            Render2D.drawRoundedRect(ix - 4, iy - 3, 20.0F, 20.0F, 4.0F,
                    ColorUtil.multiplyAlpha(active ? Theme.getAccentWithAlpha(70) : 0xFF222532, alpha));
        }
        Fonts.drawIcon(icon, ix, iy + 1.0F, 11.0F, ColorUtil.multiplyAlpha(
                active ? Theme.getAccentColor() : (hovered ? Theme.TEXT_MAIN : Theme.TEXT_MUTED), alpha));
    }

    private List<Module> filtered(NightState s) {
        String q = s.getSearch().trim().toLowerCase();
        if (q.isEmpty()) return Client.INSTANCE.moduleManager.getByCategory(s.getCategory());
        return Client.INSTANCE.moduleManager.getModules().stream()
                .filter(m -> m.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    private void drawContent(NightState s, float x, float y, float w, float h,
                             int mouseX, int mouseY, float alpha) {
        float anim = s.getContentAnim().getValue();
        float a = alpha * anim;
        float slide = (1.0F - anim) * (s.getDirection() * 10.0F);

        // Search bar
        float searchW = 150.0F;
        float searchH = 18.0F;
        float sx = x + w - searchW - 12;
        float sy = y + 10.0F;
        Render2D.drawRoundedRect(sx, sy, searchW, searchH, 4.0F,
                ColorUtil.multiplyAlpha(s.isSearchFocused() ? 0xCC12141D : 0x8812141D, a));
        Fonts.drawIcon(IconUse.SEARCH, sx + 6, sy + 4.5F, 9.0F,
                ColorUtil.multiplyAlpha(s.isSearchFocused() ? Theme.getAccentColor() : Theme.TEXT_MUTED, a));
        String q = s.getSearch() + (s.isSearchFocused() ? "|" : "");
        if (q.isEmpty()) q = "Search...";
        Fonts.drawString(Fonts.SF_MEDIUM, q, sx + 20, sy + 4.0F, 8.5F,
                ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a));
        Fonts.drawString(Fonts.SF_MEDIUM, Localization.get(s.getCategory().getDisplayName()),
                x + 12, sy + 3.0F, 11.0F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, a));

        float contentX = x + 10;
        float contentY = y + 38;
        float contentW = w - 20;
        float contentH = h - 48;
        Render2D.pushScissor(contentX, contentY, contentW, contentH);
        drawCards(s, contentX, contentY + slide, contentW, contentH, mouseX, mouseY, a);
        Render2D.popScissor();

        // "Nothing found" placeholder
        if (filtered(s).isEmpty()) {
            Fonts.drawString(Fonts.SF_MEDIUM, "Ничего не найдено",
                    x + 12, contentY + 14.0F, 9.5F,
                    ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a));
        }
    }

    private void drawCards(NightState s, float startX, float startY, float totalW,
                           float totalH, int mouseX, int mouseY, float alpha) {
        List<Module> list = filtered(s);
        float colW = (totalW - 8) / 2.0F;
        float scroll = s.getScroll();
        float y1 = startY - scroll;
        float y2 = startY - scroll;
        int accent = Theme.getAccentColor();
        int onBg = cardOn();

        for (int idx = 0; idx < list.size(); idx++) {
            Module m = list.get(idx);
            boolean left = y1 <= y2;
            float mx = left ? startX : startX + colW + 8;
            float my = left ? y1 : y2;
            m.updatePosition(mx, my);
            float px = m.getPosXAnim().getValue() + m.getShakeOffset();
            float py = m.getPosYAnim().getValue();

            m.getExpandAnim().setTarget(s.isExpanded(m) ? 1.0F : 0.0F);
            m.getExpandAnim().update();

            // Staggered entrance fade
            float enter = Math.max(0.0F, Math.min(1.0F, alpha * 1.6F - idx * 0.09F));

            float setH = 0.0F;
            for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                sr.updateVisibility();
                float sh = sr.getHeight();
                if (sh > 0.1F) setH += sh + 2.0F;
            }
            float expA = m.getExpandAnim().getValue();
            float cardH = 38.0F + expA * (setH + 8.0F);
            float mAlpha = alpha * (0.35F + 0.65F * enter);

            boolean hovered = mouseX >= px && mouseX <= px + colW && mouseY >= py && mouseY <= py + 38.0F;
            float hoverBoost = hovered ? 0.14F : 0.0F;

            Render2D.drawRoundedRect(px, py, colW, cardH, 6.0F,
                    ColorUtil.multiplyAlpha(m.isState() ? onBg : CARD_OFF,
                            mAlpha * (m.isState() ? 0.8F : 0.6F) + hoverBoost * mAlpha));
            if (m.isState()) {
                Render2D.drawRoundedRect(px, py + 7.0F, 2.0F, 24.0F, 1.0F,
                        ColorUtil.multiplyAlpha(accent, mAlpha));
            }

            Fonts.drawString(Fonts.SF_MEDIUM, m.getName(), px + 9, py + 6.0F, 10.5F,
                    ColorUtil.multiplyAlpha(m.isState() ? Theme.TEXT_MAIN : Theme.TEXT_MUTED, mAlpha));
            if (m.hasDescription()) {
                String d = Fonts.SF_MEDIUM.trimToWidth(Localization.get(m.getDescription()), colW - 52.0F, 7.5F);
                Fonts.drawString(Fonts.SF_MEDIUM, d, px + 9, py + 20.0F, 7.5F,
                        ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, mAlpha * 0.9F));
            }

            // Animated toggle switch
            float swTarget = m.isState() ? 1.0F : 0.0F;
            float swA = switchAnim.getOrDefault(m.getName(), swTarget);
            swA += (swTarget - swA) * 0.35F;
            if (Math.abs(swTarget - swA) < 0.003F) swA = swTarget;
            switchAnim.put(m.getName(), swA);
            float swW = 22.0F, swH = 12.0F;
            float swX = px + colW - swW - 8.0F;
            float swY = py + 8.0F;
            if (swA > 0.02F) {
                Render2D.drawRoundedRect(swX - 2.0F, swY - 2.0F, swW + 4.0F, swH + 4.0F, swH / 2.0F + 2.0F,
                        ColorUtil.multiplyAlpha(accent, mAlpha * swA * 0.28F));
            }
            int track = ColorUtil.lerp(ColorUtil.rgba(48, 50, 62, 255), accent, swA);
            Render2D.drawRoundedRect(swX, swY, swW, swH, swH / 2.0F,
                    ColorUtil.multiplyAlpha(track, mAlpha));
            float thumb = 9.0F;
            float thumbX = swX + 1.5F + (swW - thumb - 3.0F) * swA;
            Render2D.drawRoundedRect(thumbX, swY + 1.5F, thumb, thumb, thumb / 2.0F,
                    ColorUtil.multiplyAlpha(ColorUtil.WHITE, mAlpha));

            // Bind hint
            if (m.isBinding()) {
                Fonts.drawString(Fonts.SF_MEDIUM, "...", swX - 22, swY + 1.0F, 8.0F,
                        ColorUtil.multiplyAlpha(accent, mAlpha));
            } else if (m.getBind().isBound()) {
                String b = m.getBind().getDisplayValue();
                if (m.getBind().size() > 1 && Fonts.SF_MEDIUM.getWidth(b, 7.5F) > 46.0F) {
                    b = m.getBind().getDisplayValue(0) + "+" + (m.getBind().size() - 1);
                }
                float bw = Fonts.SF_MEDIUM.getWidth(b, 7.5F);
                Fonts.drawString(Fonts.SF_MEDIUM, b, swX - bw - 6, swY + 1.5F, 7.5F,
                        ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, mAlpha));
            }

            if (expA > 0.02F) {
                float panelH = expA * (setH + 4.0F);
                Render2D.drawRoundedRect(px + 4, py + 38.0F, colW - 8, panelH, 4.0F,
                        ColorUtil.multiplyAlpha(0xFF0D0F15, mAlpha * 0.75F));
                Render2D.drawRoundedRect(px + 10, py + 38.5F, colW - 20, 0.5F, 0.25F,
                        ColorUtil.multiplyAlpha(0xFFFFFFFF, mAlpha * 0.07F));
                float setY = py + 41.0F;
                for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                    float sh = sr.getHeight();
                    if (sh > 0.5F) {
                        sr.render(px + 7, setY, colW - 14, sh, mouseX, mouseY,
                                mAlpha * expA);
                        setY += sh + 2.0F;
                    }
                }
            }

            if (left) y1 += cardH + 6.0F;
            else y2 += cardH + 6.0F;
        }

        float total = Math.max(y1, y2) + scroll - startY;
        s.setMaxScroll(Math.max(0.0F, total - totalH + 8.0F));

        if (s.getMaxScroll() > 1.0F) {
            float barH = Math.max(22.0F, totalH * (totalH / (totalH + s.getMaxScroll())));
            float barY = startY + (totalH - barH) * (scroll / s.getMaxScroll());
            Render2D.drawRoundedRect(startX + totalW - 3.0F, barY, 2.0F, barH, 1.0F,
                    ColorUtil.multiplyAlpha(0xFFFFFFFF, alpha * 0.16F));
        }
    }

    public boolean handleMouseButton(NightState s, int mouseX, int mouseY, int button, int action) {
        if (s.getModal() != null) {
            if (action == InputConstants.PRESS) s.getModal().mouseClicked(mouseX, mouseY, button);
            else if (action == InputConstants.RELEASE) s.getModal().mouseReleased(mouseX, mouseY, button);
            return true;
        }

        float x = s.getPanelX();
        float y = s.getPanelY();
        float w = s.getFitW();
        float h = s.getFitH();
        float sw = sideW(s);

        if (action == InputConstants.PRESS) {
            for (Module m : Client.INSTANCE.moduleManager.getModules()) {
                if (m.isBinding()) {
                    Binds.assign(m.getBind(), KeyUtil.fromMouseButton(button));
                    m.setBinding(false);
                    return true;
                }
            }
        }

        float gearY = y + h - 24;
        if (s.isSettingsOpen()) {
            float popY = gearY - settingsPopup.getHeight() - 8;
            if (action == InputConstants.PRESS) {
                if (settingsPopup.mouseClicked(x + 8, popY, mouseX, mouseY, button)) return true;
                s.setSettingsOpen(false);
            } else if (action == InputConstants.RELEASE) {
                settingsPopup.mouseReleased(mouseX, mouseY, button);
            }
        }

        if (action == InputConstants.RELEASE) {
            s.setPanelDragging(false);
            for (Module m : Client.INSTANCE.moduleManager.getModules()) {
                if (s.isExpanded(m)) {
                    for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                        sr.mouseReleased(mouseX, mouseY, button);
                    }
                }
            }
            return true;
        }
        if (action != InputConstants.PRESS) return true;

        // Drag by top strip
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 14) {
            s.setPanelDragging(true);
            s.setDragLastX(mouseX);
            s.setDragLastY(mouseY);
            return true;
        }

        // Gear
        if (mouseX >= x + 6 && mouseX <= x + 28 && mouseY >= gearY - 4 && mouseY <= gearY + 16) {
            s.setSettingsOpen(!s.isSettingsOpen());
            return true;
        }

        // Конфиги / друзья (зоны совпадают с подсветкой в drawSideIcon)
        if (mouseX >= x + 39 && mouseX <= x + 59 && mouseY >= gearY - 4 && mouseY <= gearY + 16) {
            s.setSettingsOpen(false);
            s.setModal(s.getModal() instanceof ConfigModal ? null : new ConfigModal());
            return true;
        }
        if (mouseX >= x + 61 && mouseX <= x + 81 && mouseY >= gearY - 4 && mouseY <= gearY + 16) {
            s.setSettingsOpen(false);
            s.setModal(s.getModal() instanceof FriendsModal ? null : new FriendsModal());
            return true;
        }

        // Categories
        float catY = y + 46.0F;
        for (Category c : Category.values()) {
            if (mouseX >= x + 6 && mouseX <= x + sw - 6 && mouseY >= catY && mouseY <= catY + 22) {
                s.switchCategory(c);
                return true;
            }
            catY += 26.0F;
        }

        // Search focus
        float searchW = 150.0F;
        float sx = x + sw + (w - sw) - searchW - 12;
        if (mouseX >= sx && mouseX <= sx + searchW && mouseY >= y + 10 && mouseY <= y + 28) {
            s.setSearchFocused(true);
            return true;
        }
        s.setSearchFocused(false);
        // Module cards
        float contentX = x + sw + 10;
        float contentY = y + 38;
        float contentW = w - sw - 20;
        float contentH = h - 48;
        if (mouseY < contentY || mouseY > contentY + contentH || mouseX < contentX) {
            return true;
        }
        float colW = (contentW - 8) / 2.0F;

        for (Module m : filtered(s)) {
            float px = m.getPosXAnim().getValue() + m.getShakeOffset();
            float py = m.getPosYAnim().getValue();
            float setH = 0.0F;
            for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                sr.updateVisibility();
                float sh = sr.getHeight();
                if (sh > 0.1F) setH += sh + 2.0F;
            }
            float cardH = 38.0F + m.getExpandAnim().getValue() * (setH + 2.0F);

            if (mouseX >= px && mouseX <= px + colW && mouseY >= py && mouseY <= py + 38) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT) m.toggle();
                else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    if (m.getSettingRenderers().isEmpty()) m.triggerShake();
                    else s.toggleExpanded(m);
                } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                    // Shift + средняя — режим последней навешенной клавиши:
                    // переключение или «пока клавиша зажата».
                    boolean shift = KeyUtil.isKeyDown(InputConstants.KEY_LSHIFT)
                            || KeyUtil.isKeyDown(InputConstants.KEY_RSHIFT);
                    if (shift && m.getBind().isBound()) {
                        BindMode mode = m.getBind().toggleLastMode();
                        Notifications.push(m.getName() + " / "
                                + m.getBind().getDisplayValue(m.getBind().size() - 1) + ": "
                                + (mode == BindMode.HOLD ? "пока клавиша зажата" : "переключение"));
                    } else {
                        m.setBinding(true);
                    }
                }
                return true;
            }
            if (s.isExpanded(m) && mouseX >= px && mouseX <= px + colW) {
                float setY = py + 41.0F;
                for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                    float sh = sr.getHeight();
                    if (sh > 0.5F) {
                        if (mouseY >= setY && mouseY <= setY + sh) {
                            sr.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        setY += sh + 2.0F;
                    }
                }
            }
        }
        return true;
    }

    public boolean handleKey(NightState s, int key, int scanCode, int modifiers) {
        if (s.getModal() != null) {
            Modal modal = s.getModal();
            boolean consumed = modal.keyPressed(key, scanCode, modifiers);
            if (modal.isTextFocused()) {
                int synthesized = ModalShell.synthChar(key);
                if (synthesized != 0) modal.charTyped(synthesized);
            }
            return consumed;
        }

        for (Module m : Client.INSTANCE.moduleManager.getModules()) {
            if (m.isBinding()) {
                if (key == InputConstants.KEY_ESCAPE) m.setBinding(false);
                else if (key == InputConstants.KEY_DELETE) {
                    m.getBind().clear();
                    m.setBinding(false);
                } else {
                    Binds.assign(m.getBind(), key);
                    m.setBinding(false);
                }
                return true;
            }
        }

        // Ctrl+F — поиск. Флаги модификаторов из события в 26.3 надёжнее не читать,
        // чем состояние клавиатуры: на части раскладок бит приходит другим.
        if (key == InputConstants.KEY_F && (KeyUtil.isKeyDown(InputConstants.KEY_LCONTROL)
                || KeyUtil.isKeyDown(InputConstants.KEY_RCONTROL))) {
            s.setSearchFocused(true);
            return true;
        }
        if (s.isSearchFocused()) {
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) {
                s.setSearchFocused(false);
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE && !s.getSearch().isEmpty()) {
                s.setSearch(s.getSearch().substring(0, s.getSearch().length() - 1));
                s.resetScroll();
                return true;
            }
            int synthesized = ModalShell.synthChar(key);
            if (synthesized != 0) {
                s.setSearch(s.getSearch() + (char) synthesized);
                s.resetScroll();
            }
            return true;
        }
        for (Module m : Client.INSTANCE.moduleManager.getModules()) {
            if (s.isExpanded(m)) {
                for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                    sr.keyPressed(key, scanCode, modifiers);
                }
            }
        }
        return false;
    }

    public void handleChar(NightState s, int codePoint) {
        // Отклик на уже синтезированную клавишу: иначе первая буква удвоилась бы там,
        // где символьные события всё-таки приходят.
        if (!ModalShell.acceptSystemChar(codePoint)) return;

        if (s.getModal() != null) {
            s.getModal().charTyped(codePoint);
            return;
        }
        if (s.isSearchFocused()) {
            s.setSearch(s.getSearch() + (char) codePoint);
            s.resetScroll();
            return;
        }
        for (Module m : Client.INSTANCE.moduleManager.getModules()) {
            if (s.isExpanded(m)) {
                for (SettingRenderer<?> sr : m.getSettingRenderers()) {
                    sr.charTyped(codePoint);
                }
            }
        }
    }

    public void handleScroll(NightState s, double delta, int mouseX, int mouseY) {
        if (s.getModal() != null) {
            s.getModal().mouseScroll(delta);
            return;
        }

        float sw = sideW(s);
        float x = s.getPanelX() + sw;
        float y = s.getPanelY();
        float w = s.getFitW() - sw;
        float h = s.getFitH();
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            s.scrollBy((float) delta * 26.0F);
        }
    }
}
