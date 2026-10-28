package pub.godweer.util.render.world;

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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.module.impl.render.Lines;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Lines: «кибер-трассы» в мире. Каждая линия растёт прямыми участками вдоль осей
 * блоков: длина участка до 3 блоков, направление выбирается случайно из свободных,
 * разворот назад не допускается. Проходимость проверяется по целым блокам, поэтому
 * линия огибает препятствия, а не проходит сквозь них; в тупике линия уходит в
 * увядание.
 *
 * Острие интерполируется между тиками, поэтому рост гладкий, а не по блокам.
 * Яркость идёт продольным градиентом (хвост 0.22, острие 1.0), вся линия умножается
 * на квадратичную огибающую появления/исчезновения.
 *
 * Неон — четыре прохода лентами, повёрнутыми к камере: три аддитивных ореола
 * (5.4/3.1/1.7 ширины ядра при альфах 0.12/0.20/0.42) и чёткое ядро обычным
 * блендингом. Width делаю лентами, а не lineWidth, потому что в 26.3 толщина линий
 * на современных бэкендах не задаётся.
 *
 * Create by daun kvass
 */
public final class LinesWorldRenderer {

    private static final double TICK_SECONDS = 1.0 / 20.0;
    private static final double FADE_IN = 0.35D;
    private static final double FADE_OUT = 0.85D;
    private static final double GRADIENT_TAIL = 0.22D;

    private static final int MAX_SPAWN_PER_TICK = 3;
    private static final int SPAWN_ATTEMPTS = 16;
    private static final double SPAWN_NEAR = 4.0D;
    private static final double SPAWN_SECTOR_DEGREES = 70.0D;

    // Длина одного прямого участка в блоках: трасса растёт шагами вдоль осей блоков,
    // поэтому повороты кратны 90° и читаются как рёбра, а не как случайная кривая.
    private static final double MAX_SEGMENT = 3.0D;
    // Шесть осевых направлений для continuation и четыре горизонтальных для старта:
    // первая трасса, которая сразу уходит вверх или вниз, выглядит оборванной.
    private static final Vec3[] ALL_DIRS = {
            new Vec3(1.0D, 0.0D, 0.0D), new Vec3(-1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
            new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 0.0D, -1.0D)
    };
    private static final Vec3[] HORIZONTAL_DIRS = {
            new Vec3(1.0D, 0.0D, 0.0D), new Vec3(-1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 0.0D, -1.0D)
    };

