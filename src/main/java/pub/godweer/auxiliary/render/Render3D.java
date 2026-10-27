package pub.godweer.auxiliary.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Create by daun kvass
 */
public final class Render3D {

    private static final Minecraft mc = Minecraft.getInstance();

    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();
    private static GpuBuffer transformUniform;
    private static GpuBuffer vertexGpuBuffer;
    private static final RenderPipeline PIPELINE_FILL_DEPTH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world3d/fill_depth"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();
    private static final RenderPipeline PIPELINE_FILL_THROUGH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world3d/fill_through"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline PIPELINE_OUTLINE_DEPTH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world3d/outline_depth"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withCull(false)
            .build();

    private static final RenderPipeline PIPELINE_OUTLINE_THROUGH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world3d/outline_through"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withCull(false)
            .build();

    private Render3D() {}


    public static void drawBox(BlockPos pos, Color fillCol, Color outCol, boolean fill, boolean outline) {
        drawBox(pos, fillCol, outCol, fill, outline, false);
    }

    public static void drawBox(BlockPos pos, Color fillCol, Color outCol, boolean fill, boolean outline, boolean throughWalls) {
        drawBox(pos.getX(), pos.getY(), pos.getZ(), 1.0, 1.0, 1.0, toArgb(fillCol), toArgb(outCol), fill, outline, throughWalls);
    }

    public static void drawBox(AABB box, Color fillCol, Color outCol, boolean fill, boolean outline) {
        drawBox(box, fillCol, outCol, fill, outline, false);
    }

    public static void drawBox(AABB box, Color fillCol, Color outCol, boolean fill, boolean outline, boolean throughWalls) {
        drawBox(box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ,
                toArgb(fillCol), toArgb(outCol), fill, outline, throughWalls);
    }


    public static void drawTile(BlockPos pos, Color fillCol, Color outCol, boolean fill, boolean outline) {
        drawTile(pos, fillCol, outCol, fill, outline, false);
    }

    public static void drawTile(BlockPos pos, Color fillCol, Color outCol, boolean fill, boolean outline, boolean throughWalls) {
        drawTile(pos.getX(), pos.getY() + 0.005, pos.getZ(), 1.0, 1.0, toArgb(fillCol), toArgb(outCol), fill, outline, throughWalls);
    }

    public static void drawLine(Vec3 from, Vec3 to, Color color, boolean throughWalls) {
        if (color == null || color.getAlpha() == 0) return;
        int argb = toArgb(color);

        Matrix4f mvp = buildMVP(from.x, from.y, from.z);
        if (mvp == null) return;

        dispatch(throughWalls ? PIPELINE_OUTLINE_THROUGH : PIPELINE_OUTLINE_DEPTH, PrimitiveTopology.LINES, 2, mvp, builder -> {
            float dx = (float) (to.x - from.x);
            float dy = (float) (to.y - from.y);
            float dz = (float) (to.z - from.z);
            builder.addVertex(0, 0, 0).setColor(argb);
            builder.addVertex(dx, dy, dz).setColor(argb);
        });
    }

    private static void drawBox(double x, double y, double z, double sx, double sy, double sz,
                                int fillCol, int outCol, boolean fill, boolean outline, boolean throughWalls) {
        Matrix4f mvp = buildMVP(x, y, z);
        if (mvp == null) return;

        float w = (float) sx, h = (float) sy, d = (float) sz;

        if (fill && (fillCol >>> 24) > 0) {
            dispatch(throughWalls ? PIPELINE_FILL_THROUGH : PIPELINE_FILL_DEPTH, PrimitiveTopology.TRIANGLES, 36, mvp, b -> {
                quad(b, 0, 0, 0,  w, 0, 0,  w, 0, d,  0, 0, d, fillCol);
                quad(b, 0, h, 0,  0, h, d,  w, h, d,  w, h, 0, fillCol);
                quad(b, 0, 0, 0,  0, h, 0,  w, h, 0,  w, 0, 0, fillCol);
                quad(b, 0, 0, d,  w, 0, d,  w, h, d,  0, h, d, fillCol);
                quad(b, 0, 0, 0,  0, 0, d,  0, h, d,  0, h, 0, fillCol);
                quad(b, w, 0, 0,  w, h, 0,  w, h, d,  w, 0, d, fillCol);
            });
        }

        if (outline && (outCol >>> 24) > 0) {
            dispatch(throughWalls ? PIPELINE_OUTLINE_THROUGH : PIPELINE_OUTLINE_DEPTH, PrimitiveTopology.LINES, 24, mvp, b -> {
                line(b, 0, 0, 0, w, 0, 0, outCol);
                line(b, w, 0, 0, w, 0, d, outCol);
                line(b, w, 0, d, 0, 0, d, outCol);
                line(b, 0, 0, d, 0, 0, 0, outCol);
                line(b, 0, h, 0, w, h, 0, outCol);
                line(b, w, h, 0, w, h, d, outCol);
                line(b, w, h, d, 0, h, d, outCol);
                line(b, 0, h, d, 0, h, 0, outCol);
                line(b, 0, 0, 0, 0, h, 0, outCol);
                line(b, w, 0, 0, w, h, 0, outCol);
                line(b, w, 0, d, w, h, d, outCol);
                line(b, 0, 0, d, 0, h, d, outCol);
            });
        }
    }

