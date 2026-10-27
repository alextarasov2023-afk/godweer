package pub.godweer.auxiliary.client.target;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.auxiliary.client.Annstable;
import pub.godweer.auxiliary.render.Render3DUtil;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Create by daun kvass
 */
public final class AuraMarkerRenderer {
    public static final Identifier TARGET_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/world/target.png");
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final BindGroupLayout MARKER_LAYOUT = BindGroupLayout.builder()
            .withUniform("texSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("params", UniformType.UNIFORM_BUFFER)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("godweer", "pipeline/world/aura_marker"))
            .withVertexShader(Identifier.fromNamespaceAndPath("godweer", "core/aura_marker"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("godweer", "core/aura_marker"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(MARKER_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
            .withCull(false)
            .build();

    private GpuBuffer paramsBuffer;
    private LivingEntity lastTarget;
    private float alpha;
    private long lastFrameTime;

    public void render(LivingEntity activeTarget, float tickDelta, int baseColor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) {
            reset();
            return;
        }

        updateAnimation(activeTarget);
        LivingEntity target = lastTarget;
        if (target == null || alpha <= 0.01F) {
            return;
        }

        MarkerGeometry geometry = markerGeometry(mc, target, tickDelta);
        if (geometry != null) {
            renderMarker(mc, geometry, Annstable.blend(baseColor, target, alpha));
        }
    }

    public void reset() {
        lastTarget = null;
        alpha = 0.0F;
        lastFrameTime = 0L;
    }

    private void updateAnimation(LivingEntity activeTarget) {
        long now = System.currentTimeMillis();
        float delta = lastFrameTime == 0L ? 0.016F : Math.min(100L, now - lastFrameTime) / 1000.0F;
        lastFrameTime = now;

        if (valid(activeTarget)) {
            lastTarget = activeTarget;
        }

        float targetAlpha = valid(activeTarget) ? 0.5F : 0.0F;
        float step = Mth.clamp(delta * 4.0F, 0.0F, 1.0F);
        alpha += (targetAlpha - alpha) * step;
    }

    private MarkerGeometry markerGeometry(Minecraft mc, LivingEntity target, float tickDelta) {
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) {
            return null;
        }

        Vec3 position = Render3DUtil.interpolatedPosition(target, tickDelta);
        float widthScale = (target.getBbWidth() < 1.0F) ? 0.95F : (target.getBbWidth() > 2.0F ? 1.45F : 1.0F);
        float halfSize = 0.5F * widthScale * alpha * Annstable.scale(target, 0.12F);

        if (halfSize <= 0.001F) {
            return null;
        }

        float rotationZ = (float) ((System.currentTimeMillis() % 360000L) / 1000.0D * 120.0D % 360.0D);

        Matrix4f pose = Render3DUtil.buildBillboardPose(
                camera,
                position,
                target.getBbHeight() / 2.0D,
                rotationZ
        );
        return new MarkerGeometry(pose, halfSize);
    }

    private void renderMarker(Minecraft mc, MarkerGeometry geometry, int color) {
        var renderTarget = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = renderTarget != null ? renderTarget.getColorTextureView() : null;
        if (colorView == null) return;

        AbstractTexture texture = mc.getTextureManager().getTexture(TARGET_TEXTURE);
        GpuTextureView textureView = texture != null ? texture.getTextureView() : null;
        if (textureView == null) return;

        ensureParamsBuffer();
        writeParams(color);

        MeshData mesh = buildMesh(geometry);
        GpuBuffer vertexBuffer = null;
        try {
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Godweer Aura Marker Vertices",
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer()
            );

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Godweer Aura Marker Pass",
                    colorView,
                    Optional.empty()
            )) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("params", paramsBuffer);
                pass.setUniform("texSampler", textureView, sampler);
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(4, 1, 0, 0);
            }
        } finally {
            if (vertexBuffer != null) vertexBuffer.close();
            mesh.close();
        }
    }

    private MeshData buildMesh(MarkerGeometry geometry) {
        float halfSize = geometry.halfSize;
        BufferBuilder builder = new BufferBuilder(
                ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize()),
                PrimitiveTopology.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_TEX
        );
        builder.addVertex(geometry.pose, -halfSize, -halfSize, 0.0F).setUv(0.0F, 1.0F);
        builder.addVertex(geometry.pose, -halfSize, halfSize, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(geometry.pose, halfSize, -halfSize, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(geometry.pose, halfSize, halfSize, 0.0F).setUv(1.0F, 0.0F);
        return builder.buildOrThrow();
    }

    private void ensureParamsBuffer() {
        if (paramsBuffer == null) {
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Godweer Aura Marker UBO",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
        }
    }

    private void writeParams(int color) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(
                            ((color >> 16) & 0xFF) / 255.0F,
                            ((color >> 8) & 0xFF) / 255.0F,
                            (color & 0xFF) / 255.0F,
                            ((color >> 24) & 0xFF) / 255.0F
                    )
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(paramsBuffer.slice(), data);
        }
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    public void release() {
        if (this.paramsBuffer != null) {
            this.paramsBuffer.close();
            this.paramsBuffer = null;
        }
    }

    private record MarkerGeometry(Matrix4f pose, float halfSize) {}
}