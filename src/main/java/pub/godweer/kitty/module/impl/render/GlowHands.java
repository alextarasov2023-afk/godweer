package pub.godweer.kitty.module.impl.render;

import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.world.module.GlowHandsRenderer;
import pub.godweer.auxiliary.render.world.module.HandGlowSettings;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Realistic shader glow outline around first-person hands.
 * A depth-difference mask isolates the hand pixels, a Kawase blur cascade
 * turns it into a halo field, and the composite keeps only the area OUTSIDE
 * the (dilated) hand silhouette: a crisp ring with a small gap that never
 * touches the skin, plus a hot core line and a soft outer falloff.
 */
public final class GlowHands extends Module implements HandGlowSettings {

    public static GlowHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final SliderSetting radius = slider("Radius", 10.0f, 2.0f, 40.0f, 1.0f);
    private final SliderSetting gap = slider("Gap", 3.0f, 0.0f, 8.0f, 0.5f);
    private final SliderSetting sharpness = slider("Sharpness", 0.6f, 0.0f, 1.0f, 0.05f);
    private final SliderSetting intensity = slider("Intensity", 1.4f, 0.1f, 4.0f, 0.1f);
    private final SliderSetting pulseSpeed = slider("Pulse Speed", 1.5f, 0.0f, 8.0f, 0.1f);
    private final ColorSetting color = color("Color", ColorUtil.rgba(80, 160, 255, 255));
    private final CheckBox bothHands = checkbox("Both Hands", true);

    private final GlowHandsRenderer renderer = new GlowHandsRenderer();

    public GlowHands() {
        super("GlowHands", "Реалистичная shader glow-обводка рук", Category.RENDER);
        INSTANCE = this;
    }

    public GlowHandsRenderer getRenderer() {
        return this.renderer;
    }

    @Override
    protected void onDisable() {
        this.renderer.release();
    }

    public float getRadius() {
        return radius.getValue().floatValue();
    }

    public float getGap() {
        return gap.getValue().floatValue();
    }

    public float getSharpness() {
        return sharpness.getValue().floatValue();
    }

    public float getIntensity() {
        return intensity.getValue().floatValue();
    }

    public float getPulseSpeed() {
        return pulseSpeed.getValue().floatValue();
    }

    public int getColor() {
        return color.getValue();
    }

    public boolean bothHands() {
        return bothHands.getValue();
    }
}
