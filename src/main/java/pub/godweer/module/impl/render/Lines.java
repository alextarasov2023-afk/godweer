package pub.godweer.module.impl.render;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.world.LinesWorldRenderer;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.GameTickEvent;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Lines: в мире вокруг игрока растут неоновые трассы. Каждая идёт по ортогональной
 * сетке блоков шагами 1..3 блока и поворачивает ровно на 90°, поэтому силуэт читается
 * как схема, а не как случайный клубок. Острие бежит плавно (субтиковая интерполяция),
 * хвост темнее острия, линия появляется и растворяется квадратичной огибающей.
 */
public final class Lines extends Module {

    public final SliderSetting count = slider("Lines: Количество", 80.0f, 10.0f, 200.0f, 1.0f);
    public final SliderSetting speed = slider("Lines: Скорость, блок/с", 4.0f, 0.5f, 12.0f, 0.1f);
    public final SliderSetting turns = slider("Lines: Поворотов", 12.0f, 4.0f, 24.0f, 1.0f);
    public final SliderSetting reach = slider("Lines: Дистанция спавна", 18.0f, 6.0f, 40.0f, 1.0f);
    public final SliderSetting thickness = slider("Lines: Толщина", 0.045f, 0.01f, 0.2f, 0.005f);

    public final CheckBox glow = checkbox("Lines: Неоновое свечение", true);
    public final CheckBox dashed = checkbox("Lines: Пунктир", false);
    public final SliderSetting dashScale = slider("Lines: Штрих", 0.35f, 0.05f, 4.0f, 0.05f)
            .visible(() -> this.dashed.getValue());
    public final CheckBox throughWalls = checkbox("Lines: сквозь блоки", false);

    public final CheckBox themeColor = checkbox("Цвет из темы", true);
    public final ColorSetting color = color("Цвет", ColorUtil.rgba(53, 200, 255, 255))
            .visible(() -> !this.themeColor.getValue());

    private final LinesWorldRenderer renderer = new LinesWorldRenderer();

    public Lines() {
        super("Lines", "Растущие по ортогональной сетке неоновые трассы вокруг игрока", Category.RENDER);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        this.renderer.tick(this);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (event.getDeltaTracker() == null || event.getClient() == null) return;
        this.renderer.render(this,
                event.getDeltaTracker().getGameTimeDeltaPartialTick(false), event.getClient());
    }

    @Override
    protected void onDisable() {
        this.renderer.release();
    }
}
