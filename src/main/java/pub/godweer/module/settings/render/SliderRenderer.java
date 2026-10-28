package pub.godweer.module.settings.render;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.localization.Localization;
import pub.godweer.util.math.Animation;
import pub.godweer.util.math.MathUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

import java.util.Locale;

/**
 * Create by daun kvass
 */
public class SliderRenderer extends SettingRenderer<SliderSetting> {
    private final Animation visualAnim = new Animation(0.0F, 0.25F);
    private boolean dragging;
    private boolean editing;
    private String editBuffer = "";
    private float lastX, lastY, lastW;

    public SliderRenderer(SliderSetting setting) {
        super(setting);
        visualAnim.setValue(getPercent());
    }

    private float getPercent() {
        return (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
    }

    @Override
    public float getHeight() {
        return 20.0F * visAnim.getValue();
    }

    @Override
    public void render(float x, float y, float width, float height, int mouseX, int mouseY, float alpha) {
        if (visAnim.getValue() <= 0.01F) return;

        this.lastX = x; this.lastY = y; this.lastW = width;
        float effectiveAlpha = alpha * visAnim.getValue();

        visualAnim.setTarget(getPercent());
        visualAnim.update();

        String displayName = Localization.get(setting.getName());
        Fonts.drawString(Fonts.SF_MEDIUM, displayName, x + 6, y + 1.0F, 9.5F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, effectiveAlpha));

        String valStr = editing ? editBuffer + "|" : String.format(Locale.US, "%.1f", setting.getValue());
        float valW = Fonts.SF_MEDIUM.getWidth(valStr, 9.0F);
        float valX = x + width - valW - 6;
        Fonts.drawString(Fonts.SF_MEDIUM, valStr, valX, y + 1.5F, 9.0F,
                editing ? ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, effectiveAlpha) : ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, effectiveAlpha * 0.85F));

        float barX = x + 6;
        float barY = y + 13.0F;
        float barW = width - 12;
        float barH = 2.0F;

        if (dragging) {
            float percent = MathUtil.clamp01((float) ((mouseX - barX) / barW));
            float newValue = setting.getMin() + (setting.getMax() - setting.getMin()) * percent;
            setting.setValue(newValue);
        }

        Render2D.drawRoundedRect(barX, barY, barW, barH, 1.0F, ColorUtil.multiplyAlpha(0xFF1E202A, effectiveAlpha));

        float fillW = barW * visualAnim.getValue();
        Render2D.drawRoundedRect(barX, barY, fillW, barH, 1.0F, ColorUtil.multiplyAlpha(Theme.getAccentColor(), effectiveAlpha));

        float thumbSize = 6.0F;
        float thumbX = barX + fillW - (thumbSize / 2.0F);
        float thumbY = barY + (barH - thumbSize) / 2.5F;
        Render2D.drawRoundedRect(thumbX, thumbY, thumbSize + 1, thumbSize - 1.5f, 1.5f, ColorUtil.multiplyAlpha(Theme.getAccentColor(), effectiveAlpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && visAnim.getValue() > 0.5F) {
            if (isHovered(mouseX, mouseY, lastX + lastW - 30, lastY, 30, 10)) {
                editing = true;
                editBuffer = "";
                return;
            }
            if (isHovered(mouseX, mouseY, lastX + 4, lastY + 8, lastW - 8, 12)) {
                editing = false;
                dragging = true;
            }
        } else {
            editing = false;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) return;
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            tryApply();
        } else if (keyCode == InputConstants.KEY_ESCAPE) {
            editing = false;
        } else if (keyCode == InputConstants.KEY_BACKSPACE && !editBuffer.isEmpty()) {
            editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
        }
    }

    @Override
    public void charTyped(int codePoint) {
        if (!editing) return;
        char c = (char) codePoint;
        if (Character.isDigit(c) || c == '.' || c == '-') {
            editBuffer += c;
        }
    }

    private void tryApply() {
        try {
            float parsed = Float.parseFloat(editBuffer);
            setting.setValue(parsed);
        } catch (Exception ignored) {}
        editing = false;
    }
}
