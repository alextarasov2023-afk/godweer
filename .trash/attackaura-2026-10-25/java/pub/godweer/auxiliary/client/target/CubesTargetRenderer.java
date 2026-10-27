package pub.godweer.auxiliary.client.target;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
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
import org.joml.Vector4f;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer.Sprite;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * Create by daun kvass
 */
public final class CubesTargetRenderer {
    private static final float LINE_WIDTH = 0.0022F;
    private static final float CORNER_RATIO = 0.32F;
    private static final float[][] CUBE_LAYOUT = {
            {  22.0F, 0.25F, 0.93F, 0.165F,  38.0F,  28.0F,  18.0F },
            { -42.0F, 0.14F, 0.82F, 0.115F, 112.0F, -34.0F,  24.0F },
            { 112.0F, 0.08F, 0.71F, 0.180F,  64.0F,  32.0F, -20.0F },
            { 166.0F, 0.18F, 0.68F, 0.170F, 148.0F, -26.0F,  30.0F },
            {  56.0F, 0.08F, 0.38F, 0.185F,  25.0F,  36.0F, -28.0F },
            { 192.0F, 0.24F, 0.35F, 0.150F,  82.0F, -30.0F,  16.0F },
            {  14.0F, 0.15F, 0.22F, 0.125F, 134.0F,  22.0F, -35.0F },
            { -55.0F, 0.06F, 0.16F, 0.095F,  48.0F, -40.0F,  12.0F },
            { 154.0F, 0.12F, 0.07F, 0.120F, 165.0F,  28.0F, -22.0F },
            {  68.0F, 0.08F, -0.04F, 0.155F, 95.0F, -24.0F,  26.0F },
            { -128.0F, 0.14F, 0.86F, 0.140F, 52.0F,  30.0F, -18.0F },
            { -95.0F, 0.12F, 0.48F, 0.150F, 120.0F, -28.0F,  32.0F }
    };
    private static final int CUBE_COUNT = CUBE_LAYOUT.length;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/target_cubes_glow"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final List<SwarmCube> cubes = new ArrayList<>();
    private final List<Sprite> glowSprites = new ArrayList<>(CUBE_COUNT);
    private final ParticlesWorldRenderer glowRenderer = new ParticlesWorldRenderer();
    private final TargetDeathDissolve deathDissolve = new TargetDeathDissolve();

    private GpuBuffer vertexBuffer;
    private LivingEntity lastTarget;
    private Vec3 lastTargetPos;
    private int dissolvedTargetId = Integer.MIN_VALUE;
    private float alpha = 0.0F;
    private long lastFrameNanos = 0L;

    public CubesTargetRenderer() {
        initCubes();
    }

    private void initCubes() {
        this.cubes.clear();
        for (int i = 0; i < CUBE_LAYOUT.length; i++) {
            this.cubes.add(new SwarmCube(i, CUBE_LAYOUT[i]));
        }
    }

    public void render(LivingEntity activeTarget, float tickDelta, int baseColor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) {
            reset();
            return;
        }

        long nowNanos = System.nanoTime();
        long nowMillis = System.currentTimeMillis();
        float dt = lastFrameNanos == 0L ? 0.016F : (float) ((nowNanos - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = nowNanos;
        dt = Mth.clamp(dt, 0.001F, 0.05F);

        boolean present = valid(activeTarget);
        if (present) {
            lastTarget = activeTarget;
            if (activeTarget.getId() == dissolvedTargetId) dissolvedTargetId = Integer.MIN_VALUE;
        }

        if (this.lastTarget != null && (!present || dead(this.lastTarget)) && this.lastTarget.getId() != this.dissolvedTargetId) {
            this.deathDissolve.burst(this.glowSprites, this.lastTarget, baseColor, nowMillis);
            this.dissolvedTargetId = this.lastTarget.getId();
            present = false;
        }

        updateAnimation(activeTarget, dt, tickDelta);

        if (alpha <= 0.005F || lastTargetPos == null) {
            this.deathDissolve.render(dt, nowMillis);
            return;
        }

        var renderTarget = mc.gameRenderer.mainRenderTarget();
        if (renderTarget == null || renderTarget.getColorTextureView() == null || renderTarget.getDepthTextureView() == null) {
            this.deathDissolve.render(dt, nowMillis);
            return;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f viewPose = Render3DUtil.cameraViewPose(camera);

        int color = ColorUtil.multiplyAlpha(baseColor, alpha);

        float spawnScale = easeOutBack(Math.min(1.0F, alpha * 1.15F));
        float targetHeight = lastTarget != null ? lastTarget.getBbHeight() : 1.8F;
        float targetWidth = lastTarget != null ? lastTarget.getBbWidth() : 0.6F;

        this.glowSprites.clear();

        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(this.cubes.size() * 160 * DefaultVertexFormat.POSITION_COLOR.getVertexSize(), 8192)),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        );

        int drawnVertices = 0;
        Matrix4f rotMatrix = new Matrix4f();
        Vector4f[] v = new Vector4f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = new Vector4f();
        }

