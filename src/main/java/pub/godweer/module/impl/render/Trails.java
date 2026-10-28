package pub.godweer.module.impl.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.event.list.WorldLeaveEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Trails: renders an animated 3D ribbon trail behind the player.
 */
public final class Trails extends Module {

    public static final String COLOR_THEME = "Theme";
    public static final String COLOR_GRADIENT = "Gradient";
    public static final String COLOR_RAINBOW = "Rainbow";
    public static final String COLOR_CUSTOM = "Custom";

    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();
    private static final double MIN_STEP_DIST_SQR = 0.0009D; // ~0.03 blocks
    private static final double TELEPORT_RESET_DIST_SQR = 64.0D; // 8 blocks
    // Сегменты у самой камеры и за её плоскостью не рисуем: вершина в паре
    // сантиметров от глаз даёт растянутую через весь экран полосу вместо ленты.
    private static final double CAMERA_MIN_DIST_SQR = 2.56D; // 1.6 blocks
    private static final double CAMERA_MIN_ALONG = 0.40D; // позади/внутри плоскости взгляда

    private static final RenderPipeline PIPELINE_RIBBON_DEPTH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/trails_ribbon_depth"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline PIPELINE_RIBBON_THROUGH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/trails_ribbon_through"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline PIPELINE_GLOW_DEPTH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/trails_glow_depth"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline PIPELINE_GLOW_THROUGH = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/trails_glow_through"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final HeaderSetting styleHeader = header("Style");
    public final SliderSetting duration = slider("Время жизни (сек)", 0.75F, 0.20F, 2.50F, 0.05F);
    public final SliderSetting height = slider("Высота", 1.65F, 0.30F, 2.10F, 0.05F);
    public final SliderSetting opacity = slider("Прозрачность", 0.80F, 0.15F, 1.00F, 0.05F);

    private final HeaderSetting fxHeader = header("Effects");
    public final CheckBox glow = checkbox("Свечение", true);
    public final CheckBox firstPerson = checkbox("От первого лица", false);
    public final CheckBox throughWalls = checkbox("Сквозь стены", false);

    private final HeaderSetting colorHeader = header("Color");
    public final ModeSetting colorMode = mode("Режим цвета", COLOR_THEME, COLOR_THEME, COLOR_GRADIENT, COLOR_RAINBOW, COLOR_CUSTOM);
    public final ColorSetting color1 = color("Цвет 1", ColorUtil.rgba(255, 170, 45, 255))
            .visible(() -> colorMode.getValue().equalsIgnoreCase(COLOR_CUSTOM) || colorMode.getValue().equalsIgnoreCase(COLOR_GRADIENT));
    public final ColorSetting color2 = color("Цвет 2", ColorUtil.rgba(145, 85, 255, 255))
            .visible(() -> colorMode.getValue().equalsIgnoreCase(COLOR_GRADIENT));

    private final List<TrailNode> nodes = new ArrayList<>(256);

    private GpuBuffer ribbonVertexBuffer;
    private GpuBuffer glowVertexBuffer;
    private GpuBuffer transformBuffer;

    public Trails() {
        super("Trails", "Красивый 3D шлейф за игроком при движении", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        clearState();
        releaseBuffers();
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        clearState();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null || event.getDeltaTracker() == null) {
            clearState();
            return;
        }

        if (!firstPerson.getValue() && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            return;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;

        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) return;

        long now = System.currentTimeMillis();
        long maxAgeMs = Math.max(150L, Math.round(duration.getValue() * 1000.0F));
        nodes.removeIf(n -> (now - n.timestamp) > maxAgeMs);

        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 currentPos = Render3DUtil.interpolatedPosition(mc.player, tickDelta);
        float playerHeight = Math.max(0.4F, mc.player.getBbHeight());
        float currentRibbonH = Math.min(height.getValue(), playerHeight + 0.25F);

        if (!nodes.isEmpty()) {
            TrailNode last = nodes.get(nodes.size() - 1);
            double distSqr = last.pos.distanceToSqr(currentPos);
            if (distSqr > TELEPORT_RESET_DIST_SQR) {
                nodes.clear();
            } else if (distSqr >= MIN_STEP_DIST_SQR) {
                nodes.add(new TrailNode(currentPos, currentRibbonH, now));
            }
        } else {
            nodes.add(new TrailNode(currentPos, currentRibbonH, now));
        }

        if (nodes.isEmpty()) return;

        int count = nodes.size() + 1;
        if (count < 2) return;

        Vec3 camPos = camera.position();
        boolean ignoreDepth = throughWalls.getValue();
        boolean useGlow = glow.getValue();
        float baseOpacity = opacity.getValue();

        renderRibbonTrail(camera, projection, camPos, now, maxAgeMs, currentPos, currentRibbonH, baseOpacity, ignoreDepth, useGlow);
    }

