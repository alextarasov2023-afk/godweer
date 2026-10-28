package pub.godweer.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import pub.godweer.util.hitfx.HitStrikeRenderer;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.AttackEvent;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * HitEffect: шейдерная молния с неба бьёт в сущность при ударе — 1:1 та же
 * молния что и в TargetEsp Lightning (halo+2 core, ветвления, bloom).
 * Режим "Lightning": 10 форм молнии, каждый удар — разная анимация.
 * Без грома и без шлейфов.
 */
public final class HitEffect extends Module {

    private final HitStrikeRenderer renderer = new HitStrikeRenderer();

    private final ModeSetting lightning = mode("Lightning",
            "Lightning",
            "Lightning");
    private final ColorSetting color = color("Цвет", 0xFF4FD8FF);
    private final SliderSetting beamHeight = slider("Высота луча", 14.0F, 6.0F, 32.0F, 1.0F);
    private final SliderSetting widthScale = slider("Толщина", 1.0F, 0.5F, 2.0F, 0.05F);

    public HitEffect() {
        super("HitEffect", "Молния с неба при ударе — та же что в TargetEsp Lightning", Category.RENDER);
    }

    private int pickForm() {
        // один режим Lightning: каждый удар — случайная из 10 форм
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(HitStrikeRenderer.FORM_COUNT);
    }

    @Override
    protected void onDisable() {
        renderer.release();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!inGame() || player() == null || level() == null) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !target.isAlive()) return;

        renderer.strike(target, pickForm());
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (event.getDeltaTracker() == null || event.getClient() == null) return;
        Minecraft check = event.getClient();
        if (check.player == null || check.level == null || check.gameRenderer == null) {
            renderer.release();
            return;
        }
        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderer.render(color.getValue(),
                beamHeight.getValue().floatValue(),
                widthScale.getValue().floatValue(),
                tickDelta);
    }
}
