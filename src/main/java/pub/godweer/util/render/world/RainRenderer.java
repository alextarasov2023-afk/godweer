package pub.godweer.util.render.world;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import pub.godweer.util.style.Theme;
import pub.godweer.module.impl.render.Rain;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * Simulated world-space rain.
 *
 * <p>Every drop is an object with an absolute world position, velocity and
 * mass. Gravity pulls it down toward a terminal velocity, gusts push it
 * sideways, and solid blocks stop it with a ground ring plus rebound sparks.
 * The spawn disc follows the camera, but drops themselves never do: walking
 * away simply leaves them behind while new ones appear ahead.
 *
 * <p>Drawn as view-space quads (one normal pass, one additive glow pass) over
 * a shared {@code POSITION_COLOR} pipeline with a real depth test.
 */
public final class RainRenderer {

    private static final int MAX_FALL = 3600;
    private static final int MAX_RINGS = 600;
    private static final int MAX_SPARKS = 1100;
    private static final int MAX_HAZE = 70;
    private static final int RING_SEGMENTS = 10;

    private static final double GRAVITY = 24.0;
    private static final double TERMINAL = 36.0;

    private static final RenderPipeline TRANSLUCENT = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/rain_fall"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline ADDITIVE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/rain_glow"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final class FallDrop {
        double x, y, z;
        double vx, vy, vz;
        double mass = 1.0;
        double ground;
        float phase;
        float brightness = 1.0F;
        boolean dead;
    }

    private static final class ImpactRing {
        double x, y, z;
        float life;
        float maxLife = 0.4F;
        float spread = 0.4F;
        boolean water;
    }

    private static final class Spark {
        double x, y, z;
        double vx, vy, vz;
        float life;
        float maxLife = 0.4F;
    }

    private static final class Haze {
        double x, y, z;
        double vx, vz;
        float size = 3.0F;
        float life;
        float maxLife = 4.0F;
    }

    private final List<FallDrop> drops = new ArrayList<>();
    private final List<ImpactRing> rings = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Haze> haze = new ArrayList<>();
    private final Map<Long, Integer> groundCache = new HashMap<>();
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final Random random = new Random();

    private final Vector4f tmpC = new Vector4f();
    private final Vector4f tmpD = new Vector4f();

    private ClientLevel levelRef;
    private Vec3 cam;
    private Matrix4f viewPose;
    private GpuBuffer vertexBuffer;
    private long lastFrameNanos;
    private float clock;
    private float appear;
    private float flash;
    private float nextFlash = 8.0F;

