package pub.godweer.util.target;

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
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer.Sprite;
import pub.godweer.module.impl.render.TargetEsp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Target ESP "Lightning": разряды бьют не сверху, а рождаются на самом корпусе цели и
 * уходят от него во все стороны короткими дугами. Точка старта — поверхность цилиндра
 * хитбокса (случайная высота и случайный азимут), направление — сферическое с ограничением
 * по вертикали, поэтому вокруг цели держится шевелящаяся Coronа из коротких разрядов.
 *
 * Форма канала — midpoint displacement: середина каждого пролёта отводится в случайном
 * перпендикулярном направлении, на каждом уровне вдвое слабее. Глубина 3 — восемь сегментов
 * на канал; амплитуда отвода считается от длины канала, поэтому «Излом» остаётся пропорцией
 * и не превращает дугу в клубок на большой длине.
 *
 * Точки помнятся ЛОКАЛЬНО относительно ног цели и каждый кадр перепривязываются к её
 * интерполированной позиции: разряд ползёт вместе с телом, как в оригинале. Когда цель
 * умирает или исчезает из виду, позиция замораживается на последнем теле — доживать век
 * каналы уже летят сами по себе.
 *
 * Яркость: общее появление эффекта (0.2 с на входе, 0.45 с на выходе) с ease-out кубиком,
 * поверх — линейное затухание по возрасту канала и покадровый тремор яркости. Ядро белое,
 * к внешнему концу канал перетекает в акцентный цвет; в каждой вершине — узел свечения.
 * Линии рисуем не DEBUG_LINES, а лентами из двух слоёв (широкая размытая + тонкое ядро),
 * повёрнутыми к камере: так толщина не зависит от GL_LINE_WIDTH и не рвётся на наклонных
 * сегментах.
 *
 * Create by daun kvass
 */
public final class LightningEspRenderer {

    private static final int MAX_BOLTS = 16;
    // 2^3 = восемь сегментов на канал, то есть девять опорных точек.
    private static final int DEPTH = 3;
    private static final int MAX_POINTS = (1 << DEPTH) + 1;
    private static final int SPAWN_PER_CYCLE = 2;

    // Каденция спавна в секундах и жизнь одного канала — из эталона (40 мс, 130-270 мс).
    private static final double SPAWN_INTERVAL = 0.04D;
    private static final double LIFE_MIN = 0.13D;
    private static final double LIFE_MAX = 0.27D;
    // Зазор от поверхности хитбокса, чтобы старт канала не тонул в теле.
    private static final double SURFACE_CLEARANCE = 0.05D;
    private static final double FADE_IN_SECONDS = 0.20D;
    private static final double FADE_OUT_SECONDS = 0.45D;
    // Тремор яркости: нижняя граница множителя, верх — 1.0.
    private static final float FLICKER_FLOOR = 0.75F;
    // Максимальный отвод середины пролёта как доля длины канала при «Изломе» по умолчанию.
    private static final double DISPERSION_PER_JITTER = 0.45D;
    // Ползунки «Длина» и «Разброс» оставлены в прежней шкале (1.5-12 и 0.1-4), чтобы
    // сохранённые конфиги дали эталонный размер: 6.5 ≈ 0.85 блока, 1.35 ≈ 0.14 блока.
    private static final double LENGTH_PER_SETTING = 0.13D;
    private static final double CLEARANCE_PER_SETTING = 0.10D;

    // widths in world blocks, before the thickness setting
    private static final float HALO_WIDTH = 0.055F;
    private static final float CORE_WIDTH = 0.018F;
    private static final float HALO_ALPHA = 0.34F;
    private static final float NODE_SIZE = 0.10F;
    private static final float NODE_ALPHA = 0.35F;

    private static final int WHITE_BLUE = 0xFFF2FAFF;
    private static final int HURT_TINT = 0xFFFF4040;
    private static final float BLOOM_BRIGHTNESS = 1.45F;
    private static final double MAX_DELTA_SECONDS = 0.05D;

