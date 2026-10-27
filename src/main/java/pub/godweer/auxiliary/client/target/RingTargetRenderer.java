package pub.godweer.auxiliary.client.target;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.kitty.module.impl.render.TargetEsp;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Кольцо, которое бежит по хитбоксу цели сверху вниз и обратно, оставляя
 * motion trail: прошлые позиции кольца остаются висеть в мире и гаснут по
 * возрасту. Шлейф повторяет не только ход кольца по высоте, но и перемещение
 * самой цели, потому что снимки хранятся в мировых координатах.
 *
 * Радиус шлейфа постоянный: витки не сжимаются к хвосту, иначе стопка колец
 * читается как воронка. Сечение самого кольца — мягкий градиент по высоте
 * (светлая середина, гаснущие края) плюс широкое тусклое свечение у головы:
 * обычный 3D-блум вместо плоской стены с жёсткими гранями.
 *
 * Возраст хранится в секундах, а не в кадрах: на 500 fps покадровый шлейф был бы
 * вдвое короче, чем на 60. Новый снимок берётся не чаще раза в SAMPLE_SECONDS.
 */
public final class RingTargetRenderer {

    private static final double TAU = Math.PI * 2.0;
    private static final int MAX_SAMPLES = 80;
    // 0.014 с ≈ 70 снимков в секунду: витки перекрываются плотнее, градиент
    // шлейфа получается непрерывным, а не ступенчатым.
    private static final double SAMPLE_SECONDS = 0.014D;
    private static final float MAX_DELTA_SECONDS = 0.05F;
    // Радиус = половина ширины хитбокса + база + «Отступ» из настроек с весом.
    private static final double BASE_RADIUS = 0.18D;
    private static final double OFFSET_WEIGHT = 1.5D;
    // Альфа по высоте сечения кольца: узлы от нижнего края к верхнему. Между узлами
    // альфа интерполируется линейно, поэтому сечение — мягкая трубка со светящейся
    // серединой, а не плоская стена с жёсткими гранями.
    private static final float[] SECTION = {0.00F, 0.30F, 1.00F, 0.30F, 0.00F};
    // Витков шлейфа на отрисовку больше этого числа не берём: при максимальном
    // шлейфе в 1.1 с снимков накоплено ~80, а вершин на все не нужно.
    private static final int MAX_DRAWN_RINGS = 16;
    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/target_ring"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final double[] sx = new double[MAX_SAMPLES];
    private final double[] sy = new double[MAX_SAMPLES];
    private final double[] sz = new double[MAX_SAMPLES];
    private final double[] sr = new double[MAX_SAMPLES];
    private final double[] st = new double[MAX_SAMPLES];
    private final float[] age = new float[MAX_SAMPLES];
    private int samples;

    private double sweep;
    private double sampleClock;
    private long lastFrameNanos;

    private BufferBuilder meshBuilder;
    private int written;
    private GpuBuffer vertexBuffer;
    private GpuBuffer transformBuffer;