        for (SwarmCube cube : this.cubes) {
            cube.update(dt);

            float baseRadius = (targetWidth * 0.50F + cube.radiusOffset);
            float currentRadius = baseRadius * spawnScale;
            if (activeTarget == null) {
                currentRadius += (1.0F - alpha) * 0.45F;
            }

            double waveY = Math.sin(cube.floatPhase) * 0.032D;
            double posX = Math.cos(cube.angle) * currentRadius;
            double posZ = Math.sin(cube.angle) * currentRadius;
            double posY = (cube.normalizedHeight * targetHeight) * spawnScale + waveY;

            Vec3 cubeWorldPos = lastTargetPos.add(posX, posY, posZ);

            float cubeSize = cube.size * spawnScale;
            float h = cubeSize * 0.5F;

            float glowSize = cubeSize * 1.1F;
            float glowAlpha = alpha * 0.35F;
            if (glowAlpha > 0.005F && glowSize > 0.001F) {
                this.glowSprites.add(new Sprite(
                        cubeWorldPos,
                        glowSize,
                        ColorUtil.multiplyAlpha(color, glowAlpha)
                ));
            }

            rotMatrix.identity().rotateXYZ(
                    (float) Math.toRadians(cube.pitch),
                    (float) Math.toRadians(cube.yaw),
                    (float) Math.toRadians(cube.roll)
            );

            transformVertex(v[0], -h, -h, -h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[1],  h, -h, -h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[2],  h,  h, -h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[3], -h,  h, -h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[4], -h, -h,  h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[5],  h, -h,  h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[6],  h,  h,  h, rotMatrix, cubeWorldPos, cameraPos, viewPose);
            transformVertex(v[7], -h,  h,  h, rotMatrix, cubeWorldPos, cameraPos, viewPose);

            int cFill = applyShade(color, 1.0F, alpha * 0.12F);
            addQuad(builder, v[0], v[3], v[2], v[1], cFill);
            addQuad(builder, v[5], v[6], v[7], v[4], cFill);
            addQuad(builder, v[3], v[7], v[6], v[2], cFill);
            addQuad(builder, v[4], v[0], v[1], v[5], cFill);
            addQuad(builder, v[4], v[7], v[3], v[0], cFill);
            addQuad(builder, v[1], v[2], v[6], v[5], cFill);
            drawnVertices += 36;

            int cEdge = applyShade(color, 1.55F, alpha * 0.95F);
            drawnVertices += renderCornerEdge(builder, v[0], v[1], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[1], v[2], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[2], v[3], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[3], v[0], CORNER_RATIO, cEdge);

            drawnVertices += renderCornerEdge(builder, v[4], v[5], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[5], v[6], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[6], v[7], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[7], v[4], CORNER_RATIO, cEdge);

            drawnVertices += renderCornerEdge(builder, v[0], v[4], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[1], v[5], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[2], v[6], CORNER_RATIO, cEdge);
            drawnVertices += renderCornerEdge(builder, v[3], v[7], CORNER_RATIO, cEdge);
        }

        if (!this.glowSprites.isEmpty()) {
            this.glowRenderer.render(this.glowSprites, 1.25F, true);
        }

        this.deathDissolve.render(dt, nowMillis);

        if (drawnVertices == 0) return;