    // худший случай: восемь сегментов, два слоя по два треугольника
    private static final int QUADS_PER_BOLT = (MAX_POINTS - 1) * 2;

    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/lightning_esp"))
            .withVertexShader(Identifier.parse("godweer:core/lines"))
            .withFragmentShader(Identifier.parse("godweer:core/lines"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private final Bolt[] bolts = new Bolt[MAX_BOLTS];
    private final Random random = new Random(0x197DC0DE5EEDL);
    private final ParticlesWorldRenderer particles = new ParticlesWorldRenderer();
    private final List<Sprite> sprites = new ArrayList<>(MAX_BOLTS * MAX_POINTS);

    private double spawnDelay = SPAWN_INTERVAL;
    private double time;
    private long lastFrameNanos;
    private int lastTargetId = Integer.MIN_VALUE;
    // 0..1: сколько эффекта нарисовано при текущем состоянии цели.
    private double animation;
    // Тело, к ногам которого привязаны локальные точки каналов.
    private Vec3 bodyBase;

    private BufferBuilder meshBuilder;
    private int vertices;
    private GpuBuffer vertexBuffer;
    private GpuBuffer transformBuffer;

    public LightningEspRenderer() {
        for (int i = 0; i < MAX_BOLTS; i++) {
            this.bolts[i] = new Bolt();
        }
    }

    public void render(TargetEsp module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) return;

        long nowNanos = System.nanoTime();
        double dt = this.lastFrameNanos == 0L
                ? 1.0 / 60.0
                : Math.min((nowNanos - this.lastFrameNanos) / 1.0E9, MAX_DELTA_SECONDS);
        this.lastFrameNanos = nowNanos;
        this.time += dt;

        LivingEntity target = module.resolveTarget();
        boolean present = target != null && target.isAlive() && !target.isRemoved();
        if (present) {
            if (target.getId() != this.lastTargetId) {
                // Новая цель: старые каналы привязаны к чужому телу, а координаты-то локальные.
                clearBolts();
                this.lastTargetId = target.getId();
                this.spawnDelay = 0.0D;
            }
            this.bodyBase = Render3DUtil.interpolatedPosition(target, tickDelta);
            this.animation = Math.min(1.0D, this.animation + dt / FADE_IN_SECONDS);

            this.spawnDelay -= dt;
            if (this.spawnDelay <= 0.0D) {
                for (int i = 0; i < SPAWN_PER_CYCLE; i++) {
                    spawn(target, module);
                }
                // Пауза между пакетами не метроном: ровная сетка спавна читается как софт.
                this.spawnDelay = SPAWN_INTERVAL / Math.max(0.2F, module.lightningSpeed.get())
                        * (0.75D + this.random.nextDouble() * 0.5D);
            }
        } else {
            this.lastTargetId = Integer.MIN_VALUE;
            this.animation = Math.max(0.0D, this.animation - dt / FADE_OUT_SECONDS);
        }

        // Цель ещё ни разу не была в кадре — привязывать локальные точки не к чему.
        if (this.bodyBase == null) {
            clearBolts();
            return;
        }
        renderBolts(mc, module, target);
    }

    private void spawn(LivingEntity target, TargetEsp module) {
        Bolt free = null;
        int live = 0;
        for (Bolt bolt : this.bolts) {
            if (!bolt.active) {
                if (free == null) free = bolt;
            } else {
                live++;
            }
        }
        int allowed = Mth.clamp(Math.round(module.lightningCount.get()), 1, MAX_BOLTS);
        if (free == null || live >= allowed) return;

        double height = target.getBbHeight();
        double radius = target.getBbWidth() * 0.5D + SURFACE_CLEARANCE
                + module.lightningRadius.get() * CLEARANCE_PER_SETTING;

        // Старт на боковой поверхности цилиндра хитбокса, на случайной высоте тела.
        double azimuth = this.random.nextDouble() * Math.PI * 2.0D;
        double startX = Math.cos(azimuth) * radius;
        double startY = this.random.nextDouble() * height;
        double startZ = Math.sin(azimuth) * radius;

        // Направление по сфере, вертикаль ограничена ~54°: без ограничения половина
        // разрядов складывается в плоскую лепёшку над головой и под ногами.
        double yaw = this.random.nextDouble() * Math.PI * 2.0D;
        double pitch = (this.random.nextDouble() - 0.5D) * Math.PI * 0.6D;
        double dirX = Math.cos(pitch) * Math.cos(yaw);
        double dirY = Math.sin(pitch);
        double dirZ = Math.cos(pitch) * Math.sin(yaw);

        double length = module.lightningLength.get() * LENGTH_PER_SETTING
                * (0.55D + this.random.nextDouble() * 0.9D);
        double endX = startX + dirX * length;
        double endY = startY + dirY * length;
        double endZ = startZ + dirZ * length;

        free.count = 0;
        free.push(startX, startY, startZ);
        double dispersion = length * DISPERSION_PER_JITTER
                * Mth.clamp(module.lightningJitter.get(), 0.0F, 1.5F);
        subdivide(free, startX, startY, startZ, endX, endY, endZ, dispersion, DEPTH);

        free.born = this.time;
        free.life = LIFE_MIN + this.random.nextDouble() * (LIFE_MAX - LIFE_MIN);
        free.active = true;
    }

    /**
     * Midpoint displacement: середина пролёта отводится в случайном направлении из
     * плоскости, перпендикулярной ему, потом левая и правая половины рекурсивно. Отвод
     * берётся через два взаимно перпендикулярных вектора к направлению канала, поэтому
     * излом одинаково живой и у вертикального, и у горизонтального разряда.
     */
    private void subdivide(Bolt bolt, double ax, double ay, double az,
                           double bx, double by, double bz, double disp, int depth) {
        if (depth <= 0) {
            bolt.push(bx, by, bz);
            return;
        }

        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double dirLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dirLength < 1.0E-6D || disp <= 0.0D) {
            bolt.push(bx, by, bz);
            return;
        }
        dx /= dirLength;
        dy /= dirLength;
        dz /= dirLength;

        // Репер для перпендикуляра: вдоль оси Y брать X, иначе Y — иначе векторное
        // произведение с параллельным репером даёт ноль и излом схлопывается.
        double refX = Math.abs(dy) < 0.9D ? 0.0D : 1.0D;
        double refY = Math.abs(dy) < 0.9D ? 1.0D : 0.0D;
        double p1x = dy * 0.0D - dz * refY;
        double p1y = dz * refX - dx * 0.0D;
        double p1z = dx * refY - dy * refX;
        double p1Length = Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        if (p1Length < 1.0E-6D) {
            bolt.push(bx, by, bz);
            return;
        }
        p1x /= p1Length;
        p1y /= p1Length;
        p1z /= p1Length;

        // p2 = dir × p1: единичный автоматически, оба множителя уже единичные и ортогональные.
        double p2x = dy * p1z - dz * p1y;
        double p2y = dz * p1x - dx * p1z;
        double p2z = dx * p1y - dy * p1x;

        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double shift = (this.random.nextDouble() - 0.5D) * 2.0D * disp;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double midX = (ax + bx) * 0.5D + (cos * p1x + sin * p2x) * shift;
        double midY = (ay + by) * 0.5D + (cos * p1y + sin * p2y) * shift;
        double midZ = (az + bz) * 0.5D + (cos * p1z + sin * p2z) * shift;

        subdivide(bolt, ax, ay, az, midX, midY, midZ, disp * 0.5D, depth - 1);
        subdivide(bolt, midX, midY, midZ, bx, by, bz, disp * 0.5D, depth - 1);
    }

