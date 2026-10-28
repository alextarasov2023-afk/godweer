package pub.godweer.module.impl.render;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.pipeline.PiplinePost;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shader chams: the entity model itself is repainted with procedural
 * animated fragment shaders (assets/godweer/shaders/world/chams), plus an
 * optional depth-ignoring through-walls ghost pass that keeps the body
 * visible behind blocks.
 *
 * Styles come in two flavours. Picking one name paints everyone with that
 * shader; Random gives each entity its own shader and keeps it (chosen from
 * the entity id, so it does not flicker), Cycle walks through all of them on
 * a timer. The list is derived from PiplinePost.CHAMS_STYLES, so adding a
 * shader file is enough to get it into the GUI.
 *
 * Per-frame parameters still travel through the vertex tint, because the
 * chams pipelines have no per-entity uniforms: rgb = colour * pulse,
 * alpha = animation phase (glass: opacity).
 */
public final class Chams extends Module {

    public static final String RANDOM = "Random";
    public static final String CYCLE = "Cycle";

    private static final String GLASS = "Glass";
    private static final String DEFAULT_STYLE = "Plasma";

    public static Chams INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting style = mode("Style", DEFAULT_STYLE, styleNames());
    private final SliderSetting cycleSeconds = slider("Cycle Seconds", 3.0f, 1.0f, 12.0f, 0.5f)
            .visible(this::isCycle);
    private final CheckBox throughWalls = checkbox("Through Walls", true);
    private final SliderSetting ghostStrength = slider("Ghost Strength", 0.6f, 0.1f, 1.0f, 0.05f)
            .visible(throughWalls::getValue);
    private final CheckBox players = checkbox("Players", true);
    private final CheckBox mobs = checkbox("Mobs", true);
    private final CheckBox self = checkbox("Self", false);
    // Броня не рисуется вообще: остаётся только закрашенное тело, без слоя брони поверх
    // (и без наплечников/элитры, которые vanilla подаёт тем же слоем).
    private final CheckBox hideArmor = checkbox("Hide Armor", true);
    private final SliderSetting range = slider("Range", 128.0f, 8.0f, 256.0f, 4.0f);

    private final HeaderSetting look = header("Look");
    private final ColorSetting color = color("Color", Theme.getAccentColor());
    private final SliderSetting speed = slider("Speed", 1.4f, 0.1f, 6.0f, 0.1f);
    private final SliderSetting intensity = slider("Intensity", 1.15f, 0.1f, 2.5f, 0.05f);
    private final SliderSetting opacity = slider("Opacity", 0.35f, 0.05f, 1.0f, 0.05f)
            .visible(this::isGlass);

    public Chams() {
        super("Chams",
                "Шейдерное закрашивание моделей: " + PiplinePost.CHAMS_STYLES.length
                        + " анимированных стилей, каждому игроку можно свой",
                Category.RENDER);
        INSTANCE = this;
    }

    private static String[] styleNames() {
        List<String> names = new ArrayList<>();
        for (String slug : PiplinePost.CHAMS_STYLES) {
            names.add(Character.toUpperCase(slug.charAt(0)) + slug.substring(1));
        }
        names.add(RANDOM);
        names.add(CYCLE);
        return names.toArray(new String[0]);
    }

    /** Только реальные шейдеры: Random и Cycle выбирают из них, а не из самих себя. */
    public List<String> shaderStyles() {
        return List.of(PiplinePost.CHAMS_STYLES);
    }

    public boolean isRandom() {
        return style.is(RANDOM);
    }

    public boolean isCycle() {
        return style.is(CYCLE);
    }

    /**
     * Стиль, выбранный руками. Конфиг пишет в ModeSetting значение как есть, без
     * сверки со списком, поэтому неизвестное или устаревшее имя уходит в базовый
     * шейдер: неверный суффикс — это падение прямо во время рендера модели.
     */
    public String fixedStyle() {
        String value = style.getValue();
        if (value == null) return DEFAULT_STYLE;
        String slug = value.toLowerCase(Locale.ROOT);
        return List.of(PiplinePost.CHAMS_STYLES).contains(slug) ? slug : DEFAULT_STYLE.toLowerCase(Locale.ROOT);
    }

    public boolean isGlass() {
        return style.is(GLASS);
    }

    public boolean isThroughWalls() {
        return throughWalls.getValue();
    }

    public float getGhostStrength() {
        return ghostStrength.getValue().floatValue();
    }

    public boolean showPlayers() {
        return players.getValue();
    }

    public boolean showMobs() {
        return mobs.getValue();
    }

    public boolean showSelf() {
        return self.getValue();
    }

    public boolean isHideArmor() {
        return hideArmor.getValue();
    }

    public float getCycleSeconds() {
        return cycleSeconds.getValue().floatValue();
    }

    public float getRange() {
        return range.getValue().floatValue();
    }

    public int getColor() {
        int c = color.getValue();
        // Стекло без яркого ядра выглядит пустым: оставляем ему запас на свечение.
        return isGlass() ? ColorUtil.rgba(Math.max(90, ColorUtil.red(c)), Math.max(90, ColorUtil.green(c)),
                Math.max(90, ColorUtil.blue(c)), ColorUtil.alpha(c)) : c;
    }

    public float getOpacity() {
        return opacity.getValue().floatValue();
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public float getIntensity() {
        return intensity.getValue().floatValue();
    }
}
