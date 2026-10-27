package pub.godweer.auxiliary.client.target;

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
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.kitty.module.impl.render.TargetEsp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Target ESP "Lightning".
 * <p>
 * Three (configurable) arcs crawl around the target. Every arc is split into
 * segments, each segment is recursively subdivided with smooth hash noise and
 * occasionally throws a tapering side branch — the classic bolt look. Every leaf
 * segment is expanded into a camera-facing additive ribbon quad, drawn in three
 * layers (a wide soft halo plus two thin cores). Bright bloom sprites sit on the
 * arc nodes. The whole thing fades in with an eased progress tween and flushes
 * red when the target takes damage. One mesh, one draw call, drawn through walls.
 */
public final class LightningEspRenderer {

    // ---- geometry ----
    private static final int SUBDIVISION_DEPTH = 4;
    private static final int POINTS_PER_BOLT = 4;
    private static final int SEGMENTS_PER_BOLT = POINTS_PER_BOLT - 1;
    private static final int STROKES_PER_SEGMENT = 3;
    private static final float JITTER_CELL_RATE = 6.0F;

    // worst-case number of leaf segments a single stroke can subdivide into
    // (depth 4 = 16 leaves + branch forks at level<2, depth>=2 => 64)
    private static final int MAX_LEAF_SEGMENTS = 64;

    // ribbon widths in world units and their alpha multipliers
    private static final float HALO_WIDTH = 0.052F;
    private static final float CORE_WIDTH = 0.026F;
    private static final float CORE2_WIDTH = 0.023F;
    private static final float HALO_ALPHA = 0.40F;
    private static final float CORE_ALPHA = 1.00F;

    // ---- animation ----
    private static final float FADE_SECONDS = 0.35F;
    private static final float MAX_DELTA_SECONDS = 0.05F;

    // ---- look ----
    private static final int HURT_TINT = 0xFFFF4040;
    private static final float BLOOM_BRIGHTNESS = 1.35F;

    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/lightning_esp"))
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

    private float appear;
    private float time;
    private long lastFrameNanos;
    private LivingEntity lastTarget;

    private int triangleCount;
    private BufferBuilder meshBuilder;
    private GpuBuffer vertexBuffer;
    private GpuBuffer transformBuffer;

    public void render(TargetEsp module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) return;

        LivingEntity target = module.resolveTarget();
        boolean hasTarget = valid(target);
        if (hasTarget) {
            this.lastTarget = target;
        }

        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L
                ? (1.0F / 60.0F)
                : Math.min((now - this.lastFrameNanos) / 1.0E9F, MAX_DELTA_SECONDS);
        this.lastFrameNanos = now;

        updateAppear(hasTarget, dt);
        this.time += dt;

