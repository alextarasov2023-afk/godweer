package pub.godweer.util.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.util.misc.Annstable;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.math.anim.EaseAnimation;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer.Sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Души: три витые дорожки, которые закручены вокруг самой цели. Центр дорожки
 * берётся напрямую из интерполированной позиции моба — без пружинного «якоря с
 * упреждением», который в прошлой версии догонял бегущую цель и потому всегда был
 * сзади. Отставать может только хвост, и он нарочно короткий: шаг выборки истории
 * ~9 мс вместо ~18–34 мс.
 *
 * Дорожки привязаны к высоте хитбокса (22% / 48% / 74%), радиус дышит, а фазы полос
 * разные — так витки не сливаются в один «колхозный» обруч. Цвет считается в HSV и
 * бежит по хвосту вместе с яркостью: статичный цвет плюс аддитивный блендинг
 * выбивали середину в белый, и эффект выглядел бесцветным.
 *
 * Create by daun kvass
 */
public final class GhostTargetRenderer {
    private static final int LANES = 3;
    // 256 снимков: при шаге выборки ~9–20 мс это 2,5–5 секунд истории — с запасом
    // на самый длинный шлейф из настройки «Души: Длина».
    private static final int HISTORY = 256;
    private static final double LANE_ANGLE = Math.PI * 2.0 / LANES;
    private static final double RESET_DISTANCE = 14.0D;

    // Один оборот за ~2.2 секунды. Со стартовыми 0.72 рад/с дорожки выглядели
    // неподвижными: глаз считывает «вращение» только выше примерно 2 рад/с.
    private static final double ORBIT_SPEED = 2.9D;
    // Насколько виток уходит назад по фазе от головы шлейфа к его концу.
    private static final double SPIRAL_TWIST = 2.35D;
    // Долго ли после смены цели держим «полёт» (растянутый хвост-перелёт).
    private static final long SWITCH_FLIGHT_MILLIS = 260L;

    private final EaseAnimation appear = new EaseAnimation(450L, EaseAnimation.Easing.EASE_OUT_QUAD);
    private final List<Sprite> sprites = new ArrayList<>(LANES * 96 + 8);
    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final TargetDeathDissolve deathDissolve = new TargetDeathDissolve();

    private final Vec3[] history = new Vec3[HISTORY];
    private final long[] historyTimes = new long[HISTORY];
    private int cursor = -1;
    private int historyCount;

    private Vec3 center;
    private LivingEntity tracked;
    private long switchAt;
    private double time;
    private float flight;
    private float hurt;

    private LivingEntity lastTarget;
    private int dissolvedTargetId = Integer.MIN_VALUE;
    private boolean animationTarget;
    private long lastFrameNanos;

    public void render(LivingEntity activeTarget, float tickDelta, int color, float sizeScale) {
        render(activeTarget, tickDelta, color, sizeScale, 60);
    }