    private void renderBolts(Minecraft mc, TargetEsp module, LivingEntity target) {
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        if (Render3DUtil.levelProjectionCopy() == null) return;

        int accent = module.getAccentColor();
        if (module.lightningHurt.get() && target != null) {
            float hurt = Math.max(0.0F, target.hurtTime - tickDeltaOf(mc));
            accent = ColorUtil.lerp(accent, HURT_TINT,
                    (float) Math.sin(hurt * (Math.PI / 10.0)) * 0.5F);
        }

        this.sprites.clear();
        this.meshBuilder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(MAX_BOLTS * QUADS_PER_BOLT * 6
                        * DefaultVertexFormat.POSITION_COLOR.getVertexSize(), 32 * 1024)),
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        this.vertices = 0;

        Vec3 cam = camera.position();
        Vec3 base = this.bodyBase;
        float widthScale = module.lightningWidth.get();
        boolean glow = module.lightningGlow.get();
        // Ease-out кубик: эффект входит быстро и последние десятые доли доливает плавно.
        float global = (float) (1.0D - Math.pow(1.0D - this.animation, 3.0D));
        boolean any = false;

        for (Bolt bolt : this.bolts) {
            if (!bolt.active) continue;
            double age = bolt.life <= 0.0D ? 1.0D : (this.time - bolt.born) / bolt.life;
            if (age >= 1.0D) {
                bolt.active = false;
                continue;
            }
            // Поверх затухания — покадровый тремор: у настоящей плазмы яркость скачет,
            // ровная линия затухания читается как градиент нарисованный софтом.
            float flicker = FLICKER_FLOOR + (1.0F - FLICKER_FLOOR) * this.random.nextFloat();
            float alpha = global * (float) (1.0D - age) * flicker;
            if (alpha < 0.02F) continue;
            any = true;

            channel(bolt, base, cam, alpha, accent, widthScale, glow);
        }