        float progress = eased(this.appear);
        LivingEntity draw = this.lastTarget;
        if (draw == null || !valid(draw) || progress <= 0.01F) {
            if (!hasTarget) {
                this.lastTarget = null;
            }
            return;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        if (Render3DUtil.levelProjectionCopy() == null) return;

        int color = module.getAccentColor();
        if (module.lightningHurt.getValue()) {
            float hurt = Math.max(0.0F, draw.hurtTime - tickDelta);
            float flash = (float) Math.sin(hurt * (float) (Math.PI / 10.0)) * 0.5F;
            color = ColorUtil.lerp(color, HURT_TINT, flash);
        }
        color = ColorUtil.multiplyAlpha(color, progress);

        int count = Mth.clamp(Math.round(module.lightningCount.getValue()), 1, 8);
        float widthScale = module.lightningWidth.getValue();

        Vec3 base = Render3DUtil.interpolatedPosition(draw, tickDelta);
        Vec3 camPos = camera.position();

        float radius = (float) draw.getBbWidth() * 0.75F + 0.16F;
        float height = (float) draw.getBbHeight();

        beginMesh(count);
        this.triangleCount = 0;
        this.sprites.clear();

        int cell = (int) (this.time * JITTER_CELL_RATE);
        float cellFrac = this.time * JITTER_CELL_RATE - cell;

        for (int bolt = 0; bolt < count; bolt++) {
            float phase = this.time * 1.3F + (float) bolt / count;
            int flashCell = (int) Math.floor(phase);
            float flashFrac = phase - flashCell;

            // each bolt only shows during a short window of its own phase -> flicker
            float fadeIn = Mth.clamp(flashFrac / 0.08F, 0.0F, 1.0F);
            float fadeOut = 1.0F - Mth.clamp((flashFrac - 0.42F) / 0.3F, 0.0F, 1.0F);
            float intensity = fadeIn * fadeOut
                    * Mth.clamp(0.55F + 0.45F * noise(bolt, 900, cell * 4), 0.0F, 1.0F);
            if (intensity <= 0.01F) continue;

            float bob = height * 0.16F * (float) Math.sin(this.time * 1.7F + bolt * 2.1F);
            float baseAngle = noise(bolt, 11, flashCell) * (float) Math.PI;
            float arc = 0.6F + 0.5F * Math.abs(noise(bolt, 12, flashCell));
            float arcDir = noise(bolt, 13, flashCell) >= 0.0F ? 1.0F : -1.0F;
            float startY = height * (0.85F + 0.05F * noise(bolt, 14, flashCell));
            float drop = height * 0.67F;
            float boltRadius = radius * (1.0F + 0.12F * noise(bolt, 15, flashCell));
            float scroll = this.time * 0.9F;

            float px = 0.0F;
            float py = 0.0F;
            float pz = 0.0F;
            for (int point = 0; point < POINTS_PER_BOLT; point++) {
                float t = (float) point / (POINTS_PER_BOLT - 1);
                float angle = baseAngle + scroll + arcDir * arc * (float) (Math.PI * 2.0) * t;
                float x = (float) (Math.cos(angle) * boltRadius);
                float z = (float) (Math.sin(angle) * boltRadius);
                float y = startY - drop * t + bob;

                if (point > 0) {
                    int seed = ((bolt * 131 + point) * 17 + cell * 101) | 1;
                    stroke(base, camPos, px, py, pz, x, y, z,
                            HALO_WIDTH * widthScale, HALO_ALPHA, seed, cell, cellFrac, intensity, color);
                    stroke(base, camPos, px, py, pz, x, y, z,
                            CORE_WIDTH * widthScale, CORE_ALPHA, seed, cell, cellFrac, intensity, color);
                    stroke(base, camPos, px, py, pz, x, y, z,
                            CORE2_WIDTH * widthScale, CORE_ALPHA, seed, cell, cellFrac, intensity, color);
                }

                if ((point & 1) == 0) {
                    this.sprites.add(new ParticlesWorldRenderer.Sprite(
                            new Vec3(base.x + x, base.y + y, base.z + z),
                            0.09F * widthScale * intensity,
                            ColorUtil.withAlpha(color, Math.round(ColorUtil.alpha(color) * 0.35F * intensity))));
                }

                px = x;
                py = y;
                pz = z;
            }
        }

        if (this.triangleCount > 0) {
            submit(camera);
        }
        if (!this.sprites.isEmpty()) {
            this.particles.render(this.sprites, BLOOM_BRIGHTNESS, true, true);
        }
    }

    private void updateAppear(boolean hasTarget, float dt) {
        float goal = hasTarget ? 1.0F : 0.0F;
        float step = dt / FADE_SECONDS;
        if (this.appear < goal) {
            this.appear = Math.min(goal, this.appear + step);
        } else {
            this.appear = Math.max(goal, this.appear - step);
        }
    }

    private static float eased(float value) {
        float k = Mth.clamp(value, 0.0F, 1.0F);
        return k < 0.5F
                ? 4.0F * k * k * k
                : 1.0F - (float) (Math.pow(-2.0F * k + 2.0F, 3.0) / 2.0);
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

        // basis perpendicular to the segment that never collapses
        boolean vertical = Math.abs(dirY) >= 0.9F;
        float u = vertical ? 1.0F : 0.0F;
        float v = vertical ? 0.0F : 1.0F;
        float p1x = -dirZ * v;
        float p1y = dirZ * u;
        float p1z = dirX * v - dirY * u;
        float p1l = (float) Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        if (p1l < 1.0E-5F) p1l = 1.0F;
        p1x /= p1l;
        p1y /= p1l;
        p1z /= p1l;

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

        subdivide(base, cam, x1, y1, z1, mx, my, mz,
                depth - 1, lineWidth, from, midColor, seed * 2 + 7, level, cell, cellFrac);
        subdivide(base, cam, mx, my, mz, x2, y2, z2,
                depth - 1, lineWidth, midColor, to, seed * 2 + 9, level, cell, cellFrac);

        // one side branch per subdivision, tapering out to fully transparent
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

    /** Expands a single leaf segment into a camera-facing ribbon quad. */
    private void emitRibbon(Vec3 base, Vec3 cam,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float width, int from, int to) {
        if (width <= 0.0F || (ColorUtil.alpha(from) <= 0 && ColorUtil.alpha(to) <= 0)) return;

        double w1x = base.x + x1;
        double w1y = base.y + y1;
        double w1z = base.z + z1;
        double w2x = base.x + x2;
        double w2y = base.y + y2;
        double w2z = base.z + z2;

        double sdx = w2x - w1x;
        double sdy = w2y - w1y;
        double sdz = w2z - w1z;
        double midX = (w1x + w2x) * 0.5D;
        double midY = (w1y + w2y) * 0.5D;
        double midZ = (w1z + w2z) * 0.5D;
        double vx = midX - cam.x;
        double vy = midY - cam.y;
        double vz = midZ - cam.z;

        double sx = sdy * vz - sdz * vy;
        double sy = sdz * vx - sdx * vz;
        double sz = sdx * vy - sdy * vx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-8D || !Double.isFinite(sl)) return;
        sx /= sl;
        sy /= sl;
        sz /= sl;

        float half = width * 0.5F;
        float ox = (float) (sx * half);
        float oy = (float) (sy * half);
        float oz = (float) (sz * half);

        float ax = (float) (w1x - cam.x);
        float ay = (float) (w1y - cam.y);
        float az = (float) (w1z - cam.z);
        float bx = (float) (w2x - cam.x);
        float by = (float) (w2y - cam.y);
        float bz = (float) (w2z - cam.z);

        float c1x = ax - ox, c1y = ay - oy, c1z = az - oz;
        float c2x = ax + ox, c2y = ay + oy, c2z = az + oz;
        float c3x = bx + ox, c3y = by + oy, c3z = bz + oz;
        float c4x = bx - ox, c4y = by - oy, c4z = bz - oz;

        BufferBuilder builder = this.meshBuilder;
        builder.addVertex(c1x, c1y, c1z).setColor(from);
        builder.addVertex(c2x, c2y, c2z).setColor(from);
        builder.addVertex(c3x, c3y, c3z).setColor(to);

        builder.addVertex(c1x, c1y, c1z).setColor(from);
        builder.addVertex(c3x, c3y, c3z).setColor(to);
        builder.addVertex(c4x, c4y, c4z).setColor(to);

        this.triangleCount += 2;
    }

