package pub.godweer.util.render;

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
import java.util.List;
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


    /** Box in world coordinates for a batched pass; sizes are 1.0 for a single block. */
    public record WorldBox(double x, double y, double z,
                           double width, double height, double depth,
                           Color fill, Color outline) {

        public static WorldBox ofBlock(int x, int y, int z, Color fill, Color outline) {
            return new WorldBox(x, y, z, 1.0, 1.0, 1.0, fill, outline);
        }
    }


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

    /**
     * Заливка + каркас на весь список. drawBox() на каждый блок непригоден для ESP:
     * это по два render pass на рамку, а сотню блоков так не вывезти.
     */
    public static void drawBoxes(List<WorldBox> boxes, boolean throughWalls) {
        drawBoxFills(boxes, throughWalls, 1.0F, 0.0F, 0.0F);
        drawBoxOutlines(boxes, throughWalls, 1.0F, 0.0F, 0.0F);
    }

    public static void drawBoxFills(List<WorldBox> boxes, boolean throughWalls,
                                    float alphaScale, float fadeNear, float fadeFar) {
        Matrix4f mvp = batchMvp();
        if (mvp == null || boxes.isEmpty()) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        dispatch(throughWalls ? PIPELINE_FILL_THROUGH : PIPELINE_FILL_DEPTH, PrimitiveTopology.TRIANGLES,
                boxes.size() * 36, mvp, b -> {
                    for (WorldBox box : boxes) {
                        float x = (float) (box.x() - cam.x);
                        float y = (float) (box.y() - cam.y);
                        float z = (float) (box.z() - cam.z);
                        float w = (float) box.width();
                        float h = (float) box.height();
                        float d = (float) box.depth();
                        int col = scaleAlpha(toArgb(box.fill()),
                                fade(x + w * 0.5F, y + h * 0.5F, z + d * 0.5F, alphaScale, fadeNear, fadeFar));
                        if ((col >>> 24) == 0) continue;
                        boxFaces(b, x, y, z, w, h, d, col);
                    }
                });
    }

    public static void drawBoxOutlines(List<WorldBox> boxes, boolean throughWalls,
                                       float alphaScale, float fadeNear, float fadeFar) {
        Matrix4f mvp = batchMvp();
        if (mvp == null || boxes.isEmpty()) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        dispatch(throughWalls ? PIPELINE_OUTLINE_THROUGH : PIPELINE_OUTLINE_DEPTH, PrimitiveTopology.LINES,
                boxes.size() * 24, mvp, b -> {
                    for (WorldBox box : boxes) {
                        float x = (float) (box.x() - cam.x);
                        float y = (float) (box.y() - cam.y);
                        float z = (float) (box.z() - cam.z);
                        float w = (float) box.width();
                        float h = (float) box.height();
                        float d = (float) box.depth();
                        int col = scaleAlpha(toArgb(box.outline()),
                                fade(x + w * 0.5F, y + h * 0.5F, z + d * 0.5F, alphaScale, fadeNear, fadeFar));
                        if ((col >>> 24) == 0) continue;
                        boxEdges(b, x, y, z, w, h, d, col);
                    }
                });
    }

    /** Только углы куба, по три отрезка из каждого угла: читается лучше, чем весь каркас. */
    public static void drawBoxCorners(List<WorldBox> boxes, boolean throughWalls, float alphaScale,
                                      float fadeNear, float fadeFar, float corner) {
        Matrix4f mvp = batchMvp();
        if (mvp == null || boxes.isEmpty()) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        dispatch(throughWalls ? PIPELINE_OUTLINE_THROUGH : PIPELINE_OUTLINE_DEPTH, PrimitiveTopology.LINES,
                boxes.size() * 24, mvp, b -> {
                    for (WorldBox box : boxes) {
                        float x = (float) (box.x() - cam.x);
                        float y = (float) (box.y() - cam.y);
                        float z = (float) (box.z() - cam.z);
                        float w = (float) box.width();
                        float h = (float) box.height();
                        float d = (float) box.depth();
                        int col = scaleAlpha(toArgb(box.outline()),
                                fade(x + w * 0.5F, y + h * 0.5F, z + d * 0.5F, alphaScale, fadeNear, fadeFar));
                        if ((col >>> 24) == 0) continue;
                        boxCorners(b, x, y, z, w, h, d, col, corner);
                    }
                });
    }

    /**
     * Мягкое свечение: три раздутые оболочки куба с убывающей альфой. Одна заливка на
     * весь список, поэтому «bloom» бесплатен даже на несколько сотен блоков.
     */
    public static void drawBoxGlow(List<WorldBox> boxes, boolean throughWalls,
                                   float alphaScale, float fadeNear, float fadeFar) {
        Matrix4f mvp = batchMvp();
        if (mvp == null || boxes.isEmpty()) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        dispatch(throughWalls ? PIPELINE_FILL_THROUGH : PIPELINE_FILL_DEPTH, PrimitiveTopology.TRIANGLES,
                boxes.size() * GROWTH.length * 36, mvp, b -> {
                    for (WorldBox box : boxes) {
                        float x = (float) (box.x() - cam.x);
                        float y = (float) (box.y() - cam.y);
                        float z = (float) (box.z() - cam.z);
                        float w = (float) box.width();
                        float h = (float) box.height();
                        float d = (float) box.depth();
                        float base = fade(x + w * 0.5F, y + h * 0.5F, z + d * 0.5F, alphaScale, fadeNear, fadeFar);
                        int argb = toArgb(box.outline());
                        if ((argb >>> 24) == 0) continue;

                        for (int shell = 0; shell < GROWTH.length; shell++) {
                            float grow = GROWTH[shell];
                            boxFaces(b, x - grow, y - grow, z - grow,
                                    w + grow * 2.0F, h + grow * 2.0F, d + grow * 2.0F,
                                    scaleAlpha(argb, base * SHELL_ALPHA[shell]));
                        }
                    }
                });
    }

    private static final float[] GROWTH = {0.09F, 0.2F, 0.34F};
    private static final float[] SHELL_ALPHA = {0.55F, 0.3F, 0.15F};

    /**
     * Матрица для батчей. Вершины списка уже отсчитываются от камеры
     * (box.x() - cam.x), поэтому матрица не должна сдвигать мир ещё раз —
     * иначе весь ESP уезжает за пределы экрана и не рисуется.
     */
    private static Matrix4f batchMvp() {
        if (mc.gameRenderer == null) return null;
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) return null;

        Camera camera = mc.gameRenderer.mainCamera();
        return new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));
    }

    /** Затухание по расстоянию: после fadeFar остаётся 8% видимости, чтобы блок не исчезал резко. */
    private static float fade(float cx, float cy, float cz, float scale, float fadeNear, float fadeFar) {
        if (fadeFar <= fadeNear) return scale;
        float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        return scale * Math.max(0.08F, 1.0F - (dist - fadeNear) / (fadeFar - fadeNear));
    }

    private static int scaleAlpha(int argb, float scale) {
        if (scale >= 0.999F) return argb;
        if (scale <= 0.001F) return 0;
        int alpha = Math.min(255, (int) ((argb >>> 24) * scale));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static void boxCorners(BufferBuilder b, float x, float y, float z,
                                   float w, float h, float d, int col, float corner) {
        float s = corner * Math.min(w, Math.min(h, d));
        float xe = x + w, ye = y + h, ze = z + d;

        corner(b, x, y, z, s, s, s, col);
        corner(b, xe, y, z, -s, s, s, col);
        corner(b, x, ye, z, s, -s, s, col);
        corner(b, xe, ye, z, -s, -s, s, col);
        corner(b, x, y, ze, s, s, -s, col);
        corner(b, xe, y, ze, -s, s, -s, col);
        corner(b, x, ye, ze, s, -s, -s, col);
        corner(b, xe, ye, ze, -s, -s, -s, col);
    }

    private static void corner(BufferBuilder b, float px, float py, float pz,
                               float sx, float sy, float sz, int col) {
        line(b, px, py, pz, px + sx, py, pz, col);
        line(b, px, py, pz, px, py + sy, pz, col);
        line(b, px, py, pz, px, py, pz + sz, col);
    }

    private static void drawBox(double x, double y, double z, double sx, double sy, double sz,
                                int fillCol, int outCol, boolean fill, boolean outline, boolean throughWalls) {
        Matrix4f mvp = buildMVP(x, y, z);
        if (mvp == null) return;

        float w = (float) sx, h = (float) sy, d = (float) sz;

        if (fill && (fillCol >>> 24) > 0) {
            dispatch(throughWalls ? PIPELINE_FILL_THROUGH : PIPELINE_FILL_DEPTH, PrimitiveTopology.TRIANGLES, 36, mvp,
                    b -> boxFaces(b, 0, 0, 0, w, h, d, fillCol));
        }

        if (outline && (outCol >>> 24) > 0) {
            dispatch(throughWalls ? PIPELINE_OUTLINE_THROUGH : PIPELINE_OUTLINE_DEPTH, PrimitiveTopology.LINES, 24, mvp,
                    b -> boxEdges(b, 0, 0, 0, w, h, d, outCol));
        }
    }

    private static void boxFaces(BufferBuilder b, float x, float y, float z, float w, float h, float d, int col) {
        quad(b, x, y, z,  x + w, y, z,  x + w, y, z + d,  x, y, z + d, col);
        quad(b, x, y + h, z,  x, y + h, z + d,  x + w, y + h, z + d,  x + w, y + h, z, col);
        quad(b, x, y, z,  x, y + h, z,  x + w, y + h, z,  x + w, y, z, col);
        quad(b, x, y, z + d,  x + w, y, z + d,  x + w, y + h, z + d,  x, y + h, z + d, col);
        quad(b, x, y, z,  x, y, z + d,  x, y + h, z + d,  x, y + h, z, col);
        quad(b, x + w, y, z,  x + w, y + h, z,  x + w, y + h, z + d,  x + w, y, z + d, col);
    }

    private static void boxEdges(BufferBuilder b, float x, float y, float z, float w, float h, float d, int col) {
        line(b, x, y, z, x + w, y, z, col);
        line(b, x + w, y, z, x + w, y, z + d, col);
        line(b, x + w, y, z + d, x, y, z + d, col);
        line(b, x, y, z + d, x, y, z, col);
        line(b, x, y + h, z, x + w, y + h, z, col);
        line(b, x + w, y + h, z, x + w, y + h, z + d, col);
        line(b, x + w, y + h, z + d, x, y + h, z + d, col);
        line(b, x, y + h, z + d, x, y + h, z, col);
        line(b, x, y, z, x, y + h, z, col);
        line(b, x + w, y, z, x + w, y + h, z, col);
        line(b, x + w, y, z + d, x + w, y + h, z + d, col);
        line(b, x, y, z + d, x, y + h, z + d, col);
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