    public void render(TargetEsp module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        if (Render3DUtil.levelProjectionCopy() == null) return;

        double dt = this.lastFrameNanos == 0L ? (1.0 / 60.0)
                : Math.min((System.nanoTime() - this.lastFrameNanos) / 1.0E9, MAX_DELTA_SECONDS);
        this.lastFrameNanos = System.nanoTime();

        double trailSeconds = module.ringTrail.getValue().floatValue();
        double thickness = module.ringThickness.getValue().floatValue();
        int segments = module.ringSegments.getValue().intValue();

        for (int i = 0; i < this.samples; i++) {
            this.age[i] += (float) dt;
        }
        while (this.samples > 0 && this.age[this.samples - 1] > trailSeconds) {
            this.samples--;
        }

        LivingEntity target = module.resolveTarget();
        this.sweep += dt * module.ringSpeed.getValue().floatValue();
        this.sampleClock += dt;
        if (target != null && !target.isRemoved() && this.sampleClock >= SAMPLE_SECONDS) {
            this.sampleClock = 0.0D;
            push(target, tickDelta, module, thickness);
        }

        if (this.samples == 0) return;

        Vec3 cam = camera.position();
        int color = module.getAccentColor();
        int alphaBase = ColorUtil.alpha(color);

        int glowSegments = Math.max(16, segments / 2);
        this.meshBuilder = new BufferBuilder(
                new ByteBufferBuilder(Math.min(256 * 1024, Math.max(8192, MAX_DRAWN_RINGS * segments * 8
                        * DefaultVertexFormat.POSITION_COLOR.getVertexSize()))),
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        this.written = 0;

        // Шаг 2 после головного витка: плотнее там, где кольцо яркое, разреже в хвосте,
        // где оно и так на грани видимости.
        int drawn = 0;
        for (int i = 0; i < this.samples; i += (i == 0 ? 1 : 2)) {
            double k = 1.0D - this.age[i] / trailSeconds;
            if (k <= 0.0D) continue;
            if (++drawn > MAX_DRAWN_RINGS) break;
            // smoothstep вместо степени: у хвоста альфа уходит в ноль без ступеньки,
            // и последние витки не обрываются видимой гранью.
            double fade = k * k * (3.0D - 2.0D * k);
            double thick = this.st[i] * (0.55D + 0.45D * fade);
            int coreAlpha = (int) Math.round(alphaBase * fade);
            double white = Math.max(0.0D, fade - 0.45D) * 1.4D;
            // Широкое тусклое свечение только у головы: оно и есть bloom, а на весь
            // хвост оно превращает шлейф в молочную колонну.
            if (drawn <= 4) {
                ring(this.sx[i], this.sy[i], this.sz[i], this.sr[i], thick * 3.4D,
                        color, (int) Math.round(coreAlpha * 0.16D), 0.0D, glowSegments, cam);
            }
            ring(this.sx[i], this.sy[i], this.sz[i], this.sr[i], thick,
                    color, coreAlpha, white, segments, cam);
        }

        if (this.written > 0) submit(camera);
    }

    private void push(LivingEntity target, float tickDelta, TargetEsp module, double thickness) {
        Vec3 base = Render3DUtil.interpolatedPosition(target, tickDelta);
        // Отступ от края хитбокса: база + настроенный сдвиг с коэффициентом, чтобы
        // кольцо было крупнее и на уже сохранённых в конфиге маленьких значениях.
        double radius = target.getBbWidth() * 0.5D + BASE_RADIUS + module.ringRadius.getValue().floatValue() * OFFSET_WEIGHT;
        double top = base.y + target.getBbHeight() + thickness;
        double bottom = base.y - thickness;
        // Треугольная волна: кольцо проходит хитбокс вниз, потом возвращается наверх.
        double phase = this.sweep % 2.0D;
        double travel = phase < 1.0D ? phase : 2.0D - phase;
        double y = top + (bottom - top) * travel;

        // Индекс 0 — самый свежий, поэтому сдвиг вправо на одну позицию: при полном
        // буфере он же выбрасывает самый старый снимок.
        int shift = Math.min(this.samples, MAX_SAMPLES - 1);
        System.arraycopy(this.sx, 0, this.sx, 1, shift);
        System.arraycopy(this.sy, 0, this.sy, 1, shift);
        System.arraycopy(this.sz, 0, this.sz, 1, shift);
        System.arraycopy(this.sr, 0, this.sr, 1, shift);
        System.arraycopy(this.st, 0, this.st, 1, shift);
        System.arraycopy(this.age, 0, this.age, 1, shift);
        if (this.samples < MAX_SAMPLES) {
            this.samples++;
        }

        this.sx[0] = base.x;
        this.sy[0] = y;
        this.sz[0] = base.z;
        this.sr[0] = radius;
        this.st[0] = thickness;
        this.age[0] = 0.0F;
    }

    private void ring(double cx, double cy, double cz, double radius, double thickness,
                      int color, int alpha, double white, int segments, Vec3 cam) {
        BufferBuilder builder = this.meshBuilder;
        if (builder == null || alpha <= 0 || radius <= 0.0D) return;

        // Молодой виток выбелен, старый возвращается к настройному цвету.
        double towardWhite = Math.min(1.0D, Math.max(0.0D, white));
        int r = channel(ColorUtil.red(color), towardWhite);
        int g = channel(ColorUtil.green(color), towardWhite);
        int bl = channel(ColorUtil.blue(color), towardWhite);

        double px = cx - cam.x, py = cy - cam.y, pz = cz - cam.z;
        double half = thickness * 0.5D;
        double step = thickness / (SECTION.length - 1);
        for (int b = 0; b + 1 < SECTION.length; b++) {
            // Соседние полосы делят общий край с той же альфой, поэтому сечение
            // читается непрерывным градиентом, а не набором ступенек.
            int bottom = ColorUtil.pack(r, g, bl, (int) Math.round(alpha * SECTION[b]));
            int top = ColorUtil.pack(r, g, bl, (int) Math.round(alpha * SECTION[b + 1]));
            if (ColorUtil.alpha(bottom) <= 0 && ColorUtil.alpha(top) <= 0) continue;
            double y0 = -half + step * b;
            double y1 = y0 + step;
            for (int i = 0; i < segments; i++) {
                double a0 = TAU * i / segments;
                double a1 = TAU * (i + 1) / segments;
                double x0 = Math.cos(a0) * radius, z0 = Math.sin(a0) * radius;
                double x1 = Math.cos(a1) * radius, z1 = Math.sin(a1) * radius;
                band(builder, px + x0, py + y0, pz + z0, px + x1, py + y0, pz + z1,
                        px + x1, py + y1, pz + z1, px + x0, py + y1, pz + z0, bottom, top);
            }
        }
    }

    private static int channel(int value, double towardWhite) {
        return Math.min(255, (int) Math.round(value + (255 - value) * towardWhite));
    }

    private void band(BufferBuilder builder,
                      double x0, double y0, double z0, double x1, double y1, double z1,
                      double x2, double y2, double z2, double x3, double y3, double z3,
                      int bottom, int top) {
        builder.addVertex((float) x0, (float) y0, (float) z0).setColor(bottom);
        builder.addVertex((float) x1, (float) y1, (float) z1).setColor(bottom);
        builder.addVertex((float) x2, (float) y2, (float) z2).setColor(top);
        builder.addVertex((float) x0, (float) y0, (float) z0).setColor(bottom);
        builder.addVertex((float) x2, (float) y2, (float) z2).setColor(top);
        builder.addVertex((float) x3, (float) y3, (float) z3).setColor(top);
        this.written += 6;
    }

    private void submit(Camera camera) {
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null || this.meshBuilder == null) return;
        MeshData mesh = this.meshBuilder.buildOrThrow();
        this.meshBuilder = null;
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
                    () -> "Godweer Target Ring",
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
            mesh.close();
        }
    }

    private void ensureTransformBuffer() {
        if (this.transformBuffer != null) return;
        this.transformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Target Ring UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, TRANSFORM_SIZE);
    }

    private void ensureVertexCapacity(int byteSize) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Target Ring Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
    }

    public void release() {
        this.samples = 0;
        this.sweep = 0.0D;
        this.sampleClock = 0.0D;
        this.lastFrameNanos = 0L;
        this.meshBuilder = null;
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
        if (this.transformBuffer != null) {
            this.transformBuffer.close();
            this.transformBuffer = null;
        }
    }
}
