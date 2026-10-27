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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * Create by daun kvass
 */
public final class CircleTargetRenderer {
    private static final int SEGMENTS = 64;
    private static final float RING_THICKNESS = 0.009F;
    private static final float TRAIL_HEIGHT = 0.42F;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/target_circle_ribbon"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final List<CircleMote> motes = new ArrayList<>();
    private final List<Sprite> moteSprites = new ArrayList<>();
    private final ParticlesWorldRenderer particleRenderer = new ParticlesWorldRenderer();
    private final TargetDeathDissolve deathDissolve = new TargetDeathDissolve();
    private final Random random = new Random();

    private GpuBuffer vertexBuffer;
    private LivingEntity lastTarget;
    private Vec3 lastTargetPos;
    private int dissolvedTargetId = Integer.MIN_VALUE;
    private float alpha = 0.0F;
    private float clock = 0.0F;
    private long lastFrameNanos = 0L;

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
            this.deathDissolve.burst(this.moteSprites, this.lastTarget, baseColor, nowMillis);
            this.dissolvedTargetId = this.lastTarget.getId();
            present = false;
        }

        updateAnimation(activeTarget, dt, tickDelta);

        updateMotes(dt, baseColor, present);

        if (alpha <= 0.005F || lastTargetPos == null) {
            renderMotesAndDissolve(dt, nowMillis);
            return;
        }

        this.clock += dt * 2.5F;

        var renderTarget = mc.gameRenderer.mainRenderTarget();
        if (renderTarget == null || renderTarget.getColorTextureView() == null || renderTarget.getDepthTextureView() == null) {
            renderMotesAndDissolve(dt, nowMillis);
            return;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f viewPose = Render3DUtil.cameraViewPose(camera);

        int color = ColorUtil.multiplyAlpha(baseColor, alpha);

        float targetHeight = lastTarget != null ? lastTarget.getBbHeight() : 1.8F;
        float targetWidth = lastTarget != null ? lastTarget.getBbWidth() : 0.6F;

        float wave = (float) Math.sin(this.clock);
        float progress = 0.5F + 0.5F * wave;
        double currentY = this.lastTargetPos.y + (targetHeight * 0.12D) + (targetHeight * 0.76D * progress);

        float velocity = (float) Math.cos(this.clock);
        double trailY = currentY - (velocity * TRAIL_HEIGHT * alpha);

        float radius = targetWidth * 0.58F + 0.22F;
        float innerRadius = Math.max(0.01F, radius - RING_THICKNESS);
        float outerRadius = radius + RING_THICKNESS;

        if (present && alpha > 0.15F && this.motes.size() < 55) {
            int spawnCount = random.nextInt(3) + 1;
            for (int i = 0; i < spawnCount; i++) {
                double pAngle = random.nextDouble() * Math.PI * 2.0;
                Vec3 pPos = new Vec3(
                        lastTargetPos.x + Math.cos(pAngle) * radius,
                        currentY + (random.nextDouble() - 0.5) * 0.02,
                        lastTargetPos.z + Math.sin(pAngle) * radius
                );
                Vec3 pMotion = new Vec3(
                        Math.cos(pAngle) * 0.02,
                        -velocity * (0.08 + random.nextDouble() * 0.12),
                        Math.sin(pAngle) * 0.02
                );
                this.motes.add(new CircleMote(pPos, pMotion, 0.075F + random.nextFloat() * 0.05F, 0.55F + random.nextFloat() * 0.35F));
            }
        }

        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(SEGMENTS * 18 * DefaultVertexFormat.POSITION_COLOR.getVertexSize()),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        );

        int cLine = ColorUtil.multiplyAlpha(applyShade(color, 1.6F, 1.0F), alpha * 0.98F);
        int cTrailTop = ColorUtil.multiplyAlpha(applyShade(color, 1.25F, 1.0F), alpha * 0.52F);
        int cTrailBottom = ColorUtil.multiplyAlpha(color, 0.0F);

        for (int i = 0; i < SEGMENTS; i++) {
            double angle1 = (i * Math.PI * 2.0D) / SEGMENTS;
            double angle2 = ((i + 1) * Math.PI * 2.0D) / SEGMENTS;

            double cos1 = Math.cos(angle1), sin1 = Math.sin(angle1);
            double cos2 = Math.cos(angle2), sin2 = Math.sin(angle2);

            Vector4f in1 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos1 * innerRadius, currentY, lastTargetPos.z + sin1 * innerRadius), cameraPos, viewPose);
            Vector4f in2 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos2 * innerRadius, currentY, lastTargetPos.z + sin2 * innerRadius), cameraPos, viewPose);
            Vector4f out1 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos1 * outerRadius, currentY, lastTargetPos.z + sin1 * outerRadius), cameraPos, viewPose);
            Vector4f out2 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos2 * outerRadius, currentY, lastTargetPos.z + sin2 * outerRadius), cameraPos, viewPose);

            Vector4f trail1 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos1 * radius, trailY, lastTargetPos.z + sin1 * radius), cameraPos, viewPose);
            Vector4f trail2 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos2 * radius, trailY, lastTargetPos.z + sin2 * radius), cameraPos, viewPose);
            Vector4f mid1 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos1 * radius, currentY, lastTargetPos.z + sin1 * radius), cameraPos, viewPose);
            Vector4f mid2 = Render3DUtil.toViewSpace(new Vec3(lastTargetPos.x + cos2 * radius, currentY, lastTargetPos.z + sin2 * radius), cameraPos, viewPose);

            builder.addVertex(in1.x, in1.y, in1.z).setColor(cLine);
            builder.addVertex(out1.x, out1.y, out1.z).setColor(cLine);
            builder.addVertex(out2.x, out2.y, out2.z).setColor(cLine);

            builder.addVertex(in1.x, in1.y, in1.z).setColor(cLine);
            builder.addVertex(out2.x, out2.y, out2.z).setColor(cLine);
            builder.addVertex(in2.x, in2.y, in2.z).setColor(cLine);

            builder.addVertex(mid1.x, mid1.y, mid1.z).setColor(cTrailTop);
            builder.addVertex(mid2.x, mid2.y, mid2.z).setColor(cTrailTop);
            builder.addVertex(trail2.x, trail2.y, trail2.z).setColor(cTrailBottom);

            builder.addVertex(mid1.x, mid1.y, mid1.z).setColor(cTrailTop);
            builder.addVertex(trail2.x, trail2.y, trail2.z).setColor(cTrailBottom);
            builder.addVertex(trail1.x, trail1.y, trail1.z).setColor(cTrailBottom);
        }

        renderMotesAndDissolve(dt, nowMillis);

        MeshData meshData = builder.buildOrThrow();
        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = meshData.vertexBuffer();
            int remainingBytes = vertexData.remaining();
            ensureVertexCapacity(remainingBytes);
            device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, remainingBytes), vertexData);

            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer Target Circle Pass",
                    renderTarget.getColorTextureView(),
                    Optional.empty(),
                    renderTarget.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setVertexBuffer(0, this.vertexBuffer.slice(0, remainingBytes));
                pass.draw(SEGMENTS * 12, 1, 0, 0);
            }
        } finally {
            meshData.close();
        }
    }

    private void updateMotes(float dt, int baseColor, boolean present) {
        this.moteSprites.clear();
        Iterator<CircleMote> it = this.motes.iterator();
        while (it.hasNext()) {
            CircleMote m = it.next();
            m.age += dt;
            if (m.age >= m.maxLife) {
                it.remove();
                continue;
            }
            m.pos = m.pos.add(m.motion.scale(dt));
            float progress = m.age / m.maxLife;
            float moteAlpha = (1.0F - progress) * (1.0F - progress) * (present ? alpha : alpha * 0.8F);
            if (moteAlpha > 0.005F) {
                this.moteSprites.add(new Sprite(
                        m.pos,
                        m.size * (1.0F + progress * 0.45F),
                        ColorUtil.multiplyAlpha(baseColor, moteAlpha * 0.95F)
                ));
            }
        }
    }

    private void renderMotesAndDissolve(float dt, long nowMillis) {
        if (!this.moteSprites.isEmpty()) {
            this.particleRenderer.render(this.moteSprites, 1.25F, true);
        }
        this.deathDissolve.render(dt, nowMillis);
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
        float speed = valid ? 5.5F : 3.5F;
        alpha += (targetAlpha - alpha) * Mth.clamp(dt * speed, 0.0F, 1.0F);
    }

    private static int applyShade(int color, float shade, float alpha) {
        int r = Math.min(255, (int) (ColorUtil.red(color) * shade));
        int g = Math.min(255, (int) (ColorUtil.green(color) * shade));
        int b = Math.min(255, (int) (ColorUtil.blue(color) * shade));
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public boolean hasActive() {
        return this.alpha > 0.005F || !this.motes.isEmpty() || this.deathDissolve.hasActive();
    }

    public void reset() {
        this.lastTarget = null;
        this.lastTargetPos = null;
        this.alpha = 0.0F;
        this.lastFrameNanos = 0L;
        this.motes.clear();
        this.moteSprites.clear();
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
        int capacity = Math.max(byteSize + byteSize / 2, 32 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Target Circle Vertex Buffer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                capacity
        );
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private static boolean dead(LivingEntity entity) {
        return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
    }

    private static final class CircleMote {
        Vec3 pos;
        final Vec3 motion;
        final float size;
        final float maxLife;
        float age = 0.0F;

        CircleMote(Vec3 pos, Vec3 motion, float size, float maxLife) {
            this.pos = pos;
            this.motion = motion;
            this.size = size;
            this.maxLife = maxLife;
        }
    }
}