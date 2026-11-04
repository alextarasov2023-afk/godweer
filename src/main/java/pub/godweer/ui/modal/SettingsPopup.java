package pub.godweer.ui.modal;

import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.localization.Localization;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
public class SettingsPopup {
    private final ModeSetting language = new ModeSetting("Language", "ENG", "ENG", "RU");
    private final ModeSetting bgMode = new ModeSetting("Background", Theme.getBackgroundMode(), "Blur", "None");
    private final ColorSetting accent = new ColorSetting("Accent", Theme.getAccentColor());

    private final List<SettingRenderer<?>> renderers = new ArrayList<>();
    private float cachedHeight = 135.0F;
    private final float width = 125.0F;

    private int lastSyncedAccent = -1;
    private String lastSyncedBgMode = "";

    private static final int[] ACCENT_PRESETS = {
            ColorUtil.rgba(255, 170, 45, 255),
            ColorUtil.rgba(0, 160, 255, 255),
            ColorUtil.rgba(70, 220, 120, 255),
            ColorUtil.rgba(160, 110, 255, 255),
            ColorUtil.rgba(240, 90, 90, 255)
    };

    public SettingsPopup() {
        renderers.add(language.createRenderer());
        renderers.add(bgMode.createRenderer());
        renderers.add(accent.createRenderer());
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return cachedHeight;
    }

    public void syncFromTheme() {
        if (!bgMode.getValue().equalsIgnoreCase(Theme.getBackgroundMode())) {
            bgMode.setValue(Theme.getBackgroundMode());
        }
        if (accent.getValue() != Theme.getAccentColor()) {
            accent.setValue(Theme.getAccentColor());
        }
        this.lastSyncedAccent = Theme.getAccentColor();
        this.lastSyncedBgMode = Theme.getBackgroundMode();
    }

    private void updateConfigs() {
        Localization.setLanguage(language.getValue());
        Theme.setBackgroundMode(bgMode.getValue());
        Theme.setAccentColor(accent.getValue());
        this.lastSyncedAccent = Theme.getAccentColor();
        this.lastSyncedBgMode = Theme.getBackgroundMode();
    }

    public void render(float x, float y, int mouseX, int mouseY, float alpha) {
        if (alpha <= 0.01F) return;

        if (Theme.getAccentColor() != lastSyncedAccent || !Theme.getBackgroundMode().equalsIgnoreCase(lastSyncedBgMode)) {
            syncFromTheme();
        }

        updateConfigs();

        float totalSettingsH = 0.0F;
        for (SettingRenderer<?> sr : renderers) {
            sr.updateVisibility();
            float sH = sr.getHeight();
            if (sH > 0.1F) {
                totalSettingsH += sH + 2.5F;
            }
        }
        this.cachedHeight = 20.0F + 20.0F + totalSettingsH + 5.0F;

        int popupBgColor = 0xFF0A0B0F;
        float popupAlpha = alpha * 0.98F;

        Render2D.drawRoundedRect(x, y, width, cachedHeight, 6.0F, ColorUtil.multiplyAlpha(popupBgColor, popupAlpha));
        Render2D.drawRoundedOutline(x, y, width, cachedHeight, 6.0F, 0.8F,
                ColorUtil.multiplyAlpha(0xFF2D3D53, alpha));

        Fonts.drawString(Fonts.SF_MEDIUM, Localization.get("GUI Settings"), x + 7, y + 6.0F, 9.5F, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));

        float presetY = y + 19.0F;
        float presetX = x + 7.0F;
        for (int preset : ACCENT_PRESETS) {
            Render2D.drawRoundedRect(presetX, presetY, 13.0F, 13.0F, 6.5F, ColorUtil.multiplyAlpha(preset, alpha));
            if (accent.getValue() == preset) {
                Render2D.drawRoundedOutline(presetX - 1.0F, presetY - 1.0F, 15.0F, 15.0F, 7.5F, 1.0F,
                        ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha));
            }
            presetX += 18.0F;
        }

        float curY = y + 19.0F + 20.0F;
        for (SettingRenderer<?> sr : renderers) {
            float sH = sr.getHeight();
            if (sH > 0.05F) {
                sr.render(x + 3, curY, width - 6, sH, mouseX, mouseY, alpha);
                curY += sH + (2.5F * sr.getVisAnim().getValue());
            }
        }
    }

    public boolean mouseClicked(float x, float y, double mouseX, double mouseY, int button) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + cachedHeight) {
            float presetY = y + 19.0F;
            float presetX = x + 7.0F;
            for (int preset : ACCENT_PRESETS) {
                if (mouseX >= presetX - 2 && mouseX <= presetX + 15 && mouseY >= presetY - 2 && mouseY <= presetY + 15) {
                    accent.setValue(preset);
                    updateConfigs();
                    return true;
                }
                presetX += 18.0F;
            }
            float curY = y + 19.0F + 20.0F;
            for (SettingRenderer<?> sr : renderers) {
                float sH = sr.getHeight();
                if (sH > 0.05F) {
                    if (mouseY >= curY && mouseY <= curY + sH) {
                        sr.mouseClicked(mouseX, mouseY, button);
                        updateConfigs();
                        return true;
                    }
                    curY += sH + (2.5F * sr.getVisAnim().getValue());
                }
            }
            return true;
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (SettingRenderer<?> sr : renderers) {
            sr.mouseReleased(mouseX, mouseY, button);
        }
        updateConfigs();
    }
}