package pub.godweer.util.hitfx;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * HitEffect: удар молнии с неба в цель при атаке — 1:1 та же молния,
 * что и TargetEsp Lightning. Тот же пайплайн (lines + LIGHTNING blend),
 * те же ширины (halo 0.052 / core 0.026 / core2 0.023), та же геометрия
 * subdivision depth 4 с ветвлениями на глубине &lt;2, тот же hash-noise
 * и bloom-спрайты на узлах. Единственное отличие — канал вертикальный
 * (с неба в макушку цели) и живёт один короткий strike.
 */
public final class HitStrikeRenderer {

    /** 10 lightning forms / animations / strike variations. */
    public static final int FORM_COUNT = 10;

    /** Strike with its own form — each hit gets its own animation. */
    public void strike(LivingEntity target, int form) {
        if (target == null || strikes.size() >= MAX_STRIKES) return;
        for (Strike check : strikes) {
            if (check.target == target) {
                check.startNanos = System.nanoTime();
                check.form = Math.floorMod(form, FORM_COUNT);
                return;
            }
        }
        Strike s = new Strike(target, System.nanoTime());
        s.form = Math.floorMod(form, FORM_COUNT);
        strikes.add(s);
    }

    // ---- geometry — 1:1 с LightningEspRenderer ----
    private static final int SUBDIVISION_DEPTH = 4;
    private static final int POINTS_PER_BOLT = 6;
    private static final int SEGMENTS_PER_BOLT = POINTS_PER_BOLT - 1;
    private static final int STROKES_PER_SEGMENT = 3;
    // Худший случай одной полосы для SUBDIVISION_DEPTH=4 с включённым ветвлением:
    // 2^4 листьев + боковые отводы на level<2, depth>=2 = 64, а не 52.
    private static final int MAX_LEAF_SEGMENTS = 64;
    private static final float HALO_WIDTH = 0.052F;
    private static final float CORE_WIDTH = 0.026F;
    private static final float CORE2_WIDTH = 0.023F;
    private static final float HALO_ALPHA = 0.40F;
    private static final float CORE_ALPHA = 1.00F;
    private static final float BLOOM_BRIGHTNESS = 1.35F;
    private static final float JITTER_CELL_RATE = 6.0F;

    // ---- timing (seconds) ----
    private static final float BOLT_SECONDS = 0.42F;
    private static final float STRIKE_SECONDS = 0.75F;

    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/hit_strike"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final ParticlesWorldRenderer particles = new ParticlesWorldRenderer();
    private final List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();

    private final List<Strike> strikes = new ArrayList<>();

    private static final int MAX_STRIKES = 4;

    private int triangleCount;
    private BufferBuilder meshBuilder;
    private GpuBuffer vertexBuffer;
    private GpuBuffer transformBuffer;

    /** Ставит удар молнией по цели; вызывать из события атаки. */
    public void strike(LivingEntity target) {
        strike(target, java.util.concurrent.ThreadLocalRandom.current().nextInt(FORM_COUNT));
    }

    public void render(int accent, float beamHeight, float widthScale, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) return;

