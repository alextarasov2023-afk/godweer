package pub.godweer.module.settings.render;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.hud.Notifications;
import pub.godweer.module.settings.BindMode;
import pub.godweer.module.settings.Binds;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.impl.BindSetting;
import pub.godweer.util.game.KeyUtil;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.localization.Localization;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

/**
 * Create by daun kvass
 */
public class BindRenderer extends SettingRenderer<BindSetting> {
    private boolean listening;
    private float lastX, lastY, lastW, lastH;

    public BindRenderer(BindSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return 16.0F * visAnim.getValue();
    }

    @Override
    public void render(float x, float y, float width, float height, int mouseX, int mouseY, float alpha) {
        if (visAnim.getValue() <= 0.01F) return;

        this.lastX = x; this.lastY = y; this.lastW = width; this.lastH = height;
        float effectiveAlpha = alpha * visAnim.getValue();

        String displayName = Localization.get(setting.getName());
        Fonts.drawString(Fonts.SF_MEDIUM, displayName, x + 6, y + 2.5F, 9.5F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, effectiveAlpha));

        String keyName = listening ? "..." : shorten();
        float maxTagW = Math.max(28.0F, width - Fonts.SF_MEDIUM.getWidth(displayName, 9.5F) - 20.0F);
        float tagW = Math.min(Fonts.SF_MEDIUM.getWidth(keyName, 8.5F) + 8.0F, maxTagW);
        float tagH = 11.0F;
        float tagX = x + width - tagW - 6.0F;
        float tagY = y + (height - tagH) / 2.0F;

        int bg = listening ? Theme.getAccentColor() : 0xFF1C1E26;
        Render2D.drawRoundedRect(tagX, tagY, tagW, tagH, 2.5F, ColorUtil.multiplyAlpha(bg, effectiveAlpha));

        int col = listening ? 0xFFFFFFFF : Theme.TEXT_MUTED;
        Fonts.drawCenteredString(Fonts.SF_MEDIUM, keyName, tagX + (tagW / 2.0F), tagY + 1.5F, 8.5F, ColorUtil.multiplyAlpha(col, effectiveAlpha));
    }

    /** Все навешенные клавиши не влезают в тег — показываем первую и счётчик остальных. */
    private String shorten() {
        String full = setting.getDisplayValue();
        if (setting.size() <= 1 || Fonts.SF_MEDIUM.getWidth(full, 8.5F) <= 92.0F) {
            return full;
        }
        return setting.getDisplayValue(0) + " +" + (setting.size() - 1);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (visAnim.getValue() <= 0.5F) return;
        boolean hovered = isHovered(mouseX, mouseY, lastX, lastY, lastW, lastH);

        if (!listening) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && hovered) {
                listening = true;
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT && hovered && setting.isBound()) {
                BindMode mode = setting.toggleLastMode();
                Notifications.push(setting.getDisplayValue(setting.size() - 1) + ": "
                        + (mode == BindMode.HOLD ? "пока клавиша зажата" : "переключение"));
            }
        } else {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                Binds.assign(setting, KeyUtil.fromMouseButton(button));
                listening = false;
            } else if (!hovered) {
                listening = false;
            }
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening) return;

        if (keyCode == InputConstants.KEY_ESCAPE) {
            // Отмена: старые клавиши бинда остаются нетронутыми.
            listening = false;
        } else if (keyCode == InputConstants.KEY_DELETE) {
            setting.clear();
            listening = false;
        } else {
            Binds.assign(setting, keyCode);
        }
    }
}