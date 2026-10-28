package pub.godweer.util.target;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
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
import pub.godweer.module.impl.render.TargetEsp;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/** Cubes scattered around the target matching the reference layout. */
public final class HaloTargetRenderer {

    /**
     * Exact 3D layout around the target matching the reference screenshot:
     * { angleDeg, radiusOffsetFromHalfWidth, normalizedHeight (relative to bodyHeight), sizeBlocks, yawDeg, pitchDeg, rollDeg }
     *
     * Fits tightly around the target silhouette (within ~0.08..0.28 blocks from the hitbox edge)
     * from the ankles/floor up to head/shoulder level, with balanced left/right/front/back placement.
     */
    private static final float[][] CUBE_LAYOUT = {
            // 1. Top-Right (above/beside right shoulder & head)
            {  22.0F, 0.25F, 0.93F, 0.165F,  38.0F,  28.0F,  18.0F },
            // 2. Upper-Right (slightly behind right shoulder, medium-small)
            { -42.0F, 0.14F, 0.82F, 0.115F, 112.0F, -34.0F,  24.0F },
            // 3. Upper-Center-Left (in front of chest/collarbone, large)
            { 112.0F, 0.08F, 0.71F, 0.180F,  64.0F,  32.0F, -20.0F },
            // 4. Upper-Far-Left (left of shoulder/chest, large)
            { 166.0F, 0.18F, 0.68F, 0.170F, 148.0F, -26.0F,  30.0F },
            // 5. Mid-Right (in front of right arm/waist, large)
            {  56.0F, 0.08F, 0.38F, 0.185F,  25.0F,  36.0F, -28.0F },
            // 6. Mid-Left (left of waist/hip, medium-large)
            { 192.0F, 0.24F, 0.35F, 0.150F,  82.0F, -30.0F,  16.0F },
            // 7. Lower-Mid-Right outer (beside right thigh/knee, medium)
            {  14.0F, 0.15F, 0.22F, 0.125F, 134.0F,  22.0F, -35.0F },
            // 8. Lower-Mid-Right inner (tucked near right leg, small)
            { -55.0F, 0.06F, 0.16F, 0.095F,  48.0F, -40.0F,  12.0F },
            // 9. Lower-Left (near left shin/ankle, medium)
            { 154.0F, 0.12F, 0.07F, 0.120F, 165.0F,  28.0F, -22.0F },
            // 10. Bottom-Right (at right foot/floor level, medium-large)
            {  68.0F, 0.08F, -0.04F, 0.155F, 95.0F, -24.0F,  26.0F },
            // 11. Back-Upper-Left (behind head/neck for 360° camera consistency)
            { -128.0F, 0.14F, 0.86F, 0.140F, 52.0F,  30.0F, -18.0F },
            // 12. Back-Mid (behind torso for 360° camera consistency)
            { -95.0F, 0.12F, 0.48F, 0.150F, 120.0F, -28.0F,  32.0F }
    };

    private static final int CUBE_COUNT = CUBE_LAYOUT.length;
    private static final float BOB_AMOUNT = 0.032F;     // плавное вертикальное дыхание
    private static final float BOB_SPEED = 1.15F;
    private static final float ORBIT_SPEED = 0.18F;     // очень плавный дрейф вокруг цели
    private static final int FLASH_ARGB = 0xFFFA3444;
    private static final float FADE_SECONDS = 0.28F;
    private static final float MAX_DELTA_SECONDS = 0.05F;
    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/target_halo"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final float[] px = new float[8];
    private final float[] py = new float[8];
    private final float[] pz = new float[8];