        MeshData meshData = builder.buildOrThrow();
        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = meshData.vertexBuffer();
            int remainingBytes = vertexData.remaining();
            ensureVertexCapacity(remainingBytes);
            device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, remainingBytes), vertexData);

            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer Target Cubes Pass",
                    renderTarget.getColorTextureView(),
                    Optional.empty(),
                    renderTarget.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setVertexBuffer(0, this.vertexBuffer.slice(0, remainingBytes));
                pass.draw(drawnVertices, 1, 0, 0);
            }
        } finally {
            meshData.close();
        }
    }

    private static int renderCornerEdge(BufferBuilder builder, Vector4f a, Vector4f b, float ratio, int color) {
        Vector4f aEnd = new Vector4f(
                a.x + (b.x - a.x) * ratio,
                a.y + (b.y - a.y) * ratio,
                a.z + (b.z - a.z) * ratio,
                1.0F
        );
        Vector4f bStart = new Vector4f(
                b.x + (a.x - b.x) * ratio,
                b.y + (a.y - b.y) * ratio,
                b.z + (a.z - b.z) * ratio,
                1.0F
        );
        return addLine(builder, a, aEnd, LINE_WIDTH, color) + addLine(builder, bStart, b, LINE_WIDTH, color);
    }

    private static int addLine(BufferBuilder builder, Vector4f a, Vector4f b, float width, int color) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float len = (float) Math.hypot(dx, dy);
        float nx = len > 0.0001F ? -dy / len * width : width;
        float ny = len > 0.0001F ? dx / len * width : 0.0F;

        builder.addVertex(a.x + nx, a.y + ny, a.z).setColor(color);
        builder.addVertex(a.x - nx, a.y - ny, a.z).setColor(color);
        builder.addVertex(b.x - nx, b.y - ny, b.z).setColor(color);
        builder.addVertex(a.x + nx, a.y + ny, a.z).setColor(color);
        builder.addVertex(b.x - nx, b.y - ny, b.z).setColor(color);
        builder.addVertex(b.x + nx, b.y + ny, b.z).setColor(color);
        return 6;
    }

    private void updateAnimation(LivingEntity activeTarget, float dt, float tickDelta) {
        boolean valid = activeTarget != null && activeTarget.isAlive() && !activeTarget.isRemoved();
        if (valid) {
            lastTarget = activeTarget;
            Vec3 targetCenter = Render3DUtil.interpolatedPosition(activeTarget, tickDelta);
            if (lastTargetPos == null) {
                lastTargetPos = targetCenter;
            } else {
                lastTargetPos = lastTargetPos.lerp(targetCenter, Math.min(1.0, dt * 15.0));
            }
        }

        float targetAlpha = valid ? 1.0F : 0.0F;
        float speed = valid ? 5.5F : 4.0F;
        alpha += (targetAlpha - alpha) * Mth.clamp(dt * speed, 0.0F, 1.0F);
    }

    private static void transformVertex(Vector4f out, float lx, float ly, float lz, Matrix4f rot, Vec3 center, Vec3 cameraPos, Matrix4f viewPose) {
        out.set(lx, ly, lz, 1.0F);
        rot.transform(out);
        Vec3 world = center.add(out.x, out.y, out.z);
        Vector4f view = Render3DUtil.toViewSpace(world, cameraPos, viewPose);
        out.set(view.x, view.y, view.z, 1.0F);
    }

    private static void addQuad(BufferBuilder builder, Vector4f v1, Vector4f v2, Vector4f v3, Vector4f v4, int color) {
        builder.addVertex(v1.x, v1.y, v1.z).setColor(color);
        builder.addVertex(v2.x, v2.y, v2.z).setColor(color);
        builder.addVertex(v3.x, v3.y, v3.z).setColor(color);
        builder.addVertex(v1.x, v1.y, v1.z).setColor(color);
        builder.addVertex(v3.x, v3.y, v3.z).setColor(color);
        builder.addVertex(v4.x, v4.y, v4.z).setColor(color);
    }

    private static int applyShade(int color, float shade, float alpha) {
        int r = Math.min(255, (int) (ColorUtil.red(color) * shade));
        int g = Math.min(255, (int) (ColorUtil.green(color) * shade));
        int b = Math.min(255, (int) (ColorUtil.blue(color) * shade));
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private static boolean dead(LivingEntity entity) {
        return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
    }

    public void reset() {
        this.lastTarget = null;
        this.lastTargetPos = null;
        this.alpha = 0.0F;
        this.lastFrameNanos = 0L;
        this.glowSprites.clear();
        this.deathDissolve.clear();
    }

    public void release() {
        reset();
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
    }

    private void ensureVertexCapacity(int byteSize) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 48 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Target Cubes Vertex Buffer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                capacity
        );
    }

    private static final class SwarmCube {
        float angle;
        final float orbitSpeed;
        final float radiusOffset;
        final float normalizedHeight;
        final float size;
        float floatPhase;
        final float floatSpeed;
        float yaw, pitch, roll;
        final float yawSpeed, pitchSpeed, rollSpeed;

        SwarmCube(int index, float[] spec) {
            this.angle = (float) Math.toRadians(spec[0]);
            this.orbitSpeed = (index % 2 == 0 ? 1.0F : -0.7F) * 0.20F;
            this.radiusOffset = spec[1];
            this.normalizedHeight = spec[2];
            this.size = spec[3];

            this.floatPhase = index * 1.15F;
            this.floatSpeed = 1.15F;

            this.yaw = spec[4];
            this.pitch = spec[5];
            this.roll = spec[6];

            this.yawSpeed = index % 2 == 0 ? 12.0F : -12.0F;
            this.pitchSpeed = index % 3 == 0 ? -9.0F : 9.0F;
            this.rollSpeed = 8.0F;
        }

        void update(float dt) {
            this.angle += orbitSpeed * dt;
            this.floatPhase += floatSpeed * dt;
            this.yaw += yawSpeed * dt;
            this.pitch += pitchSpeed * dt;
            this.roll += rollSpeed * dt;
        }
    }

    public boolean hasActive() {
        return this.alpha > 0.005F || this.deathDissolve.hasActive();
    }
}