package pub.godweer.util.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer.Sprite;
import pub.godweer.module.impl.render.TargetEsp;

import java.util.ArrayList;
import java.util.List;

/**
 * Target ESP "Bloom": облако светящихся искр, которое поднимается вдоль силуэта цели
 * снизу вверх и растворяется у макушки.
 *
 * Таблица частиц детерминирована: 96 «паспортов» считаются один раз из хеша сида,
 * поэтому у искр есть собственный характер (свой радиус, скорость подъёма, частота
 * пульсации), а эффект не дрожит от кадра к кадру — позиция вычисляется математически
 * из времени, а не берётся из ParticleEngine.
 *
 * Чётные искры крутятся по часовой, нечётные — против: траектории пересекаются, и
 * вихрь читается плотнее. Высота циклическая (нормализованная координата 0..1 по
 * хитбоксу + запас), яркость ограничена огибающей: нижние 12% — появление, верхние
 * 23% — растворение. Каждая искра рисуется в два слоя: широкий мягкий ореол и
 * маленькое яркое ядро, оба аддитивно.
 *
 * При уроне цели всё облако уходит в кораллово-красный и прибавляет 20% размера.
 *
 * Create by daun kvass
 */
public final class BloomTargetRenderer {

    private static final int PARTICLE_COUNT = 96;
    private static final long SEED = 0xB1005EED5EEDL;

    // огибающая прозрачности: доли высоты, за которые искра появляется и растворяется
    private static final double FADE_IN = 0.12D;
    private static final double FADE_OUT = 0.23D;

    private static final int DAMAGE_TINT = ColorUtil.rgba(255, 66, 61, 255);
    private static final float STRESS_SECONDS = 1.0F / 2.4F;
    private static final float MAX_DELTA_SECONDS = 0.05F;
    private static final float BLOOM_BRIGHTNESS = 1.4F;

    private final double[] baseHeight = new double[PARTICLE_COUNT];
    private final double[] radiusMul = new double[PARTICLE_COUNT];
    private final double[] angle0 = new double[PARTICLE_COUNT];
    private final double[] riseSpeed = new double[PARTICLE_COUNT];
    private final double[] pulseFreq = new double[PARTICLE_COUNT];
    private final double[] spinSpeed = new double[PARTICLE_COUNT];
    private final double[] phase = new double[PARTICLE_COUNT];
    private final int[] direction = new int[PARTICLE_COUNT];

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final List<Sprite> sprites = new ArrayList<>(PARTICLE_COUNT * 2);

    private double time;
    private long lastFrameNanos;
    private LivingEntity lastTarget;
    private int lastHurtTime;
    private float stress;
    private float appear;

