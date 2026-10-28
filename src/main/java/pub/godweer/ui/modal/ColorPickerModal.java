package pub.godweer.ui.modal;

import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.math.MathUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

/**
 * Create by daun kvass
 */
public class ColorPickerModal implements Modal {
    private final ColorSetting setting;
    private boolean finished;

    private float x, y;
    private boolean initialized = false;
    private final Float initialSpawnX;
    private final Float initialSpawnY;

    private boolean draggingWindow;
    private float dragOffsetX, dragOffsetY;

    private float hue, sat, val, alphaVal;
    private boolean draggingSV, draggingHue, draggingAlpha;

    public ColorPickerModal(ColorSetting setting, float spawnX, float spawnY) {
        this.setting = setting;
        this.initialSpawnX = spawnX;
        this.initialSpawnY = spawnY;
        initHsv();
    }

    public ColorPickerModal(ColorSetting setting) {
        this.setting = setting;
        this.initialSpawnX = null;
        this.initialSpawnY = null;
        initHsv();
    }

    private void initHsv() {
        float[] hsv = ColorUtil.toHsv(setting.getValue());
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alphaVal = ColorUtil.alpha(setting.getValue()) / 255.0F;
    }

    @Override
    public void render(int mouseX, int mouseY, float screenWidth, float screenHeight, float alpha) {
        float w = 115.0F;
        float h = 118.0F;

        if (!initialized) {
            if (initialSpawnX != null && initialSpawnY != null) {
                this.x = MathUtil.clamp(initialSpawnX, 5.0F, screenWidth - w - 5.0F);
                this.y = MathUtil.clamp(initialSpawnY, 5.0F, screenHeight - h - 5.0F);
            } else {
                this.x = (screenWidth - w) / 2.0F;
                this.y = (screenHeight - h) / 2.0F;
            }
            initialized = true;
        }

        if (draggingWindow) {
            this.x = mouseX - dragOffsetX;
            this.y = mouseY - dragOffsetY;
        }

        Render2D.drawBlur(x, y, w, h, 6.0F, ColorUtil.rgba(18, 18, 22, Math.round(210 * alpha)), alpha);
        Render2D.drawRoundedRect(x, y, w, h, 6.0F, ColorUtil.multiplyAlpha(Theme.BG_CARD, alpha));

        Fonts.drawString(Fonts.SF_MEDIUM, setting.getName(), x + 7, y + 4.5F, 9.5F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));

        float svX = x + 7;
        float svY = y + 16;
        float svW = 84;
        float svH = 78;

        if (draggingSV) {
            sat = MathUtil.clamp01((mouseX - svX) / svW);
            val = 1.0F - MathUtil.clamp01((mouseY - svY) / svH);
            updateColor();
        }

        int pureHue = ColorUtil.fromHsv(hue, 1.0F, 1.0F, 255);
        Render2D.drawGradientRound(svX, svY, svW, svH, 2.5F, ColorUtil.WHITE, pureHue, ColorUtil.BLACK, ColorUtil.BLACK);

        float curX = svX + (sat * svW);
        float curY = svY + ((1.0F - val) * svH);
        Render2D.drawCircle(curX, curY, 2.0F, ColorUtil.WHITE);

        float aX = x + 96;
        float aY = svY;
        float aW = 12;
        float aH = svH;

        if (draggingAlpha) {
            alphaVal = 1.0F - MathUtil.clamp01((mouseY - aY) / aH);
            updateColor();
        }

        Render2D.drawRoundedRect(aX, aY, aW, aH, 2.0F, ColorUtil.multiplyAlpha(Theme.BG_ELEMENT, alpha));
        Render2D.drawRoundedRect(aX, aY + ((1.0F - alphaVal) * (aH - 3.0F)), aW, 3.0F, 1.0F, ColorUtil.WHITE);

        float hX = svX;
        float hY = y + 100;
        float hW = svW + aW + 5;
        float hH = 7;

        if (draggingHue) {
            hue = MathUtil.clamp01((mouseX - hX) / hW);
            updateColor();
        }

        for (int i = 0; i < (int) hW; i++) {
            Render2D.drawRect(hX + i, hY, 1, hH, ColorUtil.multiplyAlpha(ColorUtil.fromHsv(i / hW, 1.0F, 1.0F, 255), alpha));
        }
        Render2D.drawRoundedRect(hX + (hue * (hW - 2.0F)), hY - 1, 2.0F, hH + 2, 1.0F, ColorUtil.WHITE);
    }

    private void updateColor() {
        int packed = ColorUtil.fromHsv(hue, sat, val, Math.round(alphaVal * 255));
        setting.setValue(packed);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            float w = 115, h = 118;

            float svX = x + 7, svY = y + 16, svW = 84, svH = 78;
            if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                draggingSV = true;
                return true;
            }

            float aX = x + 96, aY = svY, aW = 12, aH = svH;
            if (mouseX >= aX && mouseX <= aX + aW && mouseY >= aY && mouseY <= aY + aH) {
                draggingAlpha = true;
                return true;
            }

            float hX = svX, hY = y + 100, hW = svW + aW + 5, hH = 7;
            if (mouseX >= hX && mouseX <= hX + hW && mouseY >= hY && mouseY <= hY + hH) {
                draggingHue = true;
                return true;
            }

            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                draggingWindow = true;
                dragOffsetX = (float) (mouseX - x);
                dragOffsetY = (float) (mouseY - y);
                return true;
            }

            finished = true;
            return true;
        }
        return true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingSV = false;
            draggingHue = false;
            draggingAlpha = false;
            draggingWindow = false;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_RETURN) {
            finished = true;
            return true;
        }
        return false;
    }

    @Override
    public void charTyped(int codePoint) {}

    @Override
    public boolean isFinished() {
        return finished;
    }
}
