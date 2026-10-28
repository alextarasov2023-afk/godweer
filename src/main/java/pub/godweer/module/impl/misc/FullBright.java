package pub.godweer.module.impl.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import pub.godweer.Client;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.GameTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.SliderSetting;

public class FullBright extends Module {
    public static final FullBright INSTANCE = new FullBright();

    public CheckBox dynamic = checkbox("Dynamic", true);
    public SliderSetting maxBrightness = slider("Max Gamma", 10.0f, 1.0f, 15.0f, 0.5f);

    private double smoothedGamma = Double.NaN;

    public FullBright() {
        super("FullBright", "Screen-space deferred lighting", Category.MISC);
    }

    @Override
    protected void onEnable() {
        if (mc.options != null) {
            this.smoothedGamma = mc.options.gamma().get();
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options != null) {
            this.smoothedGamma = mc.options.gamma().get();
        }
    }

    @EventTarget
    private void onTick(GameTickEvent event) {
        if (mc.options == null) return;

        double vanillaGamma = mc.options.gamma().get();

        if (!isEnabled()) {
            this.smoothedGamma = vanillaGamma;
            return;
        }

        double targetGamma;

        if (dynamic.get()) {
            if (mc.player != null && mc.level != null) {
                BlockPos pos = mc.player.blockPosition();
                int blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
                int skyLight = mc.level.getBrightness(LightLayer.SKY, pos);
                int totalLight = Math.max(blockLight, skyLight);

                float darknessFactor = 1.0f - (totalLight / 15.0f);

                targetGamma = vanillaGamma + (maxBrightness.get() - vanillaGamma) * darknessFactor;
            } else {
                targetGamma = vanillaGamma;
            }
        } else {
            targetGamma = maxBrightness.get();
        }

        if (Double.isNaN(smoothedGamma)) {
            smoothedGamma = vanillaGamma;
        }

        smoothedGamma += (targetGamma - smoothedGamma) * 0.15;
    }

    public static double modifyGamma(double vanillaGamma) {
        FullBright module = Client.INSTANCE.moduleManager.getFullBright();
        if (module == null || !module.isEnabled() || Double.isNaN(module.smoothedGamma)) {
            return vanillaGamma;
        }
        return Math.max(vanillaGamma, module.smoothedGamma);
    }

    public static boolean shouldSuppressDarkness() {
        FullBright module = Client.INSTANCE.moduleManager.getFullBright();
        return module != null && module.isEnabled();
    }
}