    // Потолок квадов на проход: 200 линий в пунктире могли бы выдать непомёрную
    // геометрию, а растущий ByteBufferBuilder при таком расходе только грузит память.
    private static final int MAX_QUADS_PER_PASS = 40_000;

    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static RenderPipeline pipeline(String name, BlendFunction blend, boolean depthTest) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("godweer:core/lines"))
                .withFragmentShader(Identifier.parse("godweer:core/lines"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(depthTest
                        ? Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                        : Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static final RenderPipeline ADDITIVE = pipeline("lines_additive", BlendFunction.LIGHTNING, true);
    private static final RenderPipeline ADDITIVE_THROUGH = pipeline("lines_additive_through", BlendFunction.LIGHTNING, false);
    private static final RenderPipeline ALPHA = pipeline("lines_alpha", BlendFunction.TRANSLUCENT, true);
    private static final RenderPipeline ALPHA_THROUGH = pipeline("lines_alpha_through", BlendFunction.TRANSLUCENT, false);

    private final List<Trace> traces = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    private ClientLevel lastLevel;
    private double time;
    private GpuBuffer vertexBuffer;
    private GpuBuffer transformBuffer;

    /** Контекст одного кадра: чтобы не протаскивать десять параметров через эмиссию. */
    private record Frame(Vec3 cam, int accent, double now, float tickDelta, double speed,
                         double coreWidth, boolean dashed, double dash, boolean throughWalls) {
    }

    /**
     * Популяция линий: 10..200 живых трасс. Лишние при уменьшении настройки не
     * удаляются мгновенно, а отправляются в увядание — иначе сеть мигает.
     */
    public void tick(Lines module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.traces.clear();
            this.lastLevel = null;
            return;
        }
        if (mc.level != this.lastLevel) {
            this.traces.clear();
            this.lastLevel = mc.level;
        }

        this.time += TICK_SECONDS;
        this.traces.removeIf(trace -> trace.isDead(this.time));

        int desired = Mth.clamp((int) module.count.get(), 10, 200);
        int alive = 0;
        for (Trace trace : this.traces) {
            if (!trace.dying) alive++;
        }

        int excess = alive - desired;
        if (excess > 0) {
            for (Trace trace : this.traces) {
                if (excess <= 0) break;
                if (trace.dying) continue;
                trace.startDying(this.time);
                excess--;
                alive--;
            }
        }

        double speed = Math.max(0.2D, module.speed.get());
        int missing = desired - alive;
        for (int i = 0; i < Math.min(Math.max(missing, 0), MAX_SPAWN_PER_TICK); i++) {
            spawn(mc, module);
        }

        for (Trace trace : this.traces) {
            trace.tick(mc.level, this.time, speed);
        }
    }

    private void spawn(Minecraft mc, Lines module) {
        Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;

        Vec3 origin = findSpawnPos(mc.level, camera, module);
        // Повороты берём разбросом в 5 штук: трассы одной длины выглядят конвейером.
        int max = Mth.clamp((int) module.turns.get(), 4, 24);
        int min = Math.max(3, max - 5);
        this.traces.add(new Trace(mc.level, origin, this.time, min + random.nextInt(max - min + 1)));
    }

    /**
     * Ищем чистый воздух в секторе ±70° перед камерой на дистанции 4..reach блоков:
     * линии не должны рождаться внутри стен и за спиной, где их всё равно не видно.
     */
    private Vec3 findSpawnPos(ClientLevel level, Camera camera, Lines module) {
        Vec3 camPos = camera.position();
        double far = Math.max(SPAWN_NEAR + 1.0D, module.reach.get());

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double yaw = Math.toRadians(camera.yRot() + random.nextDouble() * 2.0D * SPAWN_SECTOR_DEGREES
                    - SPAWN_SECTOR_DEGREES);
            double distance = SPAWN_NEAR + (far - SPAWN_NEAR) * random.nextDouble();
            Vec3 candidate = camPos.add(-Mth.sin((float) yaw) * distance,
                    random.nextDouble() * 5.0D - 1.0D,
                    Mth.cos((float) yaw) * distance);
            if (isPassable(level, candidate)) return candidate;
        }

        Vec3 fallback = camPos;
        return isPassable(level, fallback) ? fallback : fallback.add(0.0D, 1.0D, 0.0D);
    }

    private boolean isPassable(ClientLevel level, Vec3 point) {
        int y = Mth.floor(point.y);
        if (level.isOutsideBuildHeight(y)) return false;
        this.scratch.set(Mth.floor(point.x), y, Mth.floor(point.z));
        return level.getBlockState(this.scratch).isAir()
                && level.getFluidState(this.scratch).isEmpty();
    }

    public void render(Lines module, float tickDelta, Minecraft mc) {
        if (mc.level == null || mc.gameRenderer == null || this.traces.isEmpty()) return;

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        if (Render3DUtil.levelProjectionCopy() == null) return;

        int accent = module.themeColor.get() ? Theme.getAccentColor() : module.color.getValue();
        double dash = Mth.clamp(module.dashScale.get(), 0.05F, 8.0F);
        Frame frame = new Frame(camera.position(), accent, this.time + tickDelta * TICK_SECONDS,
                tickDelta, Math.max(0.2D, module.speed.get()), Math.max(0.006F, module.thickness.get()),
                module.dashed.get(), dash, module.throughWalls.get());

        if (module.glow.get()) {
            drawPass(frame, camera, 5.4F, 0.12F, true);
            drawPass(frame, camera, 3.1F, 0.20F, true);
            drawPass(frame, camera, 1.7F, 0.42F, true);
            drawPass(frame, camera, 1.0F, 0.95F, false);
        } else {
            drawPass(frame, camera, 1.3F, 0.92F, false);
        }
    }

    private void drawPass(Frame frame, Camera camera, float widthScale, float alphaMul, boolean additive) {
        float width = (float) Math.max(0.002D, frame.coreWidth() * widthScale);
        BufferBuilder builder = new BufferBuilder(new ByteBufferBuilder(64 * 1024),
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int quads = 0;
        for (Trace trace : this.traces) {
            quads += trace.emit(builder, frame, width, alphaMul);
            if (quads >= MAX_QUADS_PER_PASS) break;
        }
        if (quads == 0) return;
        submit(builder, camera, frame.throughWalls(), additive);
    }

    private void submit(BufferBuilder builder, Camera camera, boolean throughWalls, boolean additive) {
        MeshData mesh = builder.buildOrThrow();
        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = mesh.vertexBuffer();
            int byteCount = vertexData.remaining();
            int vertexCount = mesh.drawState().vertexCount();
            if (vertexCount <= 0) return;

            var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (screen == null || screen.getColorTextureView() == null) return;

            ensureVertexCapacity(byteCount);
            device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, byteCount), vertexData);

            Matrix4f mvp = new Matrix4f(Render3DUtil.levelProjectionCopy())
                    .mul(camera.getViewRotationMatrix(new Matrix4f()));
            ensureTransformBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(mvp).get();
                device.createCommandEncoder().writeToBuffer(this.transformBuffer.slice(), data);
            }

            RenderPipeline pipeline = additive
                    ? (throughWalls ? ADDITIVE_THROUGH : ADDITIVE)
                    : (throughWalls ? ALPHA_THROUGH : ALPHA);

            var colorView = screen.getColorTextureView();
            var depthView = screen.getDepthTextureView();
            // Тест глубины требует приложенного depth-view: без него линия прорисовалась
            // бы поверх рельефа даже при выключенном «сквозь блоки».
            try (RenderPass pass = depthView != null && !throughWalls
                    ? device.createCommandEncoder().createRenderPass(() -> "Godweer Lines",
                    colorView, Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(() -> "Godweer Lines",
                    colorView, Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
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
                () -> "Godweer Lines UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                TRANSFORM_SIZE);
    }

    private void ensureVertexCapacity(int byteSize) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Lines Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                capacity);
    }

    public void release() {
        this.traces.clear();
        this.lastLevel = null;
        this.time = 0.0D;
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
        if (this.transformBuffer != null) {
            this.transformBuffer.close();
            this.transformBuffer = null;
        }
    }

    /** Одна трасса: узлы в мировых координатах + текущий незавершённый участок. */
    private final class Trace {
        private final ArrayList<Vec3> nodes = new ArrayList<>(16);
        private final double born;

        private Vec3 segmentStart;
        private Vec3 segmentEnd;
        private Vec3 currentDir;
        private double progress;
        private int remainingTurns;
        private boolean dying;
        private double deathStart;

        Trace(ClientLevel level, Vec3 start, double now, int turns) {
            this.nodes.add(start);
            this.born = now;
            this.segmentStart = start;
            this.segmentEnd = start;
            this.remainingTurns = turns;
            if (!beginSegment(level, start, true)) {
                startDying(now);
            }
        }

        void startDying(double now) {
            if (!this.dying) {
                this.dying = true;
                this.deathStart = now;
            }
        }

        boolean isDead(double now) {
            return this.dying && now - this.deathStart >= FADE_OUT;
        }

        void tick(ClientLevel level, double now, double speed) {
            if (this.dying) return;
            if (this.segmentStart == null || this.segmentEnd == null
                    || this.segmentStart.distanceToSqr(this.segmentEnd) < 1.0E-8D) {
                startDying(now);
                return;
            }

            this.progress += speed * TICK_SECONDS;
            if (this.progress < 1.0D) return;

            this.nodes.add(this.segmentEnd);
            this.remainingTurns--;
            // повороты кончились или упёрлись в блок — линия отживает своё
            if (this.remainingTurns <= 0
                    || !beginSegment(level, this.segmentEnd, false)) {
                startDying(now);
                return;
            }
            this.progress = Mth.clamp(this.progress - 1.0D, 0.0D, 1.0D);
        }

        /**
         * Следующий прямой участок. Направление берётся из осей блоков в случайном
         * порядке: первый участок только горизонтальный, дальше — любое, кроме
         * разворота назад (иначе трасса жевала бы саму себя в первом же узле).
         * Берётся первое направление, у которого весь участок идёт в воздухе,
         * поэтому линия огибает блоки, а не проходит сквозь них.
         */
        private boolean beginSegment(ClientLevel level, Vec3 from, boolean first) {
            Vec3[] pool = first ? HORIZONTAL_DIRS : ALL_DIRS;
            int[] order = shuffle(pool.length);
            for (int attempt = 0; attempt < order.length; attempt++) {
                Vec3 dir = pool[order[attempt]];
                if (!first && this.currentDir != null && dir.dot(this.currentDir) < 0.0D) continue;
                double length = freeLength(level, from, dir);
                if (length <= 0.0D) continue;

                this.currentDir = dir;
                this.segmentStart = from;
                this.segmentEnd = from.add(dir.scale(length));
                this.progress = 0.0D;
                return true;
            }
            return false;
        }

        /** Сколько целых блоков трасса проходит по направлению, не влетев в блок. */
        private double freeLength(ClientLevel level, Vec3 from, Vec3 dir) {
            double length = 0.0D;
            for (int step = 1; step <= (int) Math.floor(MAX_SEGMENT); step++) {
                if (!isPassable(level, from.add(dir.scale(step)))) break;
                length = step;
            }
            return length;
        }

        /** Перемешанный обход направлений, чтобы каждый виток не повторял предыдущий. */
        private int[] shuffle(int count) {
            int[] order = new int[count];
            for (int i = 0; i < count; i++) order[i] = i;
            for (int i = count - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = order[i];
                order[i] = order[j];
                order[j] = swap;
            }
            return order;
        }

        /** Острие между тиками: прогресс тика + недотиканный остаток. */
        private Vec3 tip(Frame frame) {
            if (this.dying || this.segmentStart == null || this.segmentEnd == null
                    || this.segmentStart.distanceToSqr(this.segmentEnd) < 1.0E-8D) {
                return this.segmentEnd == null ? this.nodes.get(this.nodes.size() - 1) : this.segmentEnd;
            }
            double render = Mth.clamp(this.progress + frame.speed() * (frame.tickDelta() * TICK_SECONDS),
                    0.0D, 1.0D);
            return this.segmentStart.lerp(this.segmentEnd, render);
        }

        /** Квадратичная огибающая появления и увядания всей трассы. */
        private float envelope(double now) {
            double t = this.dying
                    ? 1.0D - (now - this.deathStart) / FADE_OUT
                    : (now - this.born) / FADE_IN;
            if (t <= 0.0D) return 0.0F;
            if (t >= 1.0D) return 1.0F;
            return (float) (t * t);
        }

        int emit(BufferBuilder builder, Frame frame, float width, float alphaMul) {
            float fade = envelope(frame.now());
            if (fade <= 0.01F || this.nodes.isEmpty()) return 0;

            Vec3 tip = tip(frame);
            double total = 0.0D;
            Vec3 walk = this.nodes.get(0);
            for (int i = 1; i < this.nodes.size(); i++) {
                Vec3 next = this.nodes.get(i);
                total += walk.distanceTo(next);
                walk = next;
            }
            Vec3 last = this.nodes.get(this.nodes.size() - 1);
            double tipLength = last.distanceTo(tip);
            if (tipLength > 1.0E-5D) total += tipLength;
            if (total < 1.0E-5D || width <= 0.0F) return 0;

            int quads = 0;
            double distance = 0.0D;
            Vec3 from = this.nodes.get(0);
            for (int i = 1; i < this.nodes.size(); i++) {
                Vec3 to = this.nodes.get(i);
                double length = from.distanceTo(to);
                quads += emitSegment(builder, frame, from, to, length, distance, total, fade, width, alphaMul);
                distance += length;
                from = to;
            }
            if (tipLength > 1.0E-5D) {
                quads += emitSegment(builder, frame, last, tip, tipLength, distance, total, fade, width, alphaMul);
            }
            return quads;
        }

        private int emitSegment(BufferBuilder builder, Frame frame, Vec3 a, Vec3 b, double length,
                                double startDistance, double total, float fade, float width, float alphaMul) {
            if (length < 1.0E-5D) return 0;
            double t1 = startDistance / total;
            double t2 = (startDistance + length) / total;
            if (!frame.dashed()) {
                return ribbon(builder, frame, a, b, width, colorAt(frame, t1, fade, alphaMul),
                        colorAt(frame, t2, fade, alphaMul));
            }

            // штрих и пропуск одинаковые; фаза считается от общей дистанции пути,
            // чтобы пунктир не «перещёлкивал» на стыках сегментов
            double cycle = frame.dash() * 2.0D;
            double phase = startDistance % cycle;
            boolean drawing = phase < frame.dash();
            double remaining = drawing ? frame.dash() - phase : cycle - phase;
            Vec3 direction = b.subtract(a).scale(1.0D / length);

            int quads = 0;
            double position = 0.0D;
            while (position < length - 1.0E-5D) {
                double step = Math.min(length - position, remaining);
                if (drawing && step > 1.0E-5D) {
                    Vec3 pa = a.add(direction.scale(position));
                    Vec3 pb = a.add(direction.scale(position + step));
                    double local1 = position / length;
                    double local2 = (position + step) / length;
                    double seg1 = t1 + (t2 - t1) * local1;
                    double seg2 = t1 + (t2 - t1) * local2;
                    quads += ribbon(builder, frame, pa, pb, width, colorAt(frame, seg1, fade, alphaMul),
                            colorAt(frame, seg2, fade, alphaMul));
                }
                position += step;
                remaining -= step;
                if (remaining <= 1.0E-6D) {
                    drawing = !drawing;
                    remaining = frame.dash();
                }
            }
            return quads;
        }
    }

    /** Продольный градиент: хвост темнее, острие ярче. */
    private static int colorAt(Frame frame, double t, float fade, float alphaMul) {
        float alpha = Mth.clamp((float) (GRADIENT_TAIL + (1.0D - GRADIENT_TAIL) * t) * fade * alphaMul, 0.0F, 1.0F);
        return ColorUtil.withAlpha(frame.accent(), alpha);
    }

    /** Лента между двумя точками, повёрнутая к камере: 2 треугольника, 6 вершин. */
    private static int ribbon(BufferBuilder builder, Frame frame, Vec3 a, Vec3 b, float width,
                              int from, int to) {
        if (width <= 0.0F || (ColorUtil.alpha(from) <= 0 && ColorUtil.alpha(to) <= 0)) return 0;

        double sx = b.x - a.x;
        double sy = b.y - a.y;
        double sz = b.z - a.z;
        Vec3 cam = frame.cam();
        double vx = (a.x + b.x) * 0.5D - cam.x;
        double vy = (a.y + b.y) * 0.5D - cam.y;
        double vz = (a.z + b.z) * 0.5D - cam.z;

        double nx = sy * vz - sz * vy;
        double ny = sz * vx - sx * vz;
        double nz = sx * vy - sy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-8D || !Double.isFinite(nl)) return 0;

        float half = width * 0.5F;
        float ox = (float) (nx / nl * half);
        float oy = (float) (ny / nl * half);
        float oz = (float) (nz / nl * half);

        float ax = (float) (a.x - cam.x);
        float ay = (float) (a.y - cam.y);
        float az = (float) (a.z - cam.z);
        float bx = (float) (b.x - cam.x);
        float by = (float) (b.y - cam.y);
        float bz = (float) (b.z - cam.z);

        builder.addVertex(ax - ox, ay - oy, az - oz).setColor(from);
        builder.addVertex(ax + ox, ay + oy, az + oz).setColor(from);
        builder.addVertex(bx + ox, by + oy, bz + oz).setColor(to);

        builder.addVertex(ax - ox, ay - oy, az - oz).setColor(from);
        builder.addVertex(bx + ox, by + oy, bz + oz).setColor(to);
        builder.addVertex(bx - ox, by - oy, bz - oz).setColor(to);
        return 1;
    }
}