    private void renderRibbonTrail(Camera camera, Matrix4f projection, Vec3 camPos,
                                   long now, long maxAgeMs, Vec3 headPos, float headHeight,
                                   float baseOpacity, boolean ignoreDepth, boolean useGlow) {
        int total = nodes.size() + 1;
        int segments = total - 1;
        if (segments <= 0) return;

        var forward = camera.forwardVector();
        Vec3 camFwd = new Vec3(forward.x(), forward.y(), forward.z());

        BufferBuilder ribbonBuilder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(512, segments * 12 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        );

        BufferBuilder glowBuilder = useGlow ? new BufferBuilder(
                new ByteBufferBuilder(Math.max(512, segments * 18 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        ) : null;

        int ribbonTriangles = 0;
        int glowTriangles = 0;

        for (int i = 0; i < segments; i++) {
            Vec3 p0 = i < nodes.size() ? nodes.get(i).pos : headPos;
            float h0 = i < nodes.size() ? nodes.get(i).height : headHeight;
            long t0 = i < nodes.size() ? nodes.get(i).timestamp : now;

            Vec3 p1 = (i + 1) < nodes.size() ? nodes.get(i + 1).pos : headPos;
            float h1 = (i + 1) < nodes.size() ? nodes.get(i + 1).height : headHeight;
            long t1 = (i + 1) < nodes.size() ? nodes.get(i + 1).timestamp : now;

            if (p0.distanceToSqr(p1) < 1.0E-6D) continue;
            if (nearCamera(camPos, camFwd, p0) || nearCamera(camPos, camFwd, p1)) continue;

            float life0 = 1.0F - Mth.clamp((float) (now - t0) / (float) maxAgeMs, 0.0F, 1.0F);
            float life1 = 1.0F - Mth.clamp((float) (now - t1) / (float) maxAgeMs, 0.0F, 1.0F);

            float env0 = (float) Math.pow(life0, 1.35F);
            float env1 = (float) Math.pow(life1, 1.35F);
            if (env0 <= 0.005F && env1 <= 0.005F) continue;

            float actualH0 = h0 * (0.35F + 0.65F * Mth.sqrt(life0));
            float actualH1 = h1 * (0.35F + 0.65F * Mth.sqrt(life1));
            float yOffset0 = (h0 - actualH0) * 0.12F;
            float yOffset1 = (h1 - actualH1) * 0.12F;

            float x0 = (float) (p0.x - camPos.x);
            float y0Bot = (float) (p0.y + 0.03D + yOffset0 - camPos.y);
            float y0Mid = y0Bot + actualH0 * 0.5F;
            float y0Top = y0Bot + actualH0;
            float z0 = (float) (p0.z - camPos.z);

            float x1 = (float) (p1.x - camPos.x);
            float y1Bot = (float) (p1.y + 0.03D + yOffset1 - camPos.y);
            float y1Mid = y1Bot + actualH1 * 0.5F;
            float y1Top = y1Bot + actualH1;
            float z1 = (float) (p1.z - camPos.z);

            int baseCol0 = sampleTrailColor(1.0F - life0, now);
            int baseCol1 = sampleTrailColor(1.0F - life1, now);

            int col0Bot = ColorUtil.multiplyAlpha(baseCol0, env0 * baseOpacity * 0.18F);
            int col0Mid = ColorUtil.multiplyAlpha(brighten(baseCol0, 1.15F), env0 * baseOpacity * 0.82F);
            int col0Top = ColorUtil.multiplyAlpha(baseCol0, env0 * baseOpacity * 0.22F);

            int col1Bot = ColorUtil.multiplyAlpha(baseCol1, env1 * baseOpacity * 0.18F);
            int col1Mid = ColorUtil.multiplyAlpha(brighten(baseCol1, 1.15F), env1 * baseOpacity * 0.82F);
            int col1Top = ColorUtil.multiplyAlpha(baseCol1, env1 * baseOpacity * 0.22F);

            ribbonBuilder.addVertex(x0, y0Bot, z0).setColor(col0Bot);
            ribbonBuilder.addVertex(x0, y0Mid, z0).setColor(col0Mid);
            ribbonBuilder.addVertex(x1, y1Mid, z1).setColor(col1Mid);

            ribbonBuilder.addVertex(x0, y0Bot, z0).setColor(col0Bot);
            ribbonBuilder.addVertex(x1, y1Mid, z1).setColor(col1Mid);
            ribbonBuilder.addVertex(x1, y1Bot, z1).setColor(col1Bot);

            ribbonBuilder.addVertex(x0, y0Mid, z0).setColor(col0Mid);
            ribbonBuilder.addVertex(x0, y0Top, z0).setColor(col0Top);
            ribbonBuilder.addVertex(x1, y1Top, z1).setColor(col1Top);

            ribbonBuilder.addVertex(x0, y0Mid, z0).setColor(col0Mid);
            ribbonBuilder.addVertex(x1, y1Top, z1).setColor(col1Top);
            ribbonBuilder.addVertex(x1, y1Mid, z1).setColor(col1Mid);
            ribbonTriangles += 4;

            if (glowBuilder != null) {
                int glowCore0 = ColorUtil.multiplyAlpha(brighten(baseCol0, 1.45F), env0 * baseOpacity * 0.85F);
                int glowCore1 = ColorUtil.multiplyAlpha(brighten(baseCol1, 1.45F), env1 * baseOpacity * 0.85F);
                int glowEdge0 = ColorUtil.multiplyAlpha(brighten(baseCol0, 1.35F), env0 * baseOpacity * 0.65F);
                int glowEdge1 = ColorUtil.multiplyAlpha(brighten(baseCol1, 1.35F), env1 * baseOpacity * 0.65F);

                glowTriangles += emitCameraRibbon(glowBuilder, camPos,
                        p0.x, p0.y + 0.03D + yOffset0 + actualH0, p0.z,
                        p1.x, p1.y + 0.03D + yOffset1 + actualH1, p1.z,
                        0.028F * (0.4F + 0.6F * life0), glowEdge0, glowEdge1);

                glowTriangles += emitCameraRibbon(glowBuilder, camPos,
                        p0.x, p0.y + 0.03D + yOffset0, p0.z,
                        p1.x, p1.y + 0.03D + yOffset1, p1.z,
                        0.026F * (0.4F + 0.6F * life0), glowEdge0, glowEdge1);

                glowTriangles += emitCameraRibbon(glowBuilder, camPos,
                        p0.x, p0.y + 0.03D + yOffset0 + actualH0 * 0.5F, p0.z,
                        p1.x, p1.y + 0.03D + yOffset1 + actualH1 * 0.5F, p1.z,
                        0.045F * (0.4F + 0.6F * life0), glowCore0, glowCore1);
            }
        }

        Matrix4f mvp = new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));

        if (ribbonTriangles > 0) {
            submitMesh(ribbonBuilder, mvp, ignoreDepth ? PIPELINE_RIBBON_THROUGH : PIPELINE_RIBBON_DEPTH, true);
        } else {
            ribbonBuilder.buildOrThrow().close();
        }

        if (glowBuilder != null) {
            if (glowTriangles > 0) {
                submitMesh(glowBuilder, mvp, ignoreDepth ? PIPELINE_GLOW_THROUGH : PIPELINE_GLOW_DEPTH, false);
            } else {
                glowBuilder.buildOrThrow().close();
            }
        }
    }