    /** Deterministic hash noise in [-1, 1]. */
    private static float noise(int a, int b, int c) {
        int hash = a * 374761393 + b * 668265263 + c * 1274126177;
        hash = (hash ^ hash >>> 13) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 65535) / 32768.0F - 1.0F;
    }

    /** Smoothly interpolates between two adjacent noise cells. */
    private static float smoothNoise(int a, int b, int cell, float t) {
        float from = noise(a, b, cell * 4);
        float to = noise(a, b, (cell + 1) * 4);
        float smooth = t * t * (3.0F - 2.0F * t);
        return from + (to - from) * smooth;
    }

    // ---- mesh plumbing ----

    private void beginMesh(int bolts) {
        int leafSegments = bolts * SEGMENTS_PER_BOLT * STROKES_PER_SEGMENT * MAX_LEAF_SEGMENTS;
        int bytes = leafSegments * 6 * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        // Растущий буфер: рекурсивное ветвление молнии может выдать больше листьев, чем
        // сметам, и exactlySized тогда кидал исключение в середине кадра.
        this.meshBuilder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(bytes, 64 * 1024)),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        );
    }

    private void submit(Camera camera) {
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) return;

        MeshData mesh = this.meshBuilder.buildOrThrow();
        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = mesh.vertexBuffer();
            int byteCount = vertexData.remaining();
            int vertexCount = mesh.drawState().vertexCount();
            if (vertexCount <= 0) return;

            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (target == null || target.getColorTextureView() == null) return;

            ensureVertexCapacity(byteCount);
            device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, byteCount), vertexData);

            Matrix4f mvp = new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));
            ensureTransformBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, TRANSFORM_SIZE)
                        .putMat4f(mvp)
                        .get();
                device.createCommandEncoder().writeToBuffer(this.transformBuffer.slice(), data);
            }

            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer Lightning ESP",
                    target.getColorTextureView(),
                    Optional.empty()
            )) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Projection", this.transformBuffer);
                pass.setVertexBuffer(0, this.vertexBuffer.slice(0, byteCount));
                pass.draw(vertexCount, 1, 0, 0);
            }
        } finally {
            meshBuilder = null;
            if (mesh != null) {
                mesh.close();
            }
        }
    }

    private void ensureTransformBuffer() {
        if (this.transformBuffer != null) return;
        this.transformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Lightning ESP UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                TRANSFORM_SIZE
        );
    }

    private void ensureVertexCapacity(int byteSize) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Lightning ESP Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                capacity
        );
    }

    public void release() {
        this.appear = 0.0F;
        this.time = 0.0F;
        this.lastFrameNanos = 0L;
        this.lastTarget = null;
        this.triangleCount = 0;
        this.meshBuilder = null;
        this.sprites.clear();
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
        if (this.transformBuffer != null) {
            this.transformBuffer.close();
            this.transformBuffer = null;
        }
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }
}