        if (!any) {
            this.meshBuilder = null;
            return;
        }
        if (this.vertices > 0) submit(camera);
        if (!this.sprites.isEmpty()) {
            this.particles.render(this.sprites, BLOOM_BRIGHTNESS, true, true);
        }
    }

    /** Один канал: две ленты поверх ломаной и узел свечения в каждой вершине. */
    private void channel(Bolt bolt, Vec3 base, Vec3 cam, float alpha, int accent,
                         float widthScale, boolean glow) {
        double[] points = bolt.points;
        int count = bolt.count;
        for (int i = 0; i < count - 1; i++) {
            float t0 = i / (float) (count - 1);
            float t1 = (i + 1) / (float) (count - 1);
            int at0 = i * 3;
            int at1 = (i + 1) * 3;

            double x1 = base.x + points[at0];
            double y1 = base.y + points[at0 + 1];
            double z1 = base.z + points[at0 + 2];
            double x2 = base.x + points[at1];
            double y2 = base.y + points[at1 + 1];
            double z2 = base.z + points[at1 + 2];

            int from = channelColor(accent, t0, alpha, 1.0F);
            int to = channelColor(accent, t1, alpha, 1.0F);

            ribbon(x1, y1, z1, x2, y2, z2, cam, HALO_WIDTH * widthScale,
                    ColorUtil.multiplyAlpha(from, HALO_ALPHA),
                    ColorUtil.multiplyAlpha(to, HALO_ALPHA));
            ribbon(x1, y1, z1, x2, y2, z2, cam, CORE_WIDTH * widthScale, from, to);

            if (glow) {
                this.sprites.add(new Sprite(new Vec3(x2, y2, z2),
                        NODE_SIZE + NODE_SIZE * t1,
                        ColorUtil.multiplyAlpha(WHITE_BLUE, alpha * NODE_ALPHA)));
            }
        }
    }

    /** Бело-голубое ядро у тела, к внешнему концу канала перетекающее в акцентный цвет. */
    private static int channelColor(int accent, float mix, float alpha, float brightness) {
        int base = ColorUtil.lerp(WHITE_BLUE, accent, mix);
        int r = Math.min(255, Math.round(ColorUtil.red(base) * brightness));
        int g = Math.min(255, Math.round(ColorUtil.green(base) * brightness));
        int b = Math.min(255, Math.round(ColorUtil.blue(base) * brightness));
        return ColorUtil.pack(r, g, b, Math.round(255.0F * Mth.clamp(alpha, 0.0F, 1.0F)));
    }

    /** Лента между двумя точками, повёрнутая к камере: 2 треугольника на сегмент. */
    private void ribbon(double x1, double y1, double z1, double x2, double y2, double z2,
                        Vec3 cam, float width, int from, int to) {
        if (width <= 0.0F || (ColorUtil.alpha(from) <= 0 && ColorUtil.alpha(to) <= 0)) return;

        double sx = x2 - x1;
        double sy = y2 - y1;
        double sz = z2 - z1;
        double vx = (x1 + x2) * 0.5D - cam.x;
        double vy = (y1 + y2) * 0.5D - cam.y;
        double vz = (z1 + z2) * 0.5D - cam.z;

        double nx = sy * vz - sz * vy;
        double ny = sz * vx - sx * vz;
        double nz = sx * vy - sy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-8D || !Double.isFinite(nl)) return;

        float half = width * 0.5F;
        float ox = (float) (nx / nl * half);
        float oy = (float) (ny / nl * half);
        float oz = (float) (nz / nl * half);

        float ax = (float) (x1 - cam.x);
        float ay = (float) (y1 - cam.y);
        float az = (float) (z1 - cam.z);
        float bx = (float) (x2 - cam.x);
        float by = (float) (y2 - cam.y);
        float bz = (float) (z2 - cam.z);

        BufferBuilder builder = this.meshBuilder;
        builder.addVertex(ax - ox, ay - oy, az - oz).setColor(from);
        builder.addVertex(ax + ox, ay + oy, az + oz).setColor(from);
        builder.addVertex(bx + ox, by + oy, bz + oz).setColor(to);

        builder.addVertex(ax - ox, ay - oy, az - oz).setColor(from);
        builder.addVertex(bx + ox, by + oy, bz + oz).setColor(to);
        builder.addVertex(bx - ox, by - oy, bz - oz).setColor(to);
        this.vertices += 6;
    }

    /** Дробная часть тика для сглаживания вспышки урона. */
    private static float tickDeltaOf(Minecraft mc) {
        return mc.getDeltaTracker() == null
                ? 0.0F
                : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private void clearBolts() {
        for (Bolt bolt : this.bolts) {
            bolt.active = false;
            bolt.count = 0;
            bolt.life = 0.0D;
        }
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

            var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (screen == null || screen.getColorTextureView() == null) return;

            ensureVertexCapacity(byteCount);
            device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, byteCount), vertexData);

            Matrix4f mvp = new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));
            ensureTransformBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(mvp).get();
                device.createCommandEncoder().writeToBuffer(this.transformBuffer.slice(), data);
            }

            try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                    () -> "Godweer Lightning ESP",
                    screen.getColorTextureView(),
                    Optional.empty())) {
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
                () -> "Godweer Lightning ESP UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                TRANSFORM_SIZE);
    }

    private void ensureVertexCapacity(int byteSize) {
        if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Godweer Lightning ESP Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                capacity);
    }

    public void release() {
        clearBolts();
        this.time = 0.0D;
        this.animation = 0.0D;
        this.spawnDelay = SPAWN_INTERVAL;
        this.lastFrameNanos = 0L;
        this.lastTargetId = Integer.MIN_VALUE;
        this.bodyBase = null;
        this.vertices = 0;
        this.meshBuilder = null;
        this.sprites.clear();
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
        if (this.transformBuffer != null) {
            this.transformBuffer.close();
            this.transformBuffer = null;
        }
    }

    /**
     * Канал: опорные точки в ЛОКАЛЬНЫХ координатах относительно ног цели. Перепривязка к
     * телу происходит на кадре отрисовки, поэтому разряд живёт вместе с целью, а не висит
     * в мире.
     */
    private static final class Bolt {
        final double[] points = new double[MAX_POINTS * 3];
        int count;
        boolean active;
        double born;
        double life;

        void push(double x, double y, double z) {
            if (this.count >= MAX_POINTS) return;
            int at = this.count * 3;
            this.points[at] = x;
            this.points[at + 1] = y;
            this.points[at + 2] = z;
            this.count++;
        }
    }
}
