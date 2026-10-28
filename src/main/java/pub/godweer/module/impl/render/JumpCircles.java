package pub.godweer.module.impl.render;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.JumpEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.style.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
public class JumpCircles extends Module {
    public static JumpCircles INSTANCE;

    private final HeaderSetting mainHeader = header("Jumps");
    public final CheckBox selfOnly = checkbox("Only self", true);
    public final SliderSetting radius = slider("Radius", 3.5f, 1.0f, 10.0f, 0.1f);
    public final SliderSetting duration = slider("Duration", 0.8f, 0.2f, 3.0f, 0.1f);
    public final SliderSetting ringWidth = slider("Width ring", 0.35f, 0.05f, 1.5f, 0.05f);

    private final HeaderSetting fxHeader = header("Effects");
    public final SliderSetting distortion = slider("Strength distortion ", 0.75f, 0.0f, 2.0f, 0.05f);
    public final SliderSetting glowIntensity = slider("Brightness glow", 1.5f, 0.0f, 4.0f, 0.1f);

    private final HeaderSetting colorHeader = header("Color");
    public final CheckBox themeColor = checkbox("Color from Theme", true);
    public final ColorSetting circleColor = color("Color ", 0xFF6A00FF).visible(() -> !themeColor.getValue());

    @Getter
    private final List<CircleData> circles = new ArrayList<>();

    public JumpCircles() {
        super("JumpCircles", "При прыжке такой бдышь", Category.RENDER);
        INSTANCE = this;
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (!isState()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getPlayer() == null) return;

        if (selfOnly.getValue() && event.getPlayer() != mc.player) {
            return;
        }

        Vec3 pos = event.getPlayer().position();
        int color = themeColor.getValue() ? Theme.getAccentColor() : circleColor.getValue();

        circles.add(new CircleData(
                pos,
                System.currentTimeMillis(),
                (long) (duration.getValue() * 1000L),
                radius.getValue(),
                ringWidth.getValue(),
                distortion.getValue(),
                glowIntensity.getValue(),
                color
        ));
    }

    public void update() {
        long now = System.currentTimeMillis();
        circles.removeIf(c -> (now - c.spawnTime) >= c.maxLifetime);
    }

    public boolean hasActiveCircles() {
        return isState() && !circles.isEmpty();
    }

    @Getter
    public static class CircleData {
        private final Vec3 origin;
        private final long spawnTime;
        private final long maxLifetime;
        private final float maxRadius;
        private final float ringWidth;
        private final float distortion;
        private final float glow;
        private final int color;

        public CircleData(Vec3 origin, long spawnTime, long maxLifetime, float maxRadius, float ringWidth, float distortion, float glow, int color) {
            this.origin = origin;
            this.spawnTime = spawnTime;
            this.maxLifetime = maxLifetime;
            this.maxRadius = maxRadius;
            this.ringWidth = ringWidth;
            this.distortion = distortion;
            this.glow = glow;
            this.color = color;
        }

        public float getProgress() {
            float p = (float) (System.currentTimeMillis() - spawnTime) / (float) maxLifetime;
            return Math.clamp(p, 0.0f, 1.0f);
        }
    }
}