    public void render(LivingEntity activeTarget, float tickDelta, int color, float sizeScale, int strandLength) {
        long nowNanos = System.nanoTime();
        long now = System.currentTimeMillis();
        float delta = this.lastFrameNanos == 0L
                ? 0.016F
                : Math.min(0.08F, (nowNanos - this.lastFrameNanos) / 1.0E9F);
        this.lastFrameNanos = nowNanos;
        this.time += delta;

        boolean present = valid(activeTarget);
        if (present) {
            this.lastTarget = activeTarget;
            if (activeTarget.getId() == this.dissolvedTargetId) {
                this.dissolvedTargetId = Integer.MIN_VALUE;
            }
        }

        // Распад — только когда цель реально умерла. Потерянная цель (перестал
        // наводиться) не должна рассыпаться душами, которые улетают от моба.
        if (this.lastTarget != null && dead(this.lastTarget) && this.lastTarget.getId() != this.dissolvedTargetId) {
            this.deathDissolve.burst(this.sprites, this.lastTarget, color, now);
            this.dissolvedTargetId = this.lastTarget.getId();
        }

        updateAnimations(present);

        // Шлейф живёт ровно пока цель активна: никаких дорисовок вокруг прошлого моба.
        if (!present) {
            this.lastTarget = null;
            this.sprites.clear();
            this.deathDissolve.render(delta, now);
            return;
        }

        float appearValue = this.appear.getValue();
        int particles = Math.max(6, Math.min(96, strandLength));
        Vec3 position = updateMotion(activeTarget, tickDelta, delta, now);

        this.hurt = activeTarget.hurtTime > 0
                ? activeTarget.hurtTime / 10.0F
                : Math.max(0.0F, this.hurt - 0.6F * delta);
        float hurtScale = 1.0F + this.hurt * 0.16F;

        double height = activeTarget.getBbHeight();
        // Радиус по ширине хитбокса: на тонком мобе души обнимают его, на широком
        // не улетают за экран. Фиксированные 0.72 блока давали вокруг игрока
        // «обруч», а вокруг коровы — осыпание внутрь корпуса.
        double radiusBase = Mth.clamp(activeTarget.getBbWidth() * 0.62D, 0.38D, 1.10D);

        float[] hsv = ColorUtil.toHsv(Annstable.blend(color, activeTarget, 1.0F));
        // Бледный акцент темы (или белый) иначе схлопывается в бесцветную кашу:
        // насыщение держим снизу, оттенок при этом остаётся авторским.
        float saturation = Math.max(hsv[1], 0.55F);

        this.sprites.clear();

        for (int lane = 0; lane < LANES; lane++) {
            double laneAngle = lane * LANE_ANGLE;
            // Скорости полос разные, поэтому витки расходятся спиралью, а не
            // сливаются в один читаемый обруч.
            double speed = ORBIT_SPEED * (1.0D + lane * 0.16D);
            // Шаг задержки растёт с «полётом»: на бегу цель тянет более длинный след.
            double stepMillis = 9.0D + this.flight * 11.0D;

            for (int p = 0; p < particles; p++) {
                float trail = particles == 1 ? 0.0F : (float) p / (particles - 1);
                Vec3 base = sampleHistory(now - (long) (p * stepMillis));
                double phase = this.time * speed + laneAngle - trail * SPIRAL_TWIST;
                double breathe = 1.0D + 0.12D * Mth.sin((float) (this.time * 1.7D + lane * 1.6D));
                double radius = radiusBase * breathe * hurtScale
                        * (1.0D - trail * 0.22D) * (1.0D + this.flight * 0.10D);

                double x = base.x + Mth.sin((float) phase) * radius;
                double z = base.z + Mth.cos((float) phase) * radius;
                double y = base.y
                        + height * (0.22F + lane * 0.26F)
                        + Mth.sin((float) (phase * 0.62D + trail * 2.1D)) * (0.11D + this.flight * 0.06D);

                float pulse = 0.94F + 0.06F * Mth.sin((float) (this.time * 4.2D + laneAngle - trail * 2.0F));
                float size = (0.24F - trail * 0.15F) * pulse * sizeScale
                        * (1.0F + this.hurt * 0.20F + this.flight * (1.0F - trail) * 0.14F);
                float alpha = (0.06F + 0.72F * (float) Math.pow(1.0D - trail, 1.5D)) * appearValue;
                if (alpha <= 0.006F || size <= 0.002F) continue;

                int tint = soulColor(hsv[0], saturation, trail, laneAngle, lane);
                this.sprites.add(new Sprite(new Vec3(x, y, z), size, ColorUtil.withAlpha(tint, alpha)));
            }
        }

        // Единственная яркая точка — «сердце» на самой цели. Раньше на каждой голове
        // полосы лежало по запасному кваду в том же месте, и аддитивный блендинг
        // складывал их в белое пятно вместо цветной души.
        this.sprites.add(new Sprite(position,
                0.30F * sizeScale * hurtScale * (1.0F + this.flight * 0.25F),
                ColorUtil.withAlpha(soulColor(hsv[0], saturation, 0.0F, 0.0D, 0), 0.45F * appearValue)));

        if (!this.sprites.isEmpty()) {
            this.renderer.render(this.sprites, 1.05F, true);
        }

        this.deathDissolve.render(delta, now);
    }

