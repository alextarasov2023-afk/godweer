package pub.godweer.kitty.module.impl.render;

import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.world.module.ShaderHandsRenderer;

/**
 * Simple animated shader fill for first-person hands.
 */
public final class ShaderHands extends Module {

    public static final String LAVA = "Lava";
    public static final String FROST = "Frost";
    public static final String GALAXY = "Galaxy";
    public static final String EMBER = "Ember";
    public static final String SLIME = "Slime";
    public static final String SMOKE = "Smoke";

    public static ShaderHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting style = mode("Style", LAVA, LAVA, FROST, GALAXY, EMBER, SLIME, SMOKE);
    private final SliderSetting speed = slider("Speed", 1.4f, 0.1f, 5.0f, 0.1f);
    private final SliderSetting opacity = slider("Opacity", 0.9f, 0.0f, 1.0f, 0.02f);
    private final CheckBox bothHands = checkbox("Both Hands", true);

    private final ShaderHandsRenderer renderer = new ShaderHandsRenderer();

    public ShaderHands() {
        super("ShaderHands", "Заливка рук шейдером", Category.RENDER);
        INSTANCE = this;
    }

    public ShaderHandsRenderer getRenderer() {
        return this.renderer;
    }

    public String getStyle() {
        return style.getValue();
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public float getOpacity() {
        return opacity.getValue().floatValue();
    }

    public int getColor() {
        return Theme.getAccentColor();
    }

    public boolean bothHands() {
        return bothHands.getValue();
    }
}
