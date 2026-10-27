package pub.godweer.kitty.module.settings.render;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.kitty.module.settings.SettingRenderer;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.client.localization.Localization;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;

import java.util.HashMap;
import java.util.Map;

/**
 * Create by daun kvass
 */
public class ModeRenderer extends SettingRenderer<ModeSetting> {
    private final Map<String, float[]> chipBounds = new HashMap<>();
    private float calculatedHeight = 26.0F;
    private float lastRenderWidth = 115.0F;

    public ModeRenderer(ModeSetting setting) {
        super(setting);
        this.calculatedHeight = calculateRequiredHeight(this.lastRenderWidth);
    }

    private float calculateRequiredHeight(float width) {
        float startX = 6.0F;
        float curX = startX;
        float curY = 12.5F;
        float chipH = 12.0F;
        float gap = 2.5F;
        float maxRowW = Math.max(20.0F, width - 12.0F);

        float fontSize = 8.5F;
        float padX = 4.5F;

        for (String mode : setting.getModes()) {
            String displayMode = Localization.get(mode);
            float textW = Fonts.SF_MEDIUM.getWidth(displayMode, fontSize);
            float chipW = textW + (padX * 2.0F);

            if (curX + chipW > startX + maxRowW && curX > startX) {
                curX = startX;
                curY += chipH + gap;
            }
            curX += chipW + gap;
        }

        return curY + chipH + 3.0F;
    }

    @Override
    public float getHeight() {
        return this.calculatedHeight * visAnim.getValue();
    }

    @Override
    public void render(float x, float y, float width, float height, int mouseX, int mouseY, float alpha) {
        if (visAnim.getValue() <= 0.01F) return;

        this.lastRenderWidth = width;
        float effectiveAlpha = alpha * visAnim.getValue();

        String displayName = Localization.get(setting.getName());
        Fonts.drawString(Fonts.SF_MEDIUM, displayName, x + 6.0F, y + 1.5F, 9.5F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, effectiveAlpha));

        chipBounds.clear();
        float startX = x + 6.0F;
        float curX = startX;
        float curY = y + 12.5F;
        float chipH = 12.0F;
        float gap = 2.5F;
        float maxRowW = Math.max(20.0F, width - 12.0F);

        float fontSize = 8.5F;
        float padX = 4.5F;

        for (String mode : setting.getModes()) {
            boolean active = setting.is(mode);
            String displayMode = Localization.get(mode);

            float textW = Fonts.SF_MEDIUM.getWidth(displayMode, fontSize);
            float chipW = textW + (padX * 2.0F);

            if (curX + chipW > startX + maxRowW && curX > startX) {
                curX = startX;
                curY += chipH + gap;
            }

            chipBounds.put(mode, new float[]{curX, curY, chipW, chipH});

            int bg = active ? Theme.getAccentColor() : 0x351C1F2E;
            int textCol = active ? 0xFFFFFFFF : Theme.TEXT_MUTED;

            Render2D.drawRoundedRect(curX, curY, chipW, chipH, 2.5F, ColorUtil.multiplyAlpha(bg, effectiveAlpha));
            Fonts.drawString(Fonts.SF_MEDIUM, displayMode, curX + padX, curY + 2.0F, fontSize, ColorUtil.multiplyAlpha(textCol, effectiveAlpha));

            curX += chipW + gap;
        }

        this.calculatedHeight = (curY - y) + chipH + 3.0F;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && visAnim.getValue() > 0.5F) {
            for (Map.Entry<String, float[]> entry : chipBounds.entrySet()) {
                float[] b = entry.getValue();
                if (isHovered(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                    setting.setValue(entry.getKey());
                    break;
                }
            }
        }
    }
}