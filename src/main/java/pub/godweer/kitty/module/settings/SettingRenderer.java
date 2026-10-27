package pub.godweer.kitty.module.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pub.godweer.auxiliary.math.Animation;

/**
 * Create by daun kvass
 */
@Getter
@RequiredArgsConstructor
public abstract class SettingRenderer<T extends Setting<?>> {
    protected final T setting;
    protected final Animation visAnim = new Animation(1.0F, 0.25F);

    public void updateVisibility() {
        visAnim.setTarget(setting.isVisible() ? 1.0F : 0.0F);
        visAnim.update();
    }

    public abstract void render(float x, float y, float width, float height, int mouseX, int mouseY, float alpha);
    public abstract void mouseClicked(double mouseX, double mouseY, int button);
    public void mouseReleased(double mouseX, double mouseY, int button) {}
    public void keyPressed(int keyCode, int scanCode, int modifiers) {}
    public void charTyped(int codePoint) {}

    public abstract float getHeight();

    protected boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}