    private float appear;
    private float bobTime;          // общий медленный таймер парения
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
        if (hasTarget) this.lastTarget = target;
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? (1.0F / 60.0F)
                : Math.min((now - this.lastFrameNanos) / 1.0E9F, MAX_DELTA_SECONDS);
        this.lastFrameNanos = now;
        float goal = hasTarget ? 1.0F : 0.0F;
        float step = dt / FADE_SECONDS;
        if (this.appear < goal) this.appear = Math.min(goal, this.appear + step);
        else this.appear = Math.max(goal, this.appear - step);
        float progress = eased(this.appear);
        // Кубы-ореол живут только пока цель активна в этом кадре — иначе они остаются
        // висеть на прошлой позиции и выглядят оторванными от моба.
        LivingEntity draw = target;
        if (!hasTarget || draw == null || progress <= 0.01F) {
            this.lastTarget = null;
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        if (Render3DUtil.levelProjectionCopy() == null) return;
        int color = module.getAccentColor();
        if (draw.hurtTime > 0) {
            float mix = Math.min(1.0F, Math.max(0.0F, draw.hurtTime - tickDelta) / 8.0F);
            int br = ColorUtil.red(color), bg = ColorUtil.green(color), bb = ColorUtil.blue(color);
            int fr = (FLASH_ARGB >> 16) & 0xFF, fg = (FLASH_ARGB >> 8) & 0xFF, fb = FLASH_ARGB & 0xFF;
            color = ColorUtil.pack(Math.round(br + (fr - br) * mix),
                    Math.round(bg + (fg - bg) * mix), Math.round(bb + (fb - bb) * mix),
                    ColorUtil.alpha(color));
        }
        color = ColorUtil.multiplyAlpha(color, progress);
        this.bobTime += dt * BOB_SPEED;
        Vec3 base = Render3DUtil.interpolatedPosition(draw, tickDelta);
        Vec3 camPos = camera.position();
        float bodyHeight = draw.getBbHeight();
        float bodyWidth = draw.getBbWidth();
        int bytes = Math.max(4096, CUBE_COUNT * 36 * DefaultVertexFormat.POSITION_COLOR.getVertexSize());
        this.meshBuilder = new BufferBuilder(ByteBufferBuilder.exactlySized(bytes),
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        this.triangleCount = 0;
        scatter(base, camPos, color, bodyHeight, bodyWidth, progress);
        if (this.triangleCount > 0) submit(camera);
    }

    private void scatter(Vec3 base, Vec3 cam, int color, float bodyHeight, float bodyWidth, float progress) {
        int baseR = ColorUtil.red(color), baseG = ColorUtil.green(color), baseB = ColorUtil.blue(color);
        double halfBodyWidth = bodyWidth * 0.5D;
        float orbitOffsetRad = this.bobTime * ORBIT_SPEED;

        for (int i = 0; i < CUBE_LAYOUT.length; i++) {
            float[] spec = CUBE_LAYOUT[i];
            float angleDeg = spec[0];
            float radiusOffset = spec[1];
            float normHeight = spec[2];
            float cubeSize = spec[3];
            float yaw = spec[4] + this.bobTime * (i % 2 == 0 ? 12.0F : -12.0F);
            float pitch = spec[5] + this.bobTime * (i % 3 == 0 ? -9.0F : 9.0F);
            float roll = spec[6] + this.bobTime * 8.0F;

            // Плотное расположение вокруг силуэта цели (0.06..0.25 блока от края хитбокса)
            double radius = (halfBodyWidth + radiusOffset) * (0.85D + 0.15D * progress);
            double angle = Math.toRadians(angleDeg) + orbitOffsetRad * (i % 2 == 0 ? 1.0F : -0.7F);
            float x = (float) (Math.cos(angle) * radius);
            float z = (float) (Math.sin(angle) * radius);

            // Высота строго вдоль силуэта цели: от стоп (-0.04*H) до уровня макушки (0.93*H)
            float phase = i * 1.15F;
            float y = normHeight * bodyHeight + (float) Math.sin(this.bobTime + phase) * BOB_AMOUNT;

            int alpha = Math.round(ColorUtil.alpha(color) * (0.78F + 0.22F * ((i % 3) / 2.0F)));
            float half = (cubeSize * 0.5F) * progress;
            cube(base, cam, x, y, z, half, yaw, pitch, roll, baseR, baseG, baseB, alpha);
        }
    }

    private void cube(Vec3 base, Vec3 cam, float cx, float cy, float cz, float half,
                      float yawDeg, float pitchDeg, float rollDeg, int r, int g, int b, int alpha) {
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float roll = (float) Math.toRadians(rollDeg);
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        float cpt = (float) Math.cos(pitch), spt = (float) Math.sin(pitch);
        float crl = (float) Math.cos(roll), srl = (float) Math.sin(roll);
        for (int i = 0; i < 8; i++) {
            float lx = (i & 1) != 0 ? half : -half;
            float ly = (i & 2) != 0 ? half : -half;
            float lz = (i & 4) != 0 ? half : -half;
            // Roll (Z)
            float x1 = lx * crl - ly * srl;
            float y1 = lx * srl + ly * crl;
            // Pitch (X)
            float y2 = y1 * cpt - lz * spt;
            float z2 = y1 * spt + lz * cpt;
            // Yaw (Y)
            float rx = x1 * cyw + z2 * syw;
            float rz = -x1 * syw + z2 * cyw;
            px[i] = cx + rx; py[i] = cy + y2; pz[i] = cz + rz;
        }
        quad(base, cam, 4, 5, 7, 6, r, g, b, alpha, 1.05F);
        quad(base, cam, 1, 0, 2, 3, r, g, b, alpha, 0.76F);
        quad(base, cam, 5, 1, 3, 7, r, g, b, alpha, 0.90F);
        quad(base, cam, 0, 4, 6, 2, r, g, b, alpha, 0.88F);
        quad(base, cam, 2, 6, 7, 3, r, g, b, alpha, 1.22F);
        quad(base, cam, 0, 1, 5, 4, r, g, b, alpha, 0.65F);
    }

    private void quad(Vec3 base, Vec3 cam, int a, int b, int c, int d,
                      int r, int g, int bl, int alpha, float shade) {
        int sr = Mth.clamp(Math.round(r * shade), 0, 255);
        int sg = Mth.clamp(Math.round(g * shade), 0, 255);
        int sb = Mth.clamp(Math.round(bl * shade), 0, 255);
        tri(base, cam, a, b, c, sr, sg, sb, alpha);
        tri(base, cam, a, c, d, sr, sg, sb, alpha);
    }

    private void tri(Vec3 base, Vec3 cam, int a, int b, int c, int r, int g, int bl, int alpha) {
        if (alpha <= 0) return;
        BufferBuilder builder = this.meshBuilder;
        if (builder == null) return;
        builder.addVertex((float) (base.x + px[a] - cam.x), (float) (base.y + py[a] - cam.y), (float) (base.z + pz[a] - cam.z)).setColor(ColorUtil.pack(r, g, bl, alpha));
        builder.addVertex((float) (base.x + px[b] - cam.x), (float) (base.y + py[b] - cam.y), (float) (base.z + pz[b] - cam.z)).setColor(ColorUtil.pack(r, g, bl, alpha));
        builder.addVertex((float) (base.x + px[c] - cam.x), (float) (base.y + py[c] - cam.y), (float) (base.z + pz[c] - cam.z)).setColor(ColorUtil.pack(r, g, bl, alpha));
        this.triangleCount++;
    }

    private static float eased(float v) {
        float k = Mth.clamp(v, 0.0F, 1.0F);
        return k < 0.5F ? 4.0F * k * k * k : 1.0F - (float) (Math.pow(-2.0F * k + 2.0F, 3.0D) / 2.0D);
    }

    private static float noise(int index, int salt) {
        int h = index * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
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
            if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) return;
            ensureVertexCapacity(byteCount);
            device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, byteCount), vertexData);
            Matrix4f mvp = new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));
            ensureTransformBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(mvp).get();
                device.createCommandEncoder().writeToBuffer(this.transformBuffer.slice(), data);
            }
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer Target Cubes",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Projection", this.transformBuffer);
                pass.setVertexBuffer(0, this.vertexBuffer.slice(0, byteCount));
                pass.draw(vertexCount, 1, 0, 0);
            }
        } finally {
            meshBuilder = null;
            if (mesh != null) mesh.close();
        }
    }

    private void ensureTransformBuffer() {
        if (this.transformBuffer != null) return;
        this.transformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Target Cubes UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, TRANSFORM_SIZE);
    }

    private void ensureVertexCapacity(int byteSize) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Target Cubes Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
    }

    public void release() {
        this.appear = 0.0F; this.bobTime = 0.0F;
        this.lastFrameNanos = 0L; this.lastTarget = null;
        this.triangleCount = 0; this.meshBuilder = null;
        if (this.vertexBuffer != null) { this.vertexBuffer.close(); this.vertexBuffer = null; }
        if (this.transformBuffer != null) { this.transformBuffer.close(); this.transformBuffer = null; }
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }
}