        long now = System.nanoTime();
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            if (strike.valid() && strike.age(now) < STRIKE_SECONDS) continue;
            it.remove();
        }
        if (strikes.isEmpty()) return;

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Matrix4f levelProjection = Render3DUtil.levelProjectionCopy();
        if (levelProjection == null) return;

        widthScale = Math.max(0.2F, widthScale);

        beginMesh(strikes.size());
        triangleCount = 0;
        sprites.clear();
        Vec3 camPos = camera.position();

        for (Strike strike : strikes) {
            LivingEntity draw = strike.target;
            if (!strike.valid()) continue;
            float age = strike.age(now);
            if (age > STRIKE_SECONDS) continue;
            // общий фейд хвоста
            float globalFade = 1.0F - Mth.clamp((age - (STRIKE_SECONDS - 0.22F)) / 0.22F, 0.0F, 1.0F);

            Vec3 base = Render3DUtil.interpolatedPosition(draw, tickDelta);
            float h = Math.max(0.6F, draw.getBbHeight());
            int seed = strike.seed;
            int strikeForm = strike.form;

            float boltT = age * formTimeScale(strikeForm);
            boolean boltAlive = boltT <= BOLT_SECONDS * formLifeScale(strikeForm);
            if (!boltAlive) continue;

            int cell = (int) (boltT * JITTER_CELL_RATE * formSpeed(strikeForm));
            float cellFrac = boltT * JITTER_CELL_RATE * formSpeed(strikeForm) - cell;

            float rise = Mth.clamp(boltT / formRise(strikeForm), 0.0F, 1.0F);
            float fall = 1.0F - Mth.clamp((boltT - formFallStart(strikeForm)) / formFallSpan(strikeForm), 0.0F, 1.0F);
            float flicker = formFlicker(strikeForm, seed, cell);
            float intensity = rise * fall * flicker * globalFade;
            if (intensity <= 0.01F) continue;

            // 10 strike variations: channel shape, spread, twist, width, bloom
            float driftA = noise(seed, 51, cell) * (float) Math.PI + formPhase(strikeForm, boltT);
            float spread = (2.0F + 1.5F * Math.abs(noise(seed, 61, cell))) * formSpread(strikeForm);

            float angle = driftA;
            float px = (float) (Math.cos(angle) * spread);
            float py = beamHeight * formHeight(strikeForm);
            float pz = (float) (Math.sin(angle) * spread);
            int points = formPoints(strikeForm);

            for (int point = 1; point < points + 1; point++) {
                float t = (float) point / points;
                float x, y, z;
                if (point == points) {
                    // прямое попадание: канал всегда сходится точно в центр цели
                    x = 0.0F;
                    y = h;
                    z = 0.0F;
                } else {
                    angle += noise(seed, 40 + point, cell) * formAngleStep(strikeForm)
                            + formTwist(strikeForm) * t;
                    // воронка: разброс гаснет к цели, молния бьёт прямо в неё
                    float funnel = 1.0F - t;
                    float r = spread * funnel * (1.0F - 0.5F * formTaper(strikeForm) * t)
                            + formWave(strikeForm, t, boltT, seed) * funnel;
                    x = (float) (Math.cos(angle) * r);
                    z = (float) (Math.sin(angle) * r);
                    y = beamHeight * formHeight(strikeForm) + (h - beamHeight * formHeight(strikeForm)) * t;
                }

                float wMul = widthScale * formWidth(strikeForm);
                // 1:1 те же 3 слоя что и в LightningEspRenderer#stroke
                stroke(base, camPos, px, py, pz, x, y, z,
                        HALO_WIDTH * wMul, HALO_ALPHA, seed + strikeForm * 131, cell, cellFrac, intensity, accent);
                stroke(base, camPos, px, py, pz, x, y, z,
                        CORE_WIDTH * wMul, CORE_ALPHA, seed + strikeForm * 131, cell, cellFrac, intensity, accent);
                stroke(base, camPos, px, py, pz, x, y, z,
                        CORE2_WIDTH * wMul, CORE_ALPHA, seed + strikeForm * 131, cell, cellFrac, intensity, accent);

                // extra fork channel for forms 1,4,6 (multi-strike look)
                if ((strikeForm == 1 || strikeForm == 4 || strikeForm == 6) && point > 1) {
                    float fa = angle + 0.9F;
                    float fx = x + (float) Math.cos(fa) * spread * 0.35F;
                    float fz = z + (float) Math.sin(fa) * spread * 0.35F;
                    stroke(base, camPos, x, y, z, fx, y - 0.8F, fz,
                            CORE_WIDTH * wMul * 0.7F, CORE_ALPHA * 0.8F,
                            seed * 5 + 31, cell, cellFrac, intensity, accent);
                }

                // bloom на каждом втором узле — как в LightningEsp
                if ((point & 1) == 0) {
                    sprites.add(new ParticlesWorldRenderer.Sprite(
                            new Vec3(base.x + x, base.y + y, base.z + z),
                            0.09F * wMul * intensity * formBloom(strikeForm),
                            ColorUtil.withAlpha(accent, Math.round(ColorUtil.alpha(accent) * 0.35F * intensity))));
                }

                px = x; py = y; pz = z;
            }
        }

        if (triangleCount > 0) submit(camera, levelProjection);
        if (!sprites.isEmpty()) particles.render(sprites, BLOOM_BRIGHTNESS, true, true);
    }

    private void stroke(Vec3 base, Vec3 cam,
                        float x1, float y1, float z1, float x2, float y2, float z2,
                        float width, float alphaMultiplier, int seed, int cell, float cellFrac,
                        float intensity, int color) {
        float lineWidth = width * intensity;
        int strokeColor = ColorUtil.multiplyAlpha(color, Math.min(1.0F, intensity * alphaMultiplier));
        if (lineWidth <= 0.0F || ColorUtil.alpha(strokeColor) <= 0) return;
        subdivide(base, cam, x1, y1, z1, x2, y2, z2,
                SUBDIVISION_DEPTH, lineWidth, strokeColor, strokeColor, seed, 0, cell, cellFrac);
    }

    private void subdivide(Vec3 base, Vec3 cam,
                           float x1, float y1, float z1, float x2, float y2, float z2,
                           int depth, float lineWidth, int from, int to,
                           int seed, int level, int cell, float cellFrac) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (depth <= 0 || length < 1.0E-4F) {
            emitRibbon(base, cam, x1, y1, z1, x2, y2, z2, lineWidth, from, to);
            return;
        }
        float dirX = dx / length;
        float dirY = dy / length;
        float dirZ = dz / length;
        boolean vertical = Math.abs(dirY) >= 0.9F;
        float u = vertical ? 1.0F : 0.0F;
        float v = vertical ? 0.0F : 1.0F;
        float p1x = -dirZ * v;
        float p1y = dirZ * u;
        float p1z = dirX * v - dirY * u;
        float p1l = (float) Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        if (p1l < 1.0E-5F) p1l = 1.0F;
        p1x /= p1l; p1y /= p1l; p1z /= p1l;
        float p2x = dirY * p1z - dirZ * p1y;
        float p2y = dirZ * p1x - dirX * p1z;
        float p2z = dirX * p1y - dirY * p1x;
        float h1 = smoothNoise(seed, 0, cell, cellFrac);
        float h2 = smoothNoise(seed, 1, cell, cellFrac);
        float midScale = length * 0.3F;
        float mx = (x1 + x2) * 0.5F + (p1x * h1 + p2x * h2) * midScale;
        float my = (y1 + y2) * 0.5F + (p1y * h1 + p2y * h2) * midScale;
        float mz = (z1 + z2) * 0.5F + (p1z * h1 + p2z * h2) * midScale;
        int midColor = ColorUtil.lerp(from, to, 0.5F);
        subdivide(base, cam, x1, y1, z1, mx, my, mz, depth - 1, lineWidth, from, midColor, seed * 2 + 7, level, cell, cellFrac);
        subdivide(base, cam, mx, my, mz, x2, y2, z2, depth - 1, lineWidth, midColor, to, seed * 2 + 9, level, cell, cellFrac);
        if (level < 2 && depth >= 2 && smoothNoise(seed, 2, cell, cellFrac) > 0.45F) {
            float b1 = smoothNoise(seed, 3, cell, cellFrac);
            float b2 = smoothNoise(seed, 4, cell, cellFrac);
            float bdx = dirX * 0.4F + p1x * b1 + p2x * b2;
            float bdy = dirY * 0.4F + p1y * b1 + p2y * b2;
            float bdz = dirZ * 0.4F + p1z * b1 + p2z * b2;
            float bl = (float) Math.sqrt(bdx * bdx + bdy * bdy + bdz * bdz);
            if (bl < 1.0E-5F) bl = 1.0F;
            float reach = length * 0.75F;
            subdivide(base, cam, mx, my, mz,
                    mx + bdx / bl * reach, my + bdy / bl * reach, mz + bdz / bl * reach,
                    Math.min(depth - 1, 3), lineWidth * 0.55F,
                    ColorUtil.multiplyAlpha(from, 0.7F), ColorUtil.withAlpha(from, 0),
                    seed * 3 + 13, level + 1, cell, cellFrac);
        }
    }

    private void emitRibbon(Vec3 base, Vec3 cam,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float width, int from, int to) {
        if (width <= 0.0F || (ColorUtil.alpha(from) <= 0 && ColorUtil.alpha(to) <= 0)) return;
        double w1x = base.x + x1, w1y = base.y + y1, w1z = base.z + z1;
        double w2x = base.x + x2, w2y = base.y + y2, w2z = base.z + z2;
        double sdx = w2x - w1x, sdy = w2y - w1y, sdz = w2z - w1z;
        double midX = (w1x + w2x) * 0.5D, midY = (w1y + w2y) * 0.5D, midZ = (w1z + w2z) * 0.5D;
        double vx = midX - cam.x, vy = midY - cam.y, vz = midZ - cam.z;
        double sx = sdy * vz - sdz * vy, sy = sdz * vx - sdx * vz, sz = sdx * vy - sdy * vx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-8D || !Double.isFinite(sl)) return;
        sx /= sl; sy /= sl; sz /= sl;
        float half = width * 0.5F;
        float ox = (float) (sx * half), oy = (float) (sy * half), oz = (float) (sz * half);
        float ax = (float) (w1x - cam.x), ay = (float) (w1y - cam.y), az = (float) (w1z - cam.z);
        float bx = (float) (w2x - cam.x), by = (float) (w2y - cam.y), bz = (float) (w2z - cam.z);
        float c1x = ax - ox, c1y = ay - oy, c1z = az - oz;
        float c2x = ax + ox, c2y = ay + oy, c2z = az + oz;
        float c3x = bx + ox, c3y = by + oy, c3z = bz + oz;
        float c4x = bx - ox, c4y = by - oy, c4z = bz - oz;
        BufferBuilder builder = meshBuilder;
        builder.addVertex(c1x, c1y, c1z).setColor(from);
        builder.addVertex(c2x, c2y, c2z).setColor(from);
        builder.addVertex(c3x, c3y, c3z).setColor(to);
        builder.addVertex(c1x, c1y, c1z).setColor(from);
        builder.addVertex(c3x, c3y, c3z).setColor(to);
        builder.addVertex(c4x, c4y, c4z).setColor(to);
        triangleCount += 2;
    }

    private static float noise(int a, int b, int c) {
        int hash = a * 374761393 + b * 668265263 + c * 1274126177;
        hash = (hash ^ hash >>> 13) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 65535) / 32768.0F - 1.0F;
    }

    // ---- 10 lightning forms / animations / strike variations ----
    // 0 Classic, 1 Twin Fork, 2 Spiral Coil, 3 Needle, 4 Trident,
    // 5 Serpent Wave, 6 Cross Burst, 7 Hammer Drop, 8 Widow Web, 9 Nova Crown
    private static int formPoints(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 2 -> 7;
            case 3 -> 4;
            case 5 -> 8;
            case 8 -> 7;
            default -> POINTS_PER_BOLT;
        };
    }

    private static float formSpread(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 1 -> 1.35F;
            case 2 -> 1.15F;
            case 3 -> 0.35F;
            case 4 -> 1.25F;
            case 5 -> 0.9F;
            case 6 -> 1.5F;
            case 7 -> 0.45F;
            case 8 -> 1.1F;
            case 9 -> 1.6F;
            default -> 1.0F;
        };
    }

    private static float formWidth(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 1 -> 1.0F;
            case 2 -> 0.9F;
            case 3 -> 1.5F;
            case 4 -> 1.1F;
            case 5 -> 0.85F;
            case 6 -> 1.2F;
            case 7 -> 1.7F;
            case 8 -> 0.75F;
            case 9 -> 1.15F;
            default -> 1.0F;
        };
    }

    private static float formHeight(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 5 -> 0.8F;
            case 7 -> 1.25F;
            case 9 -> 0.7F;
            default -> 1.0F;
        };
    }

    private static float formAngleStep(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 2 -> 2.4F;
            case 3 -> 0.35F;
            case 5 -> 1.1F;
            case 7 -> 0.5F;
            case 8 -> 1.8F;
            default -> 1.35F;
        };
    }

    private static float formTwist(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 2 -> 3.2F;
            case 5 -> 1.4F;
            case 6 -> 0.8F;
            case 8 -> 1.0F;
            default -> 0.0F;
        };
    }

    private static float formTaper(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 3 -> 0.25F;
            case 5 -> 0.6F;
            case 7 -> 0.95F;
            case 9 -> 0.5F;
            default -> 0.85F;
        };
    }

    private static float formWave(int f, float t, float boltT, int seed) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 5 -> (float) Math.sin(t * 9.0F + boltT * 22.0F + seed * 0.01F) * 0.55F;
            case 8 -> (float) Math.sin(t * 14.0F + seed) * 0.3F * t;
            case 2 -> (float) Math.cos(t * 6.2831F * 2.0F + boltT * 10.0F) * 0.35F;
            default -> 0.0F;
        };
    }

    private static float formPhase(int f, float boltT) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 2 -> boltT * 6.0F;
            case 6 -> boltT * 3.0F;
            case 8 -> boltT * 4.0F;
            default -> 0.0F;
        };
    }

    private static float formTipX(int f, float driftA, float t) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 4 -> (float) Math.cos(driftA) * 1.2F;
            case 6 -> (float) Math.cos(driftA + 1.5F) * 0.8F;
            default -> 0.0F;
        };
    }

    private static float formTipZ(int f, float driftA, float t) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 4 -> (float) Math.sin(driftA) * 1.2F;
            case 6 -> (float) Math.sin(driftA + 1.5F) * 0.8F;
            default -> 0.0F;
        };
    }

    private static float formSpeed(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 2 -> 1.8F;
            case 5 -> 1.5F;
            case 7 -> 0.7F;
            case 8 -> 1.6F;
            default -> 1.0F;
        };
    }

    private static float formTimeScale(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 7 -> 0.8F;
            case 9 -> 1.3F;
            default -> 1.0F;
        };
    }

    private static float formLifeScale(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 3 -> 0.8F;
            case 7 -> 1.3F;
            case 9 -> 1.2F;
            default -> 1.0F;
        };
    }

    private static float formRise(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 3 -> 0.03F;
            case 7 -> 0.1F;
            case 9 -> 0.04F;
            default -> 0.06F;
        };
    }

    private static float formFallStart(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 3 -> 0.2F;
            case 7 -> 0.4F;
            case 9 -> 0.35F;
            default -> 0.28F;
        };
    }

    private static float formFallSpan(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 7 -> 0.2F;
            case 9 -> 0.18F;
            default -> 0.14F;
        };
    }

    private static float formFlicker(int f, int seed, int cell) {
        float base = 0.7F + 0.3F * Math.abs(noise(seed, 700, cell * 5));
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 3 -> 0.9F + 0.1F * Math.abs(noise(seed, 701, cell * 5));
            case 6 -> 0.55F + 0.45F * Math.abs(noise(seed, 702, cell * 7));
            case 9 -> 0.8F + 0.2F * Math.abs(noise(seed, 703, cell * 3));
            default -> base;
        };
    }

    private static float formBloom(int f) {
        return switch (Math.floorMod(f, FORM_COUNT)) {
            case 3 -> 0.7F;
            case 7 -> 1.5F;
            case 9 -> 1.6F;
            case 6 -> 1.3F;
            default -> 1.0F;
        };
    }

    private static float smoothNoise(int a, int b, int cell, float t) {
        float from = noise(a, b, cell * 4);
        float to = noise(a, b, (cell + 1) * 4);
        float smooth = t * t * (3.0F - 2.0F * t);
        return from + (to - from) * smooth;
    }

    private void beginMesh(int strikeCount) {
        int strokesPerStrike = SEGMENTS_PER_BOLT * STROKES_PER_SEGMENT;
        int leafSegments = strikeCount * strokesPerStrike * MAX_LEAF_SEGMENTS;
        int bytes = leafSegments * 6 * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        // Растущий буфер, а не exactlySized: число полос зависит от формы (до 8 точек)
        // и от ветвлений рекурсии, так что фиксированная смета всегда могла быть
        // превышена в середине кадра — render падал с "Maximum capacity exceeded".
        meshBuilder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(bytes, 64 * 1024)),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        );
    }

    private void submit(Camera camera, Matrix4f projection) {
        MeshData mesh = meshBuilder.buildOrThrow();
        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = mesh.vertexBuffer();
            int byteCount = vertexData.remaining();
            int vertexCount = mesh.drawState().vertexCount();
            if (vertexCount <= 0) return;
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (target == null || target.getColorTextureView() == null) return;
            ensureVertexCapacity(byteCount);
            device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, byteCount), vertexData);
            Matrix4f mvp = new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));
            ensureTransformBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(mvp).get();
                device.createCommandEncoder().writeToBuffer(transformBuffer.slice(), data);
            }
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer Hit Strike", target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Projection", transformBuffer);
                pass.setVertexBuffer(0, vertexBuffer.slice(0, byteCount));
                pass.draw(vertexCount, 1, 0, 0);
            }
        } finally {
            meshBuilder = null;
            if (mesh != null) mesh.close();
        }
    }

    private void ensureTransformBuffer() {
        if (transformBuffer != null) return;
        transformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Hit Strike UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, TRANSFORM_SIZE);
    }

    private void ensureVertexCapacity(int byteSize) {
        if (vertexBuffer != null && vertexBuffer.size() >= byteSize) return;
        if (vertexBuffer != null) vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
        vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Hit Strike Vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
    }

    public void release() {
        strikes.clear();
        sprites.clear();
        triangleCount = 0;
        meshBuilder = null;
        if (vertexBuffer != null) { vertexBuffer.close(); vertexBuffer = null; }
        if (transformBuffer != null) { transformBuffer.close(); transformBuffer = null; }
    }

    private static final class Strike {
        final LivingEntity target;
        final int seed;
        int form;
        long startNanos;
        Strike(LivingEntity target, long startNanos) {
            this.target = target;
            this.seed = java.util.concurrent.ThreadLocalRandom.current().nextInt() | 1;
            this.startNanos = startNanos;
        }
        float age(long now) { return Math.max(0.0F, (now - startNanos) / 1.0E9F); }
        boolean valid() {
            return target != null && target.isAlive() && !target.isRemoved()
                    && target.level() == Minecraft.getInstance().level;
        }
    }
}
