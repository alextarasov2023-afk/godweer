package pub.godweer.module.impl.render;

import net.minecraft.world.entity.HumanoidArm;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Своя модель рук: позиция, повороты и масштаб каждой руки от первого лица.
 */
public final class ViewModel extends Module {

    public static ViewModel INSTANCE;

    public final HeaderSetting rightHeader = header("Правая рука");
    public final SliderSetting rightX = slider("Правая X", 0.0F, -2.0F, 2.0F, 0.01F);
    public final SliderSetting rightY = slider("Правая Y", 0.0F, -2.0F, 2.0F, 0.01F);
    public final SliderSetting rightZ = slider("Правая Z", 0.0F, -2.0F, 2.0F, 0.01F);
    public final SliderSetting rightYaw = slider("Правая поворот Y", 0.0F, -180.0F, 180.0F, 1.0F);
    public final SliderSetting rightPitch = slider("Правая поворот X", 0.0F, -180.0F, 180.0F, 1.0F);
    public final SliderSetting rightRoll = slider("Правая поворот Z", 0.0F, -180.0F, 180.0F, 1.0F);
    public final SliderSetting rightScale = slider("Правая размер", 1.0F, 0.1F, 3.0F, 0.01F);

    public final HeaderSetting leftHeader = header("Левая рука");
    public final SliderSetting leftX = slider("Левая X", 0.0F, -2.0F, 2.0F, 0.01F);
    public final SliderSetting leftY = slider("Левая Y", 0.0F, -2.0F, 2.0F, 0.01F);
    public final SliderSetting leftZ = slider("Левая Z", 0.0F, -2.0F, 2.0F, 0.01F);
    public final SliderSetting leftYaw = slider("Левая поворот Y", 0.0F, -180.0F, 180.0F, 1.0F);
    public final SliderSetting leftPitch = slider("Левая поворот X", 0.0F, -180.0F, 180.0F, 1.0F);
    public final SliderSetting leftRoll = slider("Левая поворот Z", 0.0F, -180.0F, 180.0F, 1.0F);
    public final SliderSetting leftScale = slider("Левая размер", 1.0F, 0.1F, 3.0F, 0.01F);

    public ViewModel() {
        super("ViewModel", "Позиция, поворот и размер рук", Category.RENDER);
        INSTANCE = this;
    }

    /** Набор правок одной руки в порядке: смещение -> повороты -> масштаб. */
    public record Hand(float x, float y, float z, float yaw, float pitch, float roll, float scale) {

        public boolean empty() {
            return this.x == 0.0F && this.y == 0.0F && this.z == 0.0F
                    && this.yaw == 0.0F && this.pitch == 0.0F && this.roll == 0.0F
                    && this.scale == 1.0F;
        }
    }

    public Hand hand(HumanoidArm arm) {
        if (arm == HumanoidArm.RIGHT) {
            return new Hand(this.rightX.get(), this.rightY.get(), this.rightZ.get(),
                    this.rightYaw.get(), this.rightPitch.get(), this.rightRoll.get(), this.rightScale.get());
        }
        return new Hand(this.leftX.get(), this.leftY.get(), this.leftZ.get(),
                this.leftYaw.get(), this.leftPitch.get(), this.leftRoll.get(), this.leftScale.get());
    }
}
