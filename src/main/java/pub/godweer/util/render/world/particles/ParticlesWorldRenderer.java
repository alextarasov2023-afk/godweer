package pub.godweer.util.render.world.particles;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.util.render.Render3DUtil;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class ParticlesWorldRenderer {
    public static final Identifier BLOOM_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/world/bloom.png");
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final int PROJECTION_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("BloomSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("WorldParticleUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("godweer", "pipeline/world/particles_world"))
            .withVertexShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline THROUGH_WALLS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("godweer", "pipeline/world/particles_world_through_walls"))
            .withVertexShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline ALPHA_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("godweer", "pipeline/world/particles_world_alpha"))
            .withVertexShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline ALPHA_THROUGH_WALLS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("godweer", "pipeline/world/particles_world_alpha_through_walls"))
            .withVertexShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("godweer", "core/particles_world"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public record Sprite(Vec3 position, float halfSize, int color) {
    }

    public void render(List<Sprite> sprites, float brightness) {
        render(sprites, brightness, false, true);
    }

    public void render(List<Sprite> sprites, float brightness, boolean throughWalls) {
        render(sprites, brightness, throughWalls, true);
    }

    public void render(List<Sprite> sprites, float brightness, boolean throughWalls, boolean additive) {
        Minecraft mc = Minecraft.getInstance();
        if (sprites.isEmpty() || mc.level == null || mc.gameRenderer == null) {
            return;
        }

        var target = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
        GpuTextureView depthView = target != null ? target.getDepthTextureView() : null;
        if (colorView == null) {
            return;
        }

        AbstractTexture bloom = mc.getTextureManager().getTexture(BLOOM_TEXTURE);
        GpuTextureView bloomView = bloom != null ? bloom.getTextureView() : null;
        if (bloomView == null) {
            return;
        }

        CameraRenderState camState = Render3DUtil.cameraRenderState(mc);
        MeshData meshData = buildMesh(mc, sprites, camState);
        if (meshData == null) {
            return;
        }

        try {
            drawPass(sprites.size(), brightness, throughWalls, additive, camState, colorView, depthView,
                    bloomView, meshData);
        } finally {
            meshData.close();
        }
    }

    private void drawPass(int spriteCount, float brightness, boolean throughWalls, boolean additive,
                          CameraRenderState camState, GpuTextureView colorView, GpuTextureView depthView,
                          GpuTextureView bloomView, MeshData meshData) {
        var device = RenderSystem.getDevice();
        GpuBuffer vertexBuffer = device.createBuffer(
                () -> "Godweer World Particles Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                meshData.vertexBuffer()
        );
        GpuBuffer uniformBuffer = null;
        GpuBuffer projectionBuffer = null;
        try {
            uniformBuffer = uploadUniform(brightness);
            projectionBuffer = uploadProjection(camState);

            RenderPipeline pipeline = additive
                    ? (throughWalls ? THROUGH_WALLS_PIPELINE : PIPELINE)
                    : (throughWalls ? ALPHA_THROUGH_WALLS_PIPELINE : ALPHA_PIPELINE);
            try (RenderPass pass = createPass(colorView, depthView, throughWalls)) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                pass.setUniform("Projection", projectionBuffer != null
                        ? projectionBuffer.slice()
                        : RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("WorldParticleUniforms", uniformBuffer);
                pass.setUniform("BloomSampler", bloomView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(spriteCount * 6, 1, 0, 0);
            }
        } finally {
            if (projectionBuffer != null) {
                projectionBuffer.close();
            }
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            vertexBuffer.close();
        }
    }

    private RenderPass createPass(GpuTextureView colorView, GpuTextureView depthView, boolean throughWalls) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (!throughWalls && depthView != null) {
            return encoder.createRenderPass(() -> "Godweer World Particles Pass",
                    colorView, Optional.empty(), depthView, OptionalDouble.empty());
        }
        return encoder.createRenderPass(() -> "Godweer World Particles Pass", colorView, Optional.empty());
    }

    private GpuBuffer uploadUniform(float brightness) {
        var device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                () -> "Godweer World Particles UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(brightness, 0.0F, 0.0F, 0.0F)
                    .get();
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }

    private GpuBuffer uploadProjection(CameraRenderState camState) {
        Matrix4f projection = camState != null && camState.projectionMatrix != null
                ? new Matrix4f(camState.projectionMatrix)
                : Render3DUtil.levelProjectionCopy();
        if (projection == null) return null;

        var device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                () -> "Godweer World Particles Projection UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                PROJECTION_SIZE
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, PROJECTION_SIZE).putMat4f(projection).get();
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }

    private MeshData buildMesh(Minecraft mc, List<Sprite> sprites, CameraRenderState camState) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camState != null ? camState.pos : camera.position();
        Matrix4f pose = camState != null
                ? new Matrix4f(camState.viewRotationMatrix)
                : Render3DUtil.cameraViewPose(camera);

        BufferBuilder builder = new BufferBuilder(
                ByteBufferBuilder.exactlySized(sprites.size() * 6 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR
        );
        for (Sprite sprite : sprites) {
            Vector4f center = Render3DUtil.toViewSpace(sprite.position(), cameraPos, pose);
            float half = sprite.halfSize();
            int color = sprite.color();

            addVertex(builder, center, -half, -half, 0.0F, 0.0F, color);
            addVertex(builder, center, -half, half, 0.0F, 1.0F, color);
            addVertex(builder, center, half, half, 1.0F, 1.0F, color);
            addVertex(builder, center, -half, -half, 0.0F, 0.0F, color);
            addVertex(builder, center, half, half, 1.0F, 1.0F, color);
            addVertex(builder, center, half, -half, 1.0F, 0.0F, color);
        }
        return builder.build();
    }

    private void addVertex(BufferBuilder builder, Vector4f center, float dx, float dy, float u, float v, int color) {
        builder.addVertex(center.x + dx, center.y + dy, center.z)
                .setUv(u, v)
                .setColor(color);
    }
}