    public BloomTargetRenderer() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            this.baseHeight[i] = random(i, 1);
            this.radiusMul[i] = 0.65D + random(i, 2) * 0.70D;
            this.angle0[i] = random(i, 3) * Math.PI * 2.0D;
            this.riseSpeed[i] = 0.16D + random(i, 4) * 0.34D;
            this.pulseFreq[i] = 1.7D + random(i, 5) * 3.8D;
            this.spinSpeed[i] = 0.7D + random(i, 6) * 2.1D;
            this.phase[i] = random(i, 7) * Math.PI * 2.0D;
            this.direction[i] = (i & 1) == 0 ? 1 : -1;
        }
    }

    public void render(TargetEsp module, float tickDelta, LivingEntity target, int color) {
        boolean present = valid(target);
        double dt = tickSeconds();
        this.time += dt;
        this.appear = approach(this.appear, present ? 1.0F : 0.0F, (float) (dt / 0.35));
        if (!present) {
            this.stress = 0.0F;
            this.lastTarget = null;
            this.sprites.clear();
            return;
        }
        if (this.appear <= 0.01F) return;

        LivingEntity entity = target;
        if (entity != this.lastTarget) {
            this.lastHurtTime = 0;
            this.stress = 0.0F;
        }
        this.lastTarget = entity;
        updateStress(entity, dt, module.bloomHurt.get());

        int count = Mth.clamp((int) module.bloomCount.get(), 1, PARTICLE_COUNT);
        double radiusSetting = Math.max(0.05D, module.bloomRadius.get());
        double extraHeight = Math.max(0.0D, module.bloomHeight.get());
        double speed = Math.max(0.05D, module.bloomSpeed.get());
        double baseSize = Math.max(0.002D, module.bloomSize.get());

        Vec3 base = Render3DUtil.interpolatedPosition(entity, tickDelta);
        double totalHeight = Math.max(0.3D, entity.getBbHeight() + extraHeight);
        // хитбокс у всех разный: привязка к ширине не даёт мелкому мобу задохнуться
        // в облаке, а крупному — остаться лысым
        double bodyScale = Math.max(1.0D, entity.getBbWidth() * 1.2D);

        int accent = ColorUtil.lerp(color, DAMAGE_TINT, this.stress);
        double stressBoost = 1.0D + 0.20D * this.stress;

        this.sprites.clear();
        for (int i = 0; i < count; i++) {
            double normalized = this.baseHeight[i] + this.time * this.riseSpeed[i] * speed;
            normalized -= Math.floor(normalized);

            float alpha = (float) envelope(normalized);
            if (alpha <= 0.01F) continue;

            double breathe = 1.0D + 0.28D * Math.sin(this.time * 1.8D + this.phase[i]);
            double radius = radiusSetting * this.radiusMul[i] * breathe * bodyScale;
            double angle = this.angle0[i] + this.time * this.spinSpeed[i] * this.direction[i] * speed;

            double x = base.x + Math.cos(angle) * radius;
            double z = base.z + Math.sin(angle) * radius;
            double y = base.y + normalized * totalHeight;

            double pulse = 0.8D + 0.25D * Math.sin(this.time * this.pulseFreq[i] + this.phase[i] * 2.0D);
            float size = (float) (baseSize * pulse * stressBoost);
            if (size <= 0.001F) continue;

            Vec3 center = new Vec3(x, y, z);
            this.sprites.add(new Sprite(center, (float) (size * 2.6D),
                    ColorUtil.withAlpha(accent, alpha * 0.30F * this.appear)));
            this.sprites.add(new Sprite(center, size,
                    ColorUtil.withAlpha(brighten(accent, 1.25F), alpha * 0.85F * this.appear)));
        }

        if (!this.sprites.isEmpty()) {
            this.renderer.render(this.sprites, BLOOM_BRIGHTNESS, true, true);
        }
    }

    /** Доля высоты → яркость: плавное появление внизу, растворение вверху. */
    private static double envelope(double normalized) {
        if (normalized < FADE_IN) return normalized / FADE_IN;
        if (normalized > 1.0D - FADE_OUT) return (1.0D - normalized) / FADE_OUT;
        return 1.0D;
    }

    /** Вспышка при уроне: кораловый цвет и +20% размера, спадает за ~0.42 с. */
    private void updateStress(LivingEntity target, double dt, boolean enabled) {
        if (!enabled) {
            this.stress = 0.0F;
            this.lastHurtTime = Math.max(0, target.hurtTime);
            return;
        }
        int hurt = Math.max(0, target.hurtTime);
        if (hurt > this.lastHurtTime) {
            this.stress = 1.0F;
        }
        this.lastHurtTime = hurt;
        this.stress = Mth.clamp((float) (this.stress - dt / STRESS_SECONDS), 0.0F, 1.0F);
    }

    /** Каналы ярче на множитель, с ограничением по 255 — ядро искры должно белить. */
    private static int brighten(int color, float scale) {
        return ColorUtil.rgba(
                Math.min(255, Math.round(ColorUtil.red(color) * scale)),
                Math.min(255, Math.round(ColorUtil.green(color) * scale)),
                Math.min(255, Math.round(ColorUtil.blue(color) * scale)),
                255);
    }

    private double tickSeconds() {
        long now = System.nanoTime();
        double dt = this.lastFrameNanos == 0L
                ? 1.0 / 60.0
                : Math.min((now - this.lastFrameNanos) / 1.0E9, MAX_DELTA_SECONDS);
        this.lastFrameNanos = now;
        return dt;
    }

    private static float approach(float value, float goal, float step) {
        if (value < goal) return Math.min(goal, value + step);
        return Math.max(goal, value - step);
    }

    public void reset() {
        this.time = 0.0D;
        this.appear = 0.0F;
        this.stress = 0.0F;
        this.lastFrameNanos = 0L;
        this.lastHurtTime = 0;
        this.lastTarget = null;
        this.sprites.clear();
    }

    /**
     * Смешивание сида с индексом и солью (splitmix64): одно и то же число всегда
     * даёт один и тот же сдвиг, поэтому таблица искр не меняется между сборками.
     */
    private static double random(int index, int salt) {
        long value = SEED ^ ((long) index * 0x9E3779B97F4A7C15L) ^ ((long) salt * 0xBF58476D1CE4E5B9L);
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }
}
