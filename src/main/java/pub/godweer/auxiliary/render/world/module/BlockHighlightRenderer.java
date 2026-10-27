package pub.godweer.auxiliary.render.world.module;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.kitty.module.impl.render.BlockHighlight;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.pipline.PiplinePost;
import pub.godweer.auxiliary.render.pipline.Post;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Create by daun kvass
 */
public final class BlockHighlightRenderer {
    private static final float BOX_EPSILON = 0.0025F;
    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();
    private static final int STYLE_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

    private final GpuBuffer transformUniforms = uniformBuffer("Block HighLight Transform UBO", TRANSFORM_SIZE);
    private final GpuBuffer styleUniforms = uniformBuffer("Block HighLight Style UBO", STYLE_SIZE);

    private BlockPos selectedPos;
    private BlockState selectedState;
    private long lastFrameNanos;

    private double currentX, currentY, currentZ;
    private float alpha = 0.0F;
    private boolean hasTarget = false;

    private GpuBuffer cachedVertexBuffer;
    private int cachedVertexCount;

    public void render(BlockHighlight module, CameraRenderState cameraState) {
        Minecraft mc = Minecraft.getInstance();

        long now = System.nanoTime();
        float deltaSeconds = (this.lastFrameNanos == 0L) ? 0.016F : Math.min(0.1F, (float) ((now - this.lastFrameNanos) / 1.0E9D));
        this.lastFrameNanos = now;

        boolean validHit = mc.level != null
                && mc.gameRenderer != null
                && cameraState != null
                && cameraState.initialized
                && (mc.hitResult instanceof BlockHitResult hit)
                && hit.getType() == HitResult.Type.BLOCK;

        BlockPos blockPos = validHit ? ((BlockHitResult) mc.hitResult).getBlockPos() : null;
        BlockState state = (validHit && blockPos != null) ? mc.level.getBlockState(blockPos) : null;

        if (state != null && state.isAir()) {
            validHit = false;
        }

        float animSpeed = module.animationSpeed.getValue();
        float factor = 1.0F - (float) Math.exp(-animSpeed * deltaSeconds);

        if (validHit) {
            this.alpha += (1.0F - this.alpha) * factor;

            if (!this.hasTarget || this.alpha < 0.05F) {
                this.currentX = blockPos.getX();
                this.currentY = blockPos.getY();
                this.currentZ = blockPos.getZ();
                this.hasTarget = true;
            } else {
                this.currentX += (blockPos.getX() - this.currentX) * factor;
                this.currentY += (blockPos.getY() - this.currentY) * factor;
                this.currentZ += (blockPos.getZ() - this.currentZ) * factor;
            }

            boolean selectionChanged = !blockPos.equals(this.selectedPos);
            if (selectionChanged || state != this.selectedState || this.cachedVertexBuffer == null) {
                this.selectedPos = blockPos.immutable();
                this.selectedState = state;
                rebuildMesh(mc, blockPos, state);
            }
        } else {
            this.alpha += (0.0F - this.alpha) * factor;
            if (this.alpha <= 0.005F) {
                this.hasTarget = false;
                resetSelection();
                return;
            }
        }

        if (this.cachedVertexBuffer == null || this.cachedVertexCount == 0) {
            return;
        }

        var target = mc.gameRenderer.mainRenderTarget();
        if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
            return;
        }

        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }

        Matrix4f modelViewProjection = projection
                .mul(cameraState.viewRotationMatrix)
                .translate(
                        (float) (this.currentX - cameraState.pos.x() + 0.5D),
                        (float) (this.currentY - cameraState.pos.y() + 0.5D),
                        (float) (this.currentZ - cameraState.pos.z() + 0.5D)
                )
                .translate(-0.5F, -0.5F, -0.5F);

        writeUniforms(module, modelViewProjection, target.width, target.height, this.alpha);

        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Block HighLight",
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty()
        );

        try {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(module)));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BlockHighLightTransform", this.transformUniforms);
            pass.setUniform("BlockHighLightStyle", this.styleUniforms);
            pass.setVertexBuffer(0, this.cachedVertexBuffer.slice());
            pass.draw(this.cachedVertexCount, 1, 0, 0);
        } finally {
            pass.close();
        }
    }

    private void rebuildMesh(Minecraft mc, BlockPos blockPos, BlockState state) {
        releaseMesh();
        List<AABB> boxes = state.getShape(mc.level, blockPos).toAabbs();
        if (boxes.isEmpty()) {
            boxes = List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }

        MeshData mesh = buildMesh(boxes);
        if (mesh == null) {
            return;
        }

        try (mesh) {
            this.cachedVertexCount = mesh.drawState().vertexCount();
            this.cachedVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Block HighLight Vertices",
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer()
            );
        }
    }

    private void releaseMesh() {
        if (this.cachedVertexBuffer != null) {
            this.cachedVertexBuffer.close();
            this.cachedVertexBuffer = null;
        }
        this.cachedVertexCount = 0;
    }

    public void resetSelection() {
        this.selectedPos = null;
        this.selectedState = null;
        this.lastFrameNanos = 0L;
        this.hasTarget = false;
        releaseMesh();
    }

    public void release() {
        resetSelection();
        this.transformUniforms.close();
        this.styleUniforms.close();
    }

    private void writeUniforms(BlockHighlight module, Matrix4f modelViewProjection, int width, int height, float alpha) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer transform = Std140Builder.onStack(stack, TRANSFORM_SIZE)
                    .putMat4f(modelViewProjection)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                    this.transformUniforms.slice(),
                    transform
            );

            int tint = module.getColors();
            ByteBuffer style = Std140Builder.onStack(stack, STYLE_SIZE)
                    .putVec4(ColorUtil.red(tint) / 255.0F, ColorUtil.green(tint) / 255.0F, ColorUtil.blue(tint) / 255.0F, alpha * 0.85F)
                    .putVec4((float) width, (float) height, Post.shaderTime() * module.shaderSpeed.getValue(), module.shaderIntensity.getValue())
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                    this.styleUniforms.slice(),
                    style
            );
        }
    }

    private RenderPipeline pipeline(BlockHighlight module) {
        boolean through = module.ignoreDepth.getValue();
        return switch (module.modes.getValue()) {
            case BlockHighlight.VARIANT_CAUSTICS -> through ? PiplinePost.BLOCK_HIGHLIGHT_CAUSTICS_THROUGH : PiplinePost.BLOCK_HIGHLIGHT_CAUSTICS;
            case BlockHighlight.VARIANT_DEEP_SPACE -> through ? PiplinePost.BLOCK_HIGHLIGHT_DEEP_SPACE_THROUGH : PiplinePost.BLOCK_HIGHLIGHT_DEEP_SPACE;
            default -> through ? PiplinePost.BLOCK_HIGHLIGHT_GLOSSY_THROUGH : PiplinePost.BLOCK_HIGHLIGHT_GLOSSY;
        };
    }

    private MeshData buildMesh(List<AABB> boxes) {
        int vertexCount = boxes.size() * 36;
        if (vertexCount == 0) {
            return null;
        }

        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(256, vertexCount * DefaultVertexFormat.POSITION.getVertexSize())),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION
        );

        for (AABB box : boxes) {
            float minX = (float) box.minX - BOX_EPSILON;
            float minY = (float) box.minY - BOX_EPSILON;
            float minZ = (float) box.minZ - BOX_EPSILON;
            float maxX = (float) box.maxX + BOX_EPSILON;
            float maxY = (float) box.maxY + BOX_EPSILON;
            float maxZ = (float) box.maxZ + BOX_EPSILON;

            face(builder, minX, minY, minZ, maxX, maxY, minZ);
            face(builder, maxX, minY, maxZ, minX, maxY, maxZ);
            face(builder, minX, minY, maxZ, minX, maxY, minZ);
            face(builder, maxX, minY, minZ, maxX, maxY, maxZ);
            face(builder, minX, maxY, minZ, maxX, maxY, maxZ);
            face(builder, minX, minY, maxZ, maxX, minY, minZ);
        }
        return builder.buildOrThrow();
    }

    private void face(BufferBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2) {
        boolean constantX = x1 == x2;
        boolean constantY = y1 == y2;
        if (constantX) {
            vertex(builder, x1, y1, z1);
            vertex(builder, x1, y1, z2);
            vertex(builder, x1, y2, z2);
            vertex(builder, x1, y2, z2);
            vertex(builder, x1, y2, z1);
            vertex(builder, x1, y1, z1);
        } else if (constantY) {
            vertex(builder, x1, y1, z1);
            vertex(builder, x2, y1, z1);
            vertex(builder, x2, y1, z2);
            vertex(builder, x2, y1, z2);
            vertex(builder, x1, y1, z2);
            vertex(builder, x1, y1, z1);
        } else {
            vertex(builder, x1, y1, z1);
            vertex(builder, x2, y1, z1);
            vertex(builder, x2, y2, z1);
            vertex(builder, x2, y2, z1);
            vertex(builder, x1, y2, z1);
            vertex(builder, x1, y1, z1);
        }
    }

    private void vertex(BufferBuilder builder, float x, float y, float z) {
        builder.addVertex(x, y, z);
    }

    private static GpuBuffer uniformBuffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
    }
}