    private int emitCameraRibbon(BufferBuilder builder, Vec3 cam,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float width, int fromColor, int toColor) {
        if (width <= 1.0E-5F || (ColorUtil.alpha(fromColor) <= 0 && ColorUtil.alpha(toColor) <= 0)) {
            return 0;
        }

        double sdx = x2 - x1;
        double sdy = y2 - y1;
        double sdz = z2 - z1;
        double midX = (x1 + x2) * 0.5D;
        double midY = (y1 + y2) * 0.5D;
        double midZ = (z1 + z2) * 0.5D;
        double vdx = cam.x - midX;
        double vdy = cam.y - midY;
        double vdz = cam.z - midZ;

        double nx = sdy * vdz - sdz * vdy;
        double ny = sdz * vdx - sdx * vdz;
        double nz = sdx * vdy - sdy * vdx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-6D) return 0;

        float ox = (float) (nx / nl * width);
        float oy = (float) (ny / nl * width);
        float oz = (float) (nz / nl * width);

        float ax = (float) (x1 - cam.x);
        float ay = (float) (y1 - cam.y);
        float az = (float) (z1 - cam.z);
        float bx = (float) (x2 - cam.x);
        float by = (float) (y2 - cam.y);
        float bz = (float) (z2 - cam.z);

        builder.addVertex(ax - ox, ay - oy, az - oz).setColor(fromColor);
        builder.addVertex(ax + ox, ay + oy, az + oz).setColor(fromColor);
        builder.addVertex(bx + ox, by + oy, bz + oz).setColor(toColor);

