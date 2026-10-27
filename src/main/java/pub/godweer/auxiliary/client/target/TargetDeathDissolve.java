package pub.godweer.auxiliary.client.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer.Sprite;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Create by daun kvass
 */
final class TargetDeathDissolve {
    private static final int MAX_FRAGMENTS = 240;

    private final List<Fragment> fragments = new ArrayList<>();
    private final List<Sprite> sprites = new ArrayList<>();
    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final Random random = new Random();

    void burst(List<Sprite> shape, LivingEntity target, int fallbackColor, long now) {
        burst(shape, target, fallbackColor, now, MAX_FRAGMENTS,
                0L, 0.018F, 0.18F, 0.42F, 1.0F,
                1200L, 1200, 1.0F);
    }

    private void burst(List<Sprite> shape, LivingEntity target, int fallbackColor, long now, int maxFragments, long holdMillis, float minimumSize, float sizeFrom, float sizeTo, float outwardMultiplier, long lifeBase, int lifeVariance, float alphaScale) {
        this.fragments.clear();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        if (shape.isEmpty()) {
            spawnFallback(target, fallbackColor, center, now);
            return;
        }

        int stride = Math.max(1, (int) Math.ceil(shape.size() / (double) maxFragments));
        int sampledCount = (shape.size() + stride - 1) / stride;
        int copies = Math.max(1, Math.min(3, maxFragments / sampledCount));
        for (int sourceIndex = 0;
             sourceIndex < shape.size() && this.fragments.size() < maxFragments;
             sourceIndex += stride) {
            Sprite source = shape.get(sourceIndex);
            for (int copy = 0; copy < copies && this.fragments.size() < maxFragments; copy++) {
                spawnFragment(
                        source.position(),
                        center,
                        Math.max(minimumSize, source.halfSize() * randomRange(sizeFrom, sizeTo)),
                        source.color(),
                        now,
                        holdMillis,
                        outwardMultiplier,
                        lifeBase,
                        lifeVariance,
                        alphaScale
                );
            }
        }
    }

    void render(float delta, long now) {
        this.sprites.clear();
        Iterator<Fragment> iterator = this.fragments.iterator();
        while (iterator.hasNext()) {
            Fragment fragment = iterator.next();
            long age = now - fragment.bornAt;
            if (age >= fragment.lifeMillis) {
                iterator.remove();
                continue;
            }

            fragment.phase += fragment.twinkleRate * delta;
            if (age >= fragment.holdMillis) {
                float drag = (float) Math.exp(-1.65F * delta);
                fragment.vx *= drag;
                fragment.vz *= drag;
                fragment.vy += 0.22F * delta;
                fragment.vx += Math.cos(fragment.phase) * 0.045F * delta;
                fragment.vz += Math.sin(fragment.phase * 0.87F) * 0.045F * delta;
                fragment.x += fragment.vx * delta;
                fragment.y += fragment.vy * delta;
                fragment.z += fragment.vz * delta;
            }

            long movingAge = Math.max(0L, age - fragment.holdMillis);
            long movingLife = Math.max(1L, fragment.lifeMillis - fragment.holdMillis);
            float progress = Mth.clamp(movingAge / (float) movingLife, 0.0F, 1.0F);
            float fadeIn = Mth.clamp(age / 90.0F, 0.0F, 1.0F);
            float fadeOut = (1.0F - progress) * (1.0F - progress);
            float twinkle = 0.72F + 0.28F * (float) Math.sin(fragment.phase);
            float alpha = fadeIn * fadeOut * twinkle * fragment.alphaScale;
            float size = fragment.halfSize * (1.0F + progress * 0.65F);

            this.sprites.add(new Sprite(
                    new Vec3(fragment.x, fragment.y, fragment.z),
                    size,
                    multiplyAlpha(fragment.color, alpha)
            ));
        }
        this.renderer.render(this.sprites, 1.65F, true);
    }

    void clear() {
        this.fragments.clear();
        this.sprites.clear();
    }

    private void spawnFallback(LivingEntity target, int color, Vec3 center, long now) {
        AABB box = target.getBoundingBox();
        for (int i = 0; i < 90; i++) {
            Vec3 position = new Vec3(
                    Mth.lerp(this.random.nextDouble(), box.minX, box.maxX),
                    Mth.lerp(this.random.nextDouble(), box.minY, box.maxY),
                    Mth.lerp(this.random.nextDouble(), box.minZ, box.maxZ)
            );
            spawnFragment(position, center, randomRange(0.055F, 0.14F), color, now,
                    0L, 1.0F, 1200L, 1200, 1.0F);
        }
    }

    private void spawnFragment(Vec3 position,
                               Vec3 center,
                               float halfSize,
                               int color,
                               long now,
                               long holdMillis,
                               float outwardMultiplier,
                               long lifeBase,
                               int lifeVariance,
                               float alphaScale) {
        Vec3 radial = position.subtract(center);
        double horizontalLength = Math.sqrt(radial.x * radial.x + radial.z * radial.z);
        double radialX = horizontalLength > 1.0E-5D ? radial.x / horizontalLength : 0.0D;
        double radialZ = horizontalLength > 1.0E-5D ? radial.z / horizontalLength : 0.0D;
        float outward = randomRange(0.35F, 1.15F) * outwardMultiplier;

        Fragment fragment = new Fragment();
        fragment.x = position.x + randomRange(-0.025F, 0.025F);
        fragment.y = position.y + randomRange(-0.025F, 0.025F);
        fragment.z = position.z + randomRange(-0.025F, 0.025F);
        fragment.vx = radialX * outward + randomRange(-0.24F, 0.24F);
        fragment.vy = randomRange(0.28F, 1.05F) + Math.max(0.0D, radial.y) * 0.04D;
        fragment.vz = radialZ * outward + randomRange(-0.24F, 0.24F);
        fragment.halfSize = Math.min(0.32F, halfSize);
        fragment.color = (0xFF << 24) | (color & 0x00FFFFFF);
        fragment.bornAt = now;
        fragment.holdMillis = holdMillis;
        fragment.lifeMillis = holdMillis + lifeBase + this.random.nextInt(Math.max(1, lifeVariance));
        fragment.alphaScale = alphaScale;
        fragment.phase = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        fragment.twinkleRate = randomRange(4.0F, 8.0F);
        this.fragments.add(fragment);
    }

    private float randomRange(float min, float max) {
        return min + (max - min) * this.random.nextFloat();
    }

    private static int multiplyAlpha(int color, float alphaFactor) {
        int a = Math.round(((color >> 24) & 0xFF) * alphaFactor);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static final class Fragment {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        float halfSize;
        int color;
        long bornAt;
        long holdMillis;
        long lifeMillis;
        float alphaScale;
        float phase;
        float twinkleRate;
    }
    public boolean hasActive() {
        return !this.fragments.isEmpty();
    }
}