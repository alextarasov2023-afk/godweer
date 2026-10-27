package pub.godweer.kitty.module.settings.render;

import pub.godweer.kitty.module.settings.SettingRenderer;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.client.localization.Localization;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;

/**
 * Create by daun kvass
 */
public class HeaderRenderer extends SettingRenderer<HeaderSetting> {

    public HeaderRenderer(HeaderSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return 16.0F * visAnim.getValue();
    }

    @Override
    public void render(float x, float y, float width, float height, int mouseX, int mouseY, float alpha) {
        if (visAnim.getValue() <= 0.01F) return;

        float effectiveAlpha = alpha * visAnim.getValue();
        float textY = y + 3.0F;

        String displayName = Localization.get(setting.getName());
        Fonts.drawString(Fonts.SF_MEDIUM, displayName, x + 6.0F, textY, 8.5F,
                ColorUtil.multiplyAlpha(Theme.getAccentColor(), effectiveAlpha));

        float textWidth = Fonts.SF_MEDIUM.getWidth(displayName, 8.5F);
        float lineX = x + 10.0F + textWidth;
        float lineW = width - (lineX - x) - 6.0F;
        if (lineW > 5.0F) {
            Render2D.drawRoundedRect(lineX, y + (height / 2.0F), lineW, 1.0F, 0.5F,
                    ColorUtil.multiplyAlpha(Theme.getAccentColor(), effectiveAlpha));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {}
}