    private static void drawTile(double x, double y, double z, double sx, double sz,
                                 int fillCol, int outCol, boolean fill, boolean outline, boolean throughWalls) {
        Matrix4f mvp = buildMVP(x, y, z);
        if (mvp == null) return;

        float w = (float) sx, d = (float) sz;

        if (fill && (fillCol >>> 24) > 0) {
            dispatch(throughWalls ? PIPELINE_FILL_THROUGH : PIPELINE_FILL_DEPTH, PrimitiveTopology.TRIANGLES, 6, mvp, b -> {
                quad(b, 0, 0, 0, 0, 0, d, w, 0, d, w, 0, 0, fillCol);
            });
        }

        if (outline && (outCol >>> 24) > 0) {
            dispatch(throughWalls ? PIPELINE_OUTLINE_THROUGH : PIPELINE_OUTLINE_DEPTH, PrimitiveTopology.LINES, 8, mvp, b -> {
                line(b, 0, 0, 0, w, 0, 0, outCol);
                line(b, w, 0, 0, w, 0, d, outCol);
                line(b, w, 0, d, 0, 0, d, outCol);
                line(b, 0, 0, d, 0, 0, 0, outCol);
            });
        }
    }


    private static Matrix4f buildMVP(double worldX, double worldY, double worldZ) {
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null || mc.gameRenderer == null) {
            return null;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        Matrix4f viewRotation = camera.getViewRotationMatrix(new Matrix4f());

        return new Matrix4f(projection)
                .mul(viewRotation)
                .translate(
                        (float) (worldX - camPos.x),
                        (float) (worldY - camPos.y),
                        (float) (worldZ - camPos.z)
                );
    }

    private static void line(BufferBuilder b, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        b.addVertex(x1, y1, z1).setColor(color);
        b.addVertex(x2, y2, z2).setColor(color);
    }

    private static void quad(BufferBuilder b,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             int color) {
        b.addVertex(x1, y1, z1).setColor(color);
        b.addVertex(x2, y2, z2).setColor(color);
        b.addVertex(x3, y3, z3).setColor(color);

        b.addVertex(x1, y1, z1).setColor(color);
        b.addVertex(x3, y3, z3).setColor(color);
        b.addVertex(x4, y4, z4).setColor(color);
    }

    private static int toArgb(Color color) {
        if (color == null) return 0;
        return (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }


    @FunctionalInterface
    private interface GeometryBuilder {
        void build(BufferBuilder builder);
    }

    private static void dispatch(RenderPipeline pipeline, PrimitiveTopology topology, int expectedVertices,
                                 Matrix4f mvp, GeometryBuilder geometryBuilder) {
        var target = mc.gameRenderer.mainRenderTarget();
        if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) return;

        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(expectedVertices * DefaultVertexFormat.POSITION_COLOR.getVertexSize(), 512)),
                topology,
                DefaultVertexFormat.POSITION_COLOR
        );

        geometryBuilder.build(builder);
        MeshData mesh = builder.buildOrThrow();

        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = mesh.vertexBuffer();
            int remainingBytes = vertexData.remaining();
            int vertexCount = mesh.drawState().vertexCount();

            if (vertexCount == 0) return;

            ensureVertexCapacity(remainingBytes);
            device.createCommandEncoder().writeToBuffer(vertexGpuBuffer.slice(0, remainingBytes), vertexData);

            ensureUniformCapacity();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer transformData = Std140Builder.onStack(stack, TRANSFORM_SIZE)
                        .putMat4f(mvp)
                        .get();
                device.createCommandEncoder().writeToBuffer(transformUniform.slice(), transformData);
            }

            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer 3D World Render",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                pass.setUniform("Projection", transformUniform);
                pass.setVertexBuffer(0, vertexGpuBuffer.slice(0, remainingBytes));
                pass.draw(vertexCount, 1, 0, 0);
            }
        } finally {
            mesh.close();
        }
    }

    private static void ensureUniformCapacity() {
        if (transformUniform != null) return;
        transformUniform = RenderSystem.getDevice().createBuffer(
                () -> "Godweer World3D Transform UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                TRANSFORM_SIZE
        );
    }

    private static void ensureVertexCapacity(int byteSize) {
        if (vertexGpuBuffer != null && vertexGpuBuffer.size() >= byteSize) return;
        if (vertexGpuBuffer != null) vertexGpuBuffer.close();
        vertexGpuBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer World3D Vertex Buffer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                Math.max(byteSize + 1024, 64 * 1024)
        );
    }
}