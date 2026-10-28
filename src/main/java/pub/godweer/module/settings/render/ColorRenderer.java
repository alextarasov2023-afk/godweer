package pub.godweer.module.settings.render;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.ui.clickgui.PanelRefractions;
import pub.godweer.ui.modal.ColorPickerModal;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.localization.Localization;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

/**
 * Create by daun kvass
 */
public class ColorRenderer extends SettingRenderer<ColorSetting> {
    private float lastX, lastY, lastW, lastH;

    public ColorRenderer(ColorSetting setting) {
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

        float previewW = 14.0F;
        float previewH = 8.0F;
        float previewX = x + width - previewW - 6.0F;
        float previewY = y + (height - previewH) / 2.0F;

        Render2D.drawRoundedRect(previewX, previewY, previewW, previewH, 2.0F, ColorUtil.multiplyAlpha(setting.getValue(), effectiveAlpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && visAnim.getValue() > 0.5F && isHovered(mouseX, mouseY, lastX, lastY, lastW, lastH)) {
            float spawnX = lastX + lastW + 8.0F;
            float spawnY = lastY;
            PanelRefractions.openModal(new ColorPickerModal(setting, spawnX, spawnY));
        }
    }
}