        builder.addVertex(ax - ox, ay - oy, az - oz).setColor(fromColor);
        builder.addVertex(bx + ox, by + oy, bz + oz).setColor(toColor);
        builder.addVertex(bx - ox, by - oy, bz - oz).setColor(toColor);
        return 2;
    }

    /** True, если точка лежит вблизи глаз или уже за плоскостью взгляда. */
    private static boolean nearCamera(Vec3 camPos, Vec3 camFwd, Vec3 point) {
        double dx = point.x - camPos.x;
        double dy = point.y - camPos.y;
        double dz = point.z - camPos.z;
        double along = dx * camFwd.x + dy * camFwd.y + dz * camFwd.z;
        return along < CAMERA_MIN_ALONG || (dx * dx + dy * dy + dz * dz) < CAMERA_MIN_DIST_SQR;
    }

    private int sampleTrailColor(float tailProgress, long nowMillis) {
        String mode = colorMode.getValue();
        float cycle = (float) ((nowMillis % 6000L) / 6000.0D);
        float wave = 0.5F + 0.5F * Mth.sin((tailProgress * 2.2F - cycle * 2.0F) * (float) (Math.PI * 2.0));

        if (mode.equalsIgnoreCase(COLOR_RAINBOW)) {
            float hue = (tailProgress * 0.65F - cycle * 1.5F) % 1.0F;
            if (hue < 0.0F) hue += 1.0F;
            return ColorUtil.fromHsv(hue, 0.78F, 1.0F, 255);
        }

        if (mode.equalsIgnoreCase(COLOR_GRADIENT)) {
            return ColorUtil.lerp(color1.getValue(), color2.getValue(), wave);
        }

        if (mode.equalsIgnoreCase(COLOR_CUSTOM)) {
            int base = color1.getValue();
            int secondary = shiftHueAndBrighten(base, 0.05F, 1.18F);
            return ColorUtil.lerp(base, secondary, wave * 0.55F);
        }

        int accent = Theme.getAccentColor();
        int shifted = shiftHueAndBrighten(accent, 0.06F, 1.22F);
        return ColorUtil.lerp(accent, shifted, wave * 0.65F);
    }

    private static int shiftHueAndBrighten(int color, float hueShift, float satBoost) {
        float[] hsv = ColorUtil.toHsv(color);
        float h = (hsv[0] + hueShift) % 1.0F;
        if (h < 0.0F) h += 1.0F;
        float s = Mth.clamp(hsv[1] * 0.88F, 0.0F, 1.0F);
        float v = Mth.clamp(hsv[2] * satBoost, 0.0F, 1.0F);
        return ColorUtil.fromHsv(h, s, v, ColorUtil.alpha(color));
    }

    private static int brighten(int color, float factor) {
        int r = Mth.clamp(Math.round(ColorUtil.red(color) * factor), 0, 255);
        int g = Mth.clamp(Math.round(ColorUtil.green(color) * factor), 0, 255);
        int b = Mth.clamp(Math.round(ColorUtil.blue(color) * factor), 0, 255);
        return ColorUtil.pack(r, g, b, ColorUtil.alpha(color));
    }

    private void submitMesh(BufferBuilder builder, Matrix4f mvp, RenderPipeline pipeline, boolean isRibbonBuffer) {
        MeshData mesh = builder.buildOrThrow();
        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = mesh.vertexBuffer();
            int byteCount = vertexData.remaining();
            int vertexCount = mesh.drawState().vertexCount();
            if (vertexCount <= 0 || byteCount <= 0) return;

            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (target == null || target.getColorTextureView() == null) return;

            GpuBuffer vbo = ensureVertexCapacity(byteCount, isRibbonBuffer);
            ensureTransformBuffer();

            var encoder = device.createCommandEncoder();
            encoder.writeToBuffer(vbo.slice(0, byteCount), vertexData);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer ubo = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(mvp).get();
                encoder.writeToBuffer(this.transformBuffer.slice(), ubo);
            }

            boolean depthTested = pipeline == PIPELINE_RIBBON_DEPTH || pipeline == PIPELINE_GLOW_DEPTH;
            var depthView = depthTested ? target.getDepthTextureView() : null;

            try (RenderPass pass = depthView != null
                    ? encoder.createRenderPass(() -> "Godweer Trails Pass", target.getColorTextureView(), Optional.empty(), depthView, OptionalDouble.empty())
                    : encoder.createRenderPass(() -> "Godweer Trails Pass", target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                pass.setUniform("Projection", this.transformBuffer);
                pass.setVertexBuffer(0, vbo.slice(0, byteCount));
                pass.draw(vertexCount, 1, 0, 0);
            }
        } finally {
            mesh.close();
        }
    }

    private GpuBuffer ensureVertexCapacity(int requiredBytes, boolean isRibbonBuffer) {
        GpuBuffer current = isRibbonBuffer ? this.ribbonVertexBuffer : this.glowVertexBuffer;
        if (current != null && current.size() >= requiredBytes) {
            return current;
        }
        if (current != null) {
            current.close();
        }
        int size = Math.max(requiredBytes * 2, 16384);
        GpuBuffer created = RenderSystem.getDevice().createBuffer(
                () -> isRibbonBuffer ? "Godweer Trails Ribbon VBO" : "Godweer Trails Glow VBO",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                size
        );
        if (isRibbonBuffer) {
            this.ribbonVertexBuffer = created;
        } else {
            this.glowVertexBuffer = created;
        }
        return created;
    }

    private void ensureTransformBuffer() {
        if (this.transformBuffer != null) return;
        this.transformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Trails Transform UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                TRANSFORM_SIZE
        );
    }

    private void clearState() {
        this.nodes.clear();
    }

    private void releaseBuffers() {
        if (this.ribbonVertexBuffer != null) {
            this.ribbonVertexBuffer.close();
            this.ribbonVertexBuffer = null;
        }
        if (this.glowVertexBuffer != null) {
            this.glowVertexBuffer.close();
            this.glowVertexBuffer = null;
        }
        if (this.transformBuffer != null) {
            this.transformBuffer.close();
            this.transformBuffer = null;
        }
    }

    private record TrailNode(Vec3 pos, float height, long timestamp) {
    }
}
