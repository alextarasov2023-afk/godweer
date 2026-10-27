package pub.godweer.kitty.module.impl.render;

import pub.godweer.cc.SmoothCamState;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * SmoothCamera — инерционная камера от 3-го лица.
 * Сама логика в SmoothCameraMixin, состояние — в SmoothCamState.
 */
public class SmoothCamera extends Module {

    public static SmoothCamera INSTANCE;

    public final SliderSetting smooth = slider("Smooth", 0.1F, SmoothCamState.getSmoothMin(), SmoothCamState.getSmoothMax(), 0.01F);

    public SmoothCamera() {
        super("SmoothCamera", "Инерционная камера", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        SmoothCamState.setEnabled(true);
        SmoothCamState.setSmooth(smooth.getValue());
    }

    @Override
    protected void onDisable() {
        SmoothCamState.setEnabled(false);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        SmoothCamState.setSmooth(smooth.getValue());
    }
}
