package pub.godweer.auxiliary.client.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.client.Annstable;
import pub.godweer.auxiliary.math.anim.EaseAnimation;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer.Sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
public final class GhostTargetRenderer {
    private static final int STRANDS = 3;
    private static final int PARTICLES_PER_STRAND = 42;

    private final EaseAnimation appear = new EaseAnimation(450L, EaseAnimation.Easing.EASE_OUT_QUAD);
    private final List<Sprite> sprites = new ArrayList<>(STRANDS * PARTICLES_PER_STRAND + 20);
    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final TargetDeathDissolve deathDissolve = new TargetDeathDissolve();

    private LivingEntity lastTarget;
    private int dissolvedTargetId = Integer.MIN_VALUE;
    private boolean animationTarget;
    private long lastFrameNanos;
    private float clock;

    public void render(LivingEntity activeTarget, float tickDelta, int color, float sizeScale) {
        long nowNanos = System.nanoTime();
        long nowMillis = System.currentTimeMillis();
        float delta = this.lastFrameNanos == 0L
                ? 0.016F
                : Math.min(0.08F, (nowNanos - this.lastFrameNanos) / 1.0E9F);
        this.lastFrameNanos = nowNanos;

        boolean present = valid(activeTarget);
        if (present) {
            this.lastTarget = activeTarget;
            if (activeTarget.getId() == this.dissolvedTargetId) {
                this.dissolvedTargetId = Integer.MIN_VALUE;
            }
        }

        // Распад — только когда цель реально умерла. Потерянная цель (перестал наводиться)
        // не должна рассыпаться душами, которые улетают от моба.
        if (this.lastTarget != null && dead(this.lastTarget) && this.lastTarget.getId() != this.dissolvedTargetId) {
            this.deathDissolve.burst(this.sprites, this.lastTarget, color, nowMillis);
            this.dissolvedTargetId = this.lastTarget.getId();
        }

        updateAnimations(present);

        // Орбита живёт ровно пока цель активна: никаких дорисовок вокруг прошлого моба.
        if (!present) {
            this.lastTarget = null;
            this.sprites.clear();
            this.deathDissolve.render(delta, nowMillis);
            return;
        }

        float appearValue = this.appear.getValue();
        this.clock += delta * 2.6F;

        Vec3 targetPos = Render3DUtil.interpolatedPosition(activeTarget, tickDelta);

        float targetHeight = activeTarget.getBbHeight();
        float targetWidth = activeTarget.getBbWidth();
        // Радиус жёстко привязан к хитбоксу и не дышит вместе с анимацией появления.
        float orbitRadius = Math.max(0.18F, targetWidth * 0.52F + 0.08F + Math.max(0.0F, sizeScale - 1.1F) * 0.05F);
        int animatedColor = Annstable.blend(color, activeTarget, 1.0F);

        this.sprites.clear();

        for (int s = 0; s < STRANDS; s++) {
            double strandOffset = s * (Math.PI * 2.0D / STRANDS);

            for (int p = 0; p < PARTICLES_PER_STRAND; p++) {
                float progress = (float) p / (float) (PARTICLES_PER_STRAND - 1);
                double angle = this.clock + strandOffset - progress * 1.55D;
                double y = targetHeight * 0.5D
                        + Math.sin(angle * 1.75D + strandOffset) * targetHeight * 0.17D;
                Vec3 position = targetPos.add(
                        Math.cos(angle) * orbitRadius,
                        y,
                        Math.sin(angle) * orbitRadius
                );

                float tailFade = 1.0F - progress;
                float alpha = (float) Math.pow(tailFade, 1.25F) * appearValue * 0.82F;
                float sizeFactor = (0.105F + (float) Math.sin(tailFade * Math.PI * 0.5F) * 0.07F)
                        * (0.6F + 0.4F * appearValue) * sizeScale;

                if (alpha <= 0.005F || sizeFactor <= 0.001F) continue;

                this.sprites.add(new Sprite(
                        position,
                        sizeFactor,
                        multiplyAlpha(animatedColor, alpha)
                ));
            }
        }

        if (!this.sprites.isEmpty()) {
            this.renderer.render(this.sprites, 1.35F, true);
        }

        this.deathDissolve.render(delta, nowMillis);
    }

    public void reset() {
        this.lastTarget = null;
        this.animationTarget = false;
        this.lastFrameNanos = 0L;
        this.clock = 0.0F;
        this.dissolvedTargetId = Integer.MIN_VALUE;
        this.sprites.clear();
        this.deathDissolve.clear();
        this.appear.animate(0.0F, 0.0F, 0L, EaseAnimation.Easing.EASE_OUT_QUAD);
    }

    private void updateAnimations(boolean present) {
        if (present == this.animationTarget) {
            return;
        }
        this.animationTarget = present;
        float target = present ? 1.0F : 0.0F;
        this.appear.animate(this.appear.getValue(), target, present ? 450L : 350L, EaseAnimation.Easing.EASE_OUT_QUAD);
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private static boolean dead(LivingEntity entity) {
        return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
    }

    private static int multiplyAlpha(int color, float alphaFactor) {
        int a = Math.round(((color >> 24) & 0xFF) * alphaFactor);
        return (a << 24) | (color & 0x00FFFFFF);
    }
    public boolean hasActive() {
        return this.appear.getValue() > 0.01F || this.deathDissolve.hasActive();
    }
}