    /**
     * Позиция-центр — живая позиция цели, без догона. Возвращает её же; история
     * позиций пишется сюда, чтобы хвост оставался в мире, когда цель уже ушла.
     */
    private Vec3 updateMotion(LivingEntity target, float tickDelta, float delta, long now) {
        Vec3 position = Render3DUtil.interpolatedPosition(target, tickDelta);

        if (this.center == null || !finite(this.center)
                || position.distanceTo(this.center) > RESET_DISTANCE) {
            this.center = position;
            this.tracked = target;
            this.flight = 0.0F;
            resetHistory();
            remember(now);
            return this.center;
        }

        if (this.tracked != target) {
            // Смена цели: голова встаёт на новую цель в этом же кадре, а хвост
            // ещё кадр-два тянется от старой — это и есть «перелёт», но без
            // отставания самой светящейся точки.
            this.tracked = target;
            this.switchAt = now;
            this.flight = Math.max(this.flight, 0.6F);
        }

        double speed = target.getDeltaMovement().length();
        float wanted = Mth.clamp((float) (speed / 0.62D), 0.0F, 1.0F);
        if (now - this.switchAt < SWITCH_FLIGHT_MILLIS) {
            wanted = Math.max(wanted, 0.6F);
        }
        // Резко вверх, плавно вниз: шлейф растягивается в момент рывка и собирается
        // обратно уже без рывков яркости.
        float response = 1.0F - (float) Math.exp(-delta * (wanted > this.flight ? 6.0F : 2.2F));
        this.flight = Mth.clamp(this.flight + (wanted - this.flight) * response, 0.0F, 1.0F);

        this.center = position;
        remember(now);
        return position;
    }

    /**
     * Цвет души: оттенок бежит по хвосту и медленно дышит во времени, яркость —
     * быстрее. Тот же самый цвет у всех частиц выглядел «плоским», а вместе с
     * аддитивным блендингом — вообще выбитым в белый.
     */
    private int soulColor(float baseHue, float saturation, float trail, double laneAngle, int lane) {
        float hue = baseHue
                + 0.10F * Mth.sin((float) (this.time * 0.45D + laneAngle))
                + trail * 0.17F;
        float value = 0.80F + 0.20F * Mth.sin((float) (this.time * 2.1D - trail * 4.2D + lane * 1.1D));
        return ColorUtil.fromHsv(hue, saturation, Mth.clamp(value, 0.45F, 1.0F), 255);
    }

    private void remember(long now) {
        if (this.center == null) return;
        if (this.historyCount > 0 && this.historyTimes[this.cursor] == now) {
            this.history[this.cursor] = this.center;
            return;
        }
        this.cursor = (this.cursor + 1) % HISTORY;
        this.history[this.cursor] = this.center;
        this.historyTimes[this.cursor] = now;
        if (this.historyCount < HISTORY) {
            this.historyCount++;
        }
    }

    private void resetHistory() {
        this.cursor = -1;
        this.historyCount = 0;
    }

    /** Позиция центра на момент sampleTime: между снимками интерполяция, старше всех — самый старый. */
    private Vec3 sampleHistory(long sampleTime) {
        if (this.center == null) return Vec3.ZERO;
        if (this.historyCount == 0 || this.cursor < 0) return this.center;

        if (sampleTime >= this.historyTimes[this.cursor]) {
            return this.history[this.cursor];
        }
        for (int i = 0; i < this.historyCount - 1; i++) {
            int newer = Math.floorMod(this.cursor - i, HISTORY);
            int older = Math.floorMod(newer - 1, HISTORY);
            long newerTime = this.historyTimes[newer];
            long olderTime = this.historyTimes[older];
            if (sampleTime < olderTime) continue;
            double progress = newerTime == olderTime
                    ? 1.0D
                    : (sampleTime - olderTime) / (double) (newerTime - olderTime);
            return lerp(this.history[older], this.history[newer], progress);
        }
        return this.history[Math.floorMod(this.cursor - this.historyCount + 1, HISTORY)];
    }

    public void reset() {
        this.lastTarget = null;
        this.animationTarget = false;
        this.lastFrameNanos = 0L;
        this.dissolvedTargetId = Integer.MIN_VALUE;
        this.sprites.clear();
        this.deathDissolve.clear();
        this.appear.animate(0.0F, 0.0F, 0L, EaseAnimation.Easing.EASE_OUT_QUAD);
        this.center = null;
        this.tracked = null;
        this.flight = 0.0F;
        this.hurt = 0.0F;
        this.switchAt = 0L;
        resetHistory();
    }

    private void updateAnimations(boolean present) {
        if (present == this.animationTarget) {
            return;
        }
        this.animationTarget = present;
        float target = present ? 1.0F : 0.0F;
        this.appear.animate(this.appear.getValue(), target, present ? 450L : 350L, EaseAnimation.Easing.EASE_OUT_QUAD);
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, from.x, to.x), Mth.lerp(delta, from.y, to.y), Mth.lerp(delta, from.z, to.z));
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private static boolean dead(LivingEntity entity) {
        return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
    }

    public boolean hasActive() {
        return this.appear.getValue() > 0.01F || this.deathDissolve.hasActive();
    }
}
