package pub.godweer.hud;

import pub.godweer.event.list.Render2DEvent;

/**
 * Draggable HUD element. Move with LMB while chat is open.
 */
public abstract class HudElement {
    protected final String id;
    protected final String name;
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected boolean enabled = true;
    protected float grabDX;
    protected float grabDY;
    protected float scale = 1.0F;

    protected HudElement(String id, String name, float x, float y, float width, float height) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void draw(Render2DEvent event);

    public boolean shouldRender() {
        return enabled;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        float w = width * scale;
        float h = height * scale;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public float getScale() { return scale; }
    public void setScale(float v) {
        this.scale = Math.max(0.55F, Math.min(2.2F, v));
    }
    public float getScaledWidth() { return width * scale; }
    public float getScaledHeight() { return height * scale; }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