    public void render(Rain module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (module == null || !module.isState() || mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return;
        }
        if (this.levelRef != mc.level) {
            this.levelRef = mc.level;
            clearAll();
        }

        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 1.0F / 60.0F
                : Mth.clamp((now - this.lastFrameNanos) * 1.0E-9F, 0.0F, 0.05F);
        this.lastFrameNanos = now;
        if (mc.isPaused()) {
            dt = 0.0F;
        }

        float rainLevel = 0.0F;
        try {
            rainLevel = mc.level.getRainLevel(tickDelta);
        } catch (Throwable ignored) {
        }
        float goal = !module.onlyWhenRaining.getValue() || rainLevel > 0.12F ? 1.0F : 0.0F;
        this.appear += Mth.clamp(goal - this.appear, -dt / 0.45F, dt / 0.45F);
        if (this.appear <= 0.001F) {
            clearAll();
            return;
        }
        if (module.skyCheck.getValue()
                && (!mc.level.dimensionType().hasSkyLight() || mc.level.dimensionType().hasCeiling())) {
            clearAll();
            return;
        }

        var cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (cameraState == null || !cameraState.initialized || target == null
                || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
            return;
        }
        this.cam = cameraState.pos;
        this.viewPose = cameraState.viewRotationMatrix;

        if (dt > 0.0F) {
            this.clock += dt;
            updateStorm(module, dt);
            updateDrops(mc.level, module, dt);
            updateRings(dt);
            updateSparks(dt);
            updateHaze(mc.level, module, dt);
        }
        if (this.drops.isEmpty() && this.rings.isEmpty() && this.sparks.isEmpty() && this.haze.isEmpty()) {
            return;
        }

        int argb = resolveColor(module);
        float rr = ((argb >> 16) & 0xFF) / 255.0F;
        float gg = ((argb >> 8) & 0xFF) / 255.0F;
        float bb = (argb & 0xFF) / 255.0F;
        float ambient = ambientAt(mc.level, this.cam);
        float bright = Mth.clamp(ambient + this.flash * 0.9F, 0.0F, 1.6F);
        float alphaMul = module.opacity.getValue() * bright * this.appear;
        float sizeMul = module.dropSize.getValue();
        float radius = module.radius.getValue();

        int unit = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        BufferBuilder base = new BufferBuilder(
                new ByteBufferBuilder(Math.max(2048,
                        (this.drops.size() * 12 + this.rings.size() * RING_SEGMENTS * 6 + this.haze.size() * 6 + 8) * unit)),
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder glow = new BufferBuilder(
                new ByteBufferBuilder(Math.max(2048,
                        (this.drops.size() * 12 + this.rings.size() * 6 + this.sparks.size() * 6 + 8) * unit)),
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int baseVerts = 0;
        int glowVerts = 0;
        baseVerts += emitHaze(base, alphaMul);
        for (FallDrop d : this.drops) {
            double dx = d.x - this.cam.x;
            double dy = d.y - this.cam.y;
            double dz = d.z - this.cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > radius + 4.0) {
                continue;
            }
            float distFade = dist <= radius * 0.6F ? 1.0F
                    : 1.0F - Mth.clamp((float) ((dist - radius * 0.6F) / (radius * 0.4F + 1.0F)), 0.0F, 1.0F);
            if (distFade <= 0.02F) {
                continue;
            }
            float nearFade = dist < 0.8 ? (float) (dist / 0.8) : 1.0F;
            double speed = Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
            if (speed < 0.001) {
                continue;
            }
            float alpha = alphaMul * d.brightness * distFade * nearFade * 0.5F;
            if (alpha <= 0.006F) {
                continue;
            }
            alpha = Math.min(alpha, 0.95F);
            float halfLen = Mth.clamp((float) (speed * 0.05 * sizeMul), 0.18F, 2.4F) * 0.5F;
            float halfWid = (0.013F + 0.018F * (float) d.mass) * sizeMul;
            float ix = (float) (d.vx / speed);
            float iy = (float) (d.vy / speed);
            float iz = (float) (d.vz / speed);
            int head = pack(rr, gg, bb, alpha);
            // Wide soft body plus a faint skirt around it.
            baseVerts += emitStreak(base, d.x, d.y, d.z, ix, iy, iz, halfLen, halfWid, halfWid * 0.35F, head, 0);
            baseVerts += emitStreak(base, d.x, d.y, d.z, ix, iy, iz, halfLen, halfWid * 2.4F,
                    halfWid * 0.9F, pack(rr, gg, bb, alpha * 0.18F), 0);
            // Bright narrow core with a white leading tip.
            glowVerts += emitStreak(glow, d.x, d.y, d.z, ix, iy, iz, halfLen, halfWid * 0.45F,
                    halfWid * 0.16F, pack(rr, gg, bb, alpha * 0.32F), 0);
            glowVerts += emitStreak(glow,
                    d.x + ix * halfLen * 0.33, d.y + iy * halfLen * 0.33, d.z + iz * halfLen * 0.33,
                    ix, iy, iz, halfLen * 0.67F, halfWid * 0.6F, halfWid * 0.2F,
                    pack(1.0F, 1.0F, 1.0F, alpha * 0.55F), 0);
        }
        baseVerts += emitRings(base, alphaMul);
        glowVerts += emitCrowns(glow, alphaMul);
        glowVerts += emitSparks(glow, alphaMul, sizeMul, rr, gg, bb);

        drawMesh(base, baseVerts, TRANSLUCENT, target);
        drawMesh(glow, glowVerts, ADDITIVE, target);
        this.cam = null;
        this.viewPose = null;
    }

    // ------------------------------------------------------------- simulation

    private void updateDrops(ClientLevel level, Rain module, float dt) {
        float radius = module.radius.getValue();
        float altitude = module.altitude.getValue();
        String presetName = module.preset.getValue();
        float target = Mth.clamp(
                (int) (radius * radius * 0.016F * module.density.getValue() * presetIntensity(presetName) * 24.0F),
                40, MAX_FALL);

        float killR = radius + 6.0F;
        for (int i = this.drops.size() - 1; i >= 0; i--) {
            FallDrop d = this.drops.get(i);
            double dx = d.x - this.cam.x;
            double dz = d.z - this.cam.z;
            if (d.dead || dx * dx + dz * dz > killR * killR || d.y < this.cam.y - altitude - 12.0) {
                int last = this.drops.size() - 1;
                this.drops.set(i, this.drops.get(last));
                this.drops.remove(last);
            }
        }

        boolean topping = this.drops.size() < target / 2;
        int budget = Math.min((int) target - this.drops.size(), topping ? 700 : 200);
        float heft = presetHeft(presetName);
        for (int i = 0; i < budget; i++) {
            spawnDrop(level, module, radius, altitude, heft, !topping);
        }

        float speedMul = module.fallSpeed.getValue();
        float windMul = module.wind.getValue() * presetGust(presetName);
        for (FallDrop d : this.drops) {
            double gust = 0.7 + 0.3 * Math.sin(this.clock * 0.5 + d.phase * 0.13)
                    + 0.15 * Math.sin(this.clock * 1.9 + d.phase * 1.7);
            double windX = windMul * 3.0 * gust;
            double windZ = windMul * 1.6 * Math.sin(this.clock * 0.3 + d.phase * 0.05) * gust;

            double drag = 1.0 - Math.min(0.9, Math.abs(d.vy) / (TERMINAL * d.mass));
            d.vy -= GRAVITY * d.mass * drag * dt;
            double terminal = -TERMINAL * d.mass;
            if (d.vy < terminal) {
                d.vy = terminal;
            }
            double blend = Math.min(1.0, dt * 3.0);
            d.vx += (windX - d.vx) * blend;
            d.vz += (windZ - d.vz) * blend;

            double nx = d.x + d.vx * dt * speedMul;
            double ny = d.y + d.vy * dt * speedMul;
            double nz = d.z + d.vz * dt * speedMul;

            if (ny <= d.ground + 2.5) {
                if (solidAt(level, nx, ny, nz)) {
                    if (module.splashes.getValue()) {
                        impact(level, module, nx, ny, nz, d);
                    }
                    d.dead = true;
                    continue;
                }
                if (ny <= d.ground - 3.0) {
                    d.dead = true;
                    continue;
                }
            }
            d.x = nx;
            d.y = ny;
            d.z = nz;
        }
    }

    private void spawnDrop(ClientLevel level, Rain module, float radius, float altitude, float heft, boolean topSpawn) {
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(this.random.nextDouble()) * radius;
        double x = this.cam.x + Math.cos(angle) * dist;
        double z = this.cam.z + Math.sin(angle) * dist;
        double y = topSpawn
                ? this.cam.y + altitude * (0.75 + this.random.nextDouble() * 0.25)
                : this.cam.y - 4.0 + this.random.nextDouble() * (altitude + 4.0);

        int surface = surfaceHeight(level, Mth.floor(x), Mth.floor(z));
        if (module.skyCheck.getValue() && surface != Integer.MIN_VALUE && y < surface + 0.5) {
            return;
        }
        FallDrop d = new FallDrop();
        d.x = x;
        d.y = y;
        d.z = z;
        d.mass = (0.6 + this.random.nextDouble() * 0.8) * heft;
        d.vy = -(6.0 + this.random.nextDouble() * 5.0) * d.mass;
        d.phase = this.random.nextFloat() * 62.8F;
        d.brightness = 0.55F + this.random.nextFloat() * 0.45F;
        d.ground = surface == Integer.MIN_VALUE ? level.getMinY() : surface;
        this.drops.add(d);
    }

    private void impact(ClientLevel level, Rain module, double x, double y, double z, FallDrop d) {
        if (this.rings.size() >= MAX_RINGS) {
            return;
        }
        ImpactRing ring = new ImpactRing();
        ring.x = x;
        ring.z = z;
        ring.y = Math.floor(y) + 1.002;
        ring.maxLife = 0.32F + this.random.nextFloat() * 0.23F;
        ring.spread = 0.3F + 0.22F * (float) d.mass;
        ring.water = fluidAt(level, x, y - 0.15, z);
        this.rings.add(ring);

        if (!module.droplets.getValue() || this.sparks.size() >= MAX_SPARKS) {
            return;
        }
        int shards = ring.water ? 4 : 3;
        for (int i = 0; i < shards; i++) {
            Spark s = new Spark();
            s.x = x;
            s.y = ring.y + 0.02;
            s.z = z;
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double power = (1.0 + this.random.nextDouble() * 1.4) * d.mass;
            s.vx = Math.cos(a) * power * 0.45 + d.vx * 0.08;
            s.vz = Math.sin(a) * power * 0.45 + d.vz * 0.08;
            s.vy = 2.0 + this.random.nextDouble() * 2.0;
            s.maxLife = 0.3F + this.random.nextFloat() * 0.2F;
            this.sparks.add(s);
        }
    }

    private void updateRings(float dt) {
        for (int i = this.rings.size() - 1; i >= 0; i--) {
            ImpactRing ring = this.rings.get(i);
            ring.life += dt;
            if (ring.life >= ring.maxLife) {
                int last = this.rings.size() - 1;
                this.rings.set(i, this.rings.get(last));
                this.rings.remove(last);
            }
        }
    }

    private void updateSparks(float dt) {
        for (int i = this.sparks.size() - 1; i >= 0; i--) {
            Spark s = this.sparks.get(i);
            s.life += dt;
            if (s.life >= s.maxLife) {
                int last = this.sparks.size() - 1;
                this.sparks.set(i, this.sparks.get(last));
                this.sparks.remove(last);
                continue;
            }
            s.vy -= GRAVITY * 0.5 * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.z += s.vz * dt;
        }
    }

    private void updateHaze(ClientLevel level, Rain module, float dt) {
        if (!module.mist.getValue()) {
            this.haze.clear();
            return;
        }
        float radius = module.radius.getValue();
        int target = Mth.clamp((int) (18.0F * module.density.getValue() * presetIntensity(module.preset.getValue())),
                5, MAX_HAZE);
        for (int i = this.haze.size() - 1; i >= 0; i--) {
            Haze m = this.haze.get(i);
            m.life += dt;
            m.x += m.vx * dt;
            m.z += m.vz * dt;
            double dx = m.x - this.cam.x;
            double dz = m.z - this.cam.z;
            if (m.life > m.maxLife || dx * dx + dz * dz > radius * radius) {
                int last = this.haze.size() - 1;
                this.haze.set(i, this.haze.get(last));
                this.haze.remove(last);
            }
        }
        while (this.haze.size() < target) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * radius * 0.85;
            double x = this.cam.x + Math.cos(angle) * dist;
            double z = this.cam.z + Math.sin(angle) * dist;
            int surface = surfaceHeight(level, Mth.floor(x), Mth.floor(z));
            if (surface == Integer.MIN_VALUE) {
                return;
            }
            Haze m = new Haze();
            m.x = x;
            m.z = z;
            m.y = surface + 0.05 + this.random.nextDouble() * 0.85;
            m.size = 2.0F + this.random.nextFloat() * 3.0F;
            m.maxLife = 2.5F + this.random.nextFloat() * 2.5F;
            m.life = this.random.nextFloat() * 1.0F;
            m.vx = (this.random.nextDouble() - 0.5) * 0.6 * module.wind.getValue();
            m.vz = (this.random.nextDouble() - 0.5) * 0.6 * module.wind.getValue();
            this.haze.add(m);
        }
    }

