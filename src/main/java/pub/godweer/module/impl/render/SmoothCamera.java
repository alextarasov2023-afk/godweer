package pub.godweer.module.impl.render;

import pub.godweer.rotation.SmoothCamState;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.GameTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * SmoothCamera — инерция всей камеры: догон поворота, догон тела камеры и плавный
 * переезд между 1, 2 и 3 лицом. Логика в SmoothCameraMixin, состояние в SmoothCamState.
 */
public class SmoothCamera extends Module {

    public static SmoothCamera INSTANCE;

    public final SliderSetting smooth = slider("Smooth", 0.1F, SmoothCamState.getSmoothMin(),
            SmoothCamState.getSmoothMax(), 0.01F);
    public final SliderSetting position = slider("Позиция", 0.12F, SmoothCamState.getPositionMin(),
            SmoothCamState.getPositionMax(), 0.01F);
    public final SliderSetting maxLag = slider("Отставание, блоков", 2.5F, SmoothCamState.getLagMin(),
            SmoothCamState.getLagMax(), 0.1F);
    public final SliderSetting transition = slider("Переход лица, сек", 0.4F, SmoothCamState.getTransitionMin(),
            SmoothCamState.getTransitionMax(), 0.05F);

    public SmoothCamera() {
        super("SmoothCamera", "Инерционный поворот, отставание камеры и плавная смена лица", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        apply();
        SmoothCamState.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        SmoothCamState.setEnabled(false);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        apply();
    }

    /** Значения читаются миксином каждый кадр, поэтому держим их синхронными. */
    private void apply() {
        SmoothCamState.setSmooth(smooth.getValue());
        SmoothCamState.setPosition(position.getValue());
        SmoothCamState.setMaxLag(maxLag.getValue());
        SmoothCamState.setTransition(transition.getValue());
    }
}