    private void updateStorm(Rain module, float dt) {
        if (!module.storm.getValue()) {
            this.flash = 0.0F;
            return;
        }
        this.flash = Math.max(0.0F, this.flash - dt * 3.0F);
        this.nextFlash -= dt;
        if (this.nextFlash <= 0.0F) {
            this.flash = 0.5F + this.random.nextFloat() * 0.5F;
            this.nextFlash = 7.0F + this.random.nextFloat() * 8.0F;
        }
    }

    // ------------------------------------------------------------------ world

    private int surfaceHeight(ClientLevel level, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Integer cached = this.groundCache.get(key);
        if (cached != null) {
            return cached;
        }
        int top = Integer.MIN_VALUE;
        try {
            if (level.hasChunk(x >> 4, z >> 4)) {
                LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                if (chunk != null) {
                    top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
                }
            }
        } catch (Throwable ignored) {
        }
        if (this.groundCache.size() > 16000) {
            this.groundCache.clear();
        }
        this.groundCache.put(key, top);
        return top;
    }

    private boolean solidAt(ClientLevel level, double x, double y, double z) {
        int bx = Mth.floor(x);
        int by = Mth.floor(y);
        int bz = Mth.floor(z);
        try {
            if (!level.hasChunk(bx >> 4, bz >> 4)) {
                return false;
            }
            this.scratch.set(bx, by, bz);
            BlockState state = level.getBlockState(this.scratch);
            if (!state.getFluidState().isEmpty()) {
                return true;
            }
            if (state.isAir()) {
                return false;
            }
            return !state.getCollisionShape(level, this.scratch).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean fluidAt(ClientLevel level, double x, double y, double z) {
        try {
            this.scratch.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            return !level.getBlockState(this.scratch).getFluidState().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int resolveColor(Rain module) {
        try {
            if (module.colorMode.is("Свой")) {
                return module.customColor.getValue();
            }
            if (module.colorMode.is("Тема")) {
                return Theme.getAccentColor();
            }
        } catch (Throwable ignored) {
        }
        return 0xFFC8DCE8;
    }

    private static float ambientAt(ClientLevel level, Vec3 camera) {
        try {
            BlockPos pos = new BlockPos(Mth.floor(camera.x), Mth.floor(camera.y), Mth.floor(camera.z));
            int total = level.getBrightness(LightLayer.BLOCK, pos) + level.getBrightness(LightLayer.SKY, pos);
            return Mth.clamp(total / 30.0F, 0.3F, 1.0F);
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    private static float presetIntensity(String preset) {
        if (preset.equalsIgnoreCase("Морось")) {
            return 0.45F;
        }
        if (preset.equalsIgnoreCase("Ливень")) {
            return 1.9F;
        }
        if (preset.equalsIgnoreCase("Шторм")) {
            return 2.7F;
        }
        return 1.0F;
    }

    private static float presetGust(String preset) {
        if (preset.equalsIgnoreCase("Морось")) {
            return 0.5F;
        }
        if (preset.equalsIgnoreCase("Ливень")) {
            return 1.4F;
        }
        if (preset.equalsIgnoreCase("Шторм")) {
            return 2.3F;
        }
        return 1.0F;
    }

    private static float presetHeft(String preset) {
        if (preset.equalsIgnoreCase("Морось")) {
            return 0.55F;
        }
        if (preset.equalsIgnoreCase("Ливень")) {
            return 1.25F;
        }
        if (preset.equalsIgnoreCase("Шторм")) {
            return 1.5F;
        }
        return 1.0F;
    }

    // ---------------------------------------------------------------- geometry

    private int emitHaze(BufferBuilder out, float alphaMul) {
        if (this.haze.isEmpty()) {
            return 0;
        }
        int verts = 0;
        for (Haze m : this.haze) {
            float progress = Mth.clamp(m.life / m.maxLife, 0.0F, 1.0F);
            float alpha = alphaMul * (float) Math.sin(progress * Math.PI) * 0.045F;
            if (alpha <= 0.003F) {
                continue;
            }
            worldToView(this.tmpC, m.x, m.y, m.z);
            if (this.tmpC.z > -0.5F) {
                continue;
            }
            float half = m.size * 0.5F;
            float top = m.size * 0.4F;
            int bottom = pack(0.75F, 0.82F, 0.9F, alpha);
            tri(out, this.tmpC.x - half, this.tmpC.y + top, this.tmpC.z, 0,
                    this.tmpC.x + half, this.tmpC.y + top, this.tmpC.z, 0,
                    this.tmpC.x + half, this.tmpC.y, this.tmpC.z, bottom);
            tri(out, this.tmpC.x - half, this.tmpC.y + top, this.tmpC.z, 0,
                    this.tmpC.x + half, this.tmpC.y, this.tmpC.z, bottom,
                    this.tmpC.x - half, this.tmpC.y, this.tmpC.z, bottom);
            verts += 6;
        }
        return verts;
    }

    private int emitRings(BufferBuilder out, float alphaMul) {
        if (this.rings.isEmpty()) {
            return 0;
        }
        int verts = 0;
        for (ImpactRing ring : this.rings) {
            float progress = Mth.clamp(ring.life / ring.maxLife, 0.0F, 1.0F);
            float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
            double dist = Math.hypot(ring.x - this.cam.x, ring.z - this.cam.z);
            if (dist > 32.0) {
                continue;
            }
            float fade = 1.0F - smoothstep(18.0F, 30.0F, (float) dist);
            float alpha = alphaMul * (1.0F - progress) * (1.0F - progress) * fade
                    * (ring.water ? 0.5F : 0.36F);
            if (alpha <= 0.008F) {
                continue;
            }
            float outer = ring.spread * (0.2F + eased * (ring.water ? 1.3F : 0.8F));
            float inner = outer * (0.55F + 0.3F * eased);
            int edge = pack(0.75F, 0.84F, 0.92F, alpha);
            for (int seg = 0; seg < RING_SEGMENTS; seg++) {
                double a0 = (Math.PI * 2.0 / RING_SEGMENTS) * seg;
                double a1 = (Math.PI * 2.0 / RING_SEGMENTS) * (seg + 1);
                worldToView(this.tmpC, ring.x + Math.cos(a0) * outer, ring.y, ring.z + Math.sin(a0) * outer);
                float oax = this.tmpC.x;
                float oay = this.tmpC.y;
                float oaz = this.tmpC.z;
                worldToView(this.tmpC, ring.x + Math.cos(a1) * outer, ring.y, ring.z + Math.sin(a1) * outer);
                float obx = this.tmpC.x;
                float oby = this.tmpC.y;
                float obz = this.tmpC.z;
                worldToView(this.tmpC, ring.x + Math.cos(a1) * inner, ring.y, ring.z + Math.sin(a1) * inner);
                float ibx = this.tmpC.x;
                float iby = this.tmpC.y;
                float ibz = this.tmpC.z;
                worldToView(this.tmpC, ring.x + Math.cos(a0) * inner, ring.y, ring.z + Math.sin(a0) * inner);
                tri(out, oax, oay, oaz, 0, obx, oby, obz, 0, ibx, iby, ibz, edge);
                tri(out, oax, oay, oaz, 0, ibx, iby, ibz, edge, this.tmpC.x, this.tmpC.y, this.tmpC.z, edge);
                verts += 6;
            }
        }
        return verts;
    }

    private int emitCrowns(BufferBuilder out, float alphaMul) {
        if (this.rings.isEmpty()) {
            return 0;
        }
        int verts = 0;
        for (ImpactRing ring : this.rings) {
            float progress = Mth.clamp(ring.life / ring.maxLife, 0.0F, 1.0F);
            if (progress > 0.5F) {
                continue;
            }
            float local = progress / 0.5F;
            float height = ring.spread * 0.6F * (float) Math.sin(local * Math.PI);
            float alpha = alphaMul * (1.0F - local) * 0.3F;
            if (alpha <= 0.008F || height <= 0.005F) {
                continue;
            }
            worldToView(this.tmpC, ring.x, ring.y, ring.z);
            if (this.tmpC.z > -0.12F) {
                continue;
            }
            float base = ring.spread * 0.18F;
            int bottom = pack(0.8F, 0.87F, 0.94F, alpha);
            tri(out, this.tmpC.x - base, this.tmpC.y, this.tmpC.z, bottom,
                    this.tmpC.x + base, this.tmpC.y, this.tmpC.z, bottom,
                    this.tmpC.x + base * 0.3F, this.tmpC.y + height, this.tmpC.z, 0);
            tri(out, this.tmpC.x - base, this.tmpC.y, this.tmpC.z, bottom,
                    this.tmpC.x + base * 0.3F, this.tmpC.y + height, this.tmpC.z, 0,
                    this.tmpC.x - base * 0.3F, this.tmpC.y + height, this.tmpC.z, 0);
            verts += 6;
        }
        return verts;
    }

    private int emitSparks(BufferBuilder out, float alphaMul, float sizeMul, float r, float g, float b) {
        if (this.sparks.isEmpty()) {
            return 0;
        }
        int verts = 0;
        for (Spark s : this.sparks) {
            float progress = Mth.clamp(s.life / s.maxLife, 0.0F, 1.0F);
            float alpha = alphaMul * (1.0F - progress) * 0.5F;
            if (alpha <= 0.008F) {
                continue;
            }
            double speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
            if (speed < 0.001) {
                continue;
            }
            float halfLen = Mth.clamp((float) (speed * 0.03 * sizeMul), 0.02F, 0.2F);
            float halfWid = 0.016F * sizeMul;
            verts += emitStreak(out, s.x, s.y, s.z,
                    (float) (s.vx / speed), (float) (s.vy / speed), (float) (s.vz / speed),
                    halfLen, halfWid, halfWid * 0.4F, pack(r, g, b, alpha), 0);
        }
        return verts;
    }

    /**
     * A tapered streak along a view-space direction: transparent tail,
     * solid head. Returns the emitted vertex count (6) or 0 when behind
     * the camera or degenerate.
     */
    private int emitStreak(BufferBuilder out, double cx, double cy, double cz,
                           float dx, float dy, float dz,
                           float halfLen, float headW, float tailW,
                           int headColor, int tailColor) {
        this.tmpC.set((float) (cx - this.cam.x), (float) (cy - this.cam.y), (float) (cz - this.cam.z), 1.0F);
        this.viewPose.transform(this.tmpC);
        this.tmpD.set(dx, dy, dz, 0.0F);
        this.viewPose.transform(this.tmpD);
        float hx = this.tmpC.x + this.tmpD.x * halfLen;
        float hy = this.tmpC.y + this.tmpD.y * halfLen;
        float hz = this.tmpC.z + this.tmpD.z * halfLen;
        float tx = this.tmpC.x - this.tmpD.x * halfLen;
        float ty = this.tmpC.y - this.tmpD.y * halfLen;
        float tz = this.tmpC.z - this.tmpD.z * halfLen;
        if (hz > -0.12F && tz > -0.12F) {
            return 0;
        }
        float px = -this.tmpD.y;
        float py = this.tmpD.x;
        float plen = (float) Math.sqrt(px * px + py * py);
        if (plen < 1.0E-4F) {
            return 0;
        }
        float nx = px / plen;
        float ny = py / plen;
        float hnx = nx * headW;
        float hny = ny * headW;
        float tnx = nx * tailW;
        float tny = ny * tailW;
        tri(out, tx - tnx, ty - tny, tz, tailColor,
                tx + tnx, ty + tny, tz, tailColor,
                hx + hnx, hy + hny, hz, headColor);
        tri(out, tx - tnx, ty - tny, tz, tailColor,
                hx + hnx, hy + hny, hz, headColor,
                hx - hnx, hy - hny, hz, headColor);
        return 6;
    }

    private void worldToView(Vector4f out, double x, double y, double z) {
        out.set((float) (x - this.cam.x), (float) (y - this.cam.y), (float) (z - this.cam.z), 1.0F);
        this.viewPose.transform(out);
    }

    private static void tri(BufferBuilder out,
                            float x1, float y1, float z1, int c1,
                            float x2, float y2, float z2, int c2,
                            float x3, float y3, float z3, int c3) {
        out.addVertex(x1, y1, z1).setColor(c1);
        out.addVertex(x2, y2, z2).setColor(c2);
        out.addVertex(x3, y3, z3).setColor(c3);
    }

    private void drawMesh(BufferBuilder builder, int verts, RenderPipeline pipeline, RenderTarget target) {
        if (verts == 0) {
            return;
        }
        try (MeshData mesh = builder.buildOrThrow()) {
            ByteBuffer data = mesh.vertexBuffer();
            int bytes = data.remaining();
            ensureCapacity(bytes);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, bytes), data);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Godweer Rain",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setVertexBuffer(0, this.vertexBuffer.slice(0, bytes));
                pass.draw(verts, 1, 0, 0);
            }
        }
    }

    private void ensureCapacity(int bytes) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= bytes) {
            return;
        }
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
        }
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Rain Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                Math.max(bytes + bytes / 2, 64 * 1024));
    }

    private static int pack(float r, float g, float b, float a) {
        int ai = Mth.clamp(Math.round(a * 255.0F), 0, 255);
        int ri = Mth.clamp(Math.round(r * 255.0F), 0, 255);
        int gi = Mth.clamp(Math.round(g * 255.0F), 0, 255);
        int bi = Mth.clamp(Math.round(b * 255.0F), 0, 255);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static float smoothstep(float low, float high, float value) {
        float t = Mth.clamp((value - low) / (high - low), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private void clearAll() {
        this.drops.clear();
        this.rings.clear();
        this.sparks.clear();
        this.haze.clear();
        this.groundCache.clear();
        this.flash = 0.0F;
        this.nextFlash = 8.0F;
    }

    public void release() {
        clearAll();
        this.levelRef = null;
        this.cam = null;
        this.viewPose = null;
        this.appear = 0.0F;
        this.clock = 0.0F;
        this.lastFrameNanos = 0L;
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
    }
}
