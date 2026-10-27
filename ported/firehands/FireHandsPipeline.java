package ez.falseclient.utils.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import ez.falseclient.system.features.render.FireHands;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.OptionalDouble;

/**
 * Fire Hands — анимированное пламя, свечение и огненный шлейф вокруг рук
 * и предметов от первого лица. Порт pub.godweer FireHandsRenderer на
 * render-пайплайн Fabric 1.21.x.
 *
 * Пайплайн каждого кадра:
 *   1) prepareSnapshot(): копия цвета экрана ДО рук -> beforeColor
 *   2) ванилла рисует руки
 *   3) compose():
 *      - копия цвета ПОСЛЕ рук        -> afterColor
 *      - Trail Pass: |after - before| = маска рук + история шлейфа (ping-pong)
 *      - Composite Pass: окраска, шум пламени, bloom, учёт глубины -> основной экран
 */
public final class FireHandsPipeline {
    private static final int TRAIL_UNIFORM_SIZE = 64;
    private static final int COMPOSITE_UNIFORM_SIZE = 128;

    // Registered on first use so resource reloads don't precompile our custom shaders.
    private static RenderPipeline PIPELINE_TRAIL;
    private static RenderPipeline PIPELINE_COMPOSITE;

    private static void ensurePipelines() {
        if (PIPELINE_COMPOSITE != null) {
            return;
        }
        PIPELINE_TRAIL = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of("false", "fire_hands_trail"))
                        .withVertexShader(Identifier.of("false", "fire_hands_vertex"))
                        .withFragmentShader(Identifier.of("false", "fire_trail_fragment"))
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("FireTrailUniforms", UniformType.UNIFORM_BUFFER)
                        .withSampler("BeforeColor")
                        .withSampler("AfterColor")
                        .withSampler("History")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());

        PIPELINE_COMPOSITE = RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.of("false", "fire_hands_composite"))
                        .withVertexShader(Identifier.of("false", "fire_hands_vertex"))
                        .withFragmentShader(Identifier.of("false", "fire_composite_fragment"))
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("FireCompositeUniforms", UniformType.UNIFORM_BUFFER)
                        .withSampler("TrailSampler")
                        .withSampler("BeforeColor")
                        .withSampler("AfterColor")
                        .withSampler("DepthSampler")
                        .withBlend(BlendFunction.ADDITIVE)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
    }

    private static GpuBuffer trailUniforms;
    private static GpuBuffer compositeUniforms;
    private static GpuBuffer dummyVertexBuffer;

    private static SimpleFramebuffer beforeColor;
    private static SimpleFramebuffer afterColor;
    private static final SimpleFramebuffer[] trail = new SimpleFramebuffer[2];
    private static int ping = 0;

    private static int width;
    private static int height;
    private static boolean prepared;
    private static boolean pendingTrailClear = true;
    private static long lastFrameNanos;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final org.joml.Vector3f MODEL_OFFSET = new org.joml.Vector3f(0, 0, 0);
    private static final org.joml.Matrix4f TEXTURE_MATRIX = new org.joml.Matrix4f();

    private FireHandsPipeline() {
    }

    /** Этап 1: вызывается ДО vanilla-рендера рук. */
    public static void prepareSnapshot(FireHands module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (!shouldRun(module, main)) {
            prepared = false;
            lastFrameNanos = 0L;
            return;
        }

        ensureBuffers();
        ensureTargets(main.textureWidth, main.textureHeight);
        if (beforeColor == null) {
            prepared = false;
            return;
        }
        copyMainTo(main, beforeColor, "false:fire_hands_before");
        prepared = true;
    }

    /** Этап 3: вызывается ПОСЛЕ vanilla-рендера рук. */
    public static void compose(FireHands module) {
        if (!prepared) {
            return;
        }
        prepared = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (!valid(main)) {
            return;
        }
        ensureTargets(main.textureWidth, main.textureHeight);
        if (afterColor == null || trail[0] == null || trail[1] == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (pendingTrailClear) {
            encoder.clearColorTexture(trail[0].getColorAttachment(), 0);
            encoder.clearColorTexture(trail[1].getColorAttachment(), 0);
            pendingTrailClear = false;
            lastFrameNanos = 0L;
        }

        copyMainTo(main, afterColor, "false:fire_hands_after");

        float dt = lastFrameNanos == 0L ? 1.0F / 60.0F
                : (float) ((System.nanoTime() - lastFrameNanos) / 1.0E9);
        dt = MathHelper.clamp(dt, 1.0E-3F, 0.1F);
        lastFrameNanos = System.nanoTime();
        float frameScale = dt * 60.0F;
        float decay = (float) Math.pow(0.02, dt / Math.max(module.getTrailLength(), 0.05F));

        ensurePipelines();
        trailPass(module, encoder, decay, frameScale);
        compositePass(module, main, encoder);
    }

    /** Сбросить историю шлейфа (безопасный старт). */
    public static void resetTrails() {
        pendingTrailClear = true;
        lastFrameNanos = 0L;
    }

    private static void trailPass(FireHands module, CommandEncoder encoder, float decay, float frameScale) {
        int src = ping;
        int dst = 1 - ping;

        ByteBuffer data = MemoryUtil.memAlloc(TRAIL_UNIFORM_SIZE);
        data.putFloat(1.0F / width).putFloat(1.0F / height).putFloat(shaderTime()).putFloat(height / 1080.0F);
        data.putFloat(module.getRise()).putFloat(module.getWobble()).putFloat(module.getTurbulence()).putFloat(decay);
        data.putFloat(module.getAuraWidth()).putFloat(module.getFireSpread()).putFloat(module.getStrength()).putFloat(frameScale);
        for (int i = 0; i < 4; i++) data.putFloat(0.0F); // дополнение до std140-размера буфера
        data.flip();
        encoder.writeToBuffer(trailUniforms.slice(), data);
        MemoryUtil.memFree(data);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "false:fire_hands_trail",
                trail[dst].getColorAttachmentView(),
                OptionalInt.empty(),
                null,
                OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE_TRAIL);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("BeforeColor", beforeColor.getColorAttachmentView(), sampler);
            pass.bindTexture("AfterColor", afterColor.getColorAttachmentView(), sampler);
            pass.bindTexture("History", trail[src].getColorAttachmentView(), sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FireTrailUniforms", trailUniforms);
            pass.draw(0, 3);
        }
        ping = dst; // ping-pong: новый кадр становится историей
    }

    private static void compositePass(FireHands module, Framebuffer main, CommandEncoder encoder) {
        float[] custom = rgba(module.getCustomColor());
        float[] clientA = rgba(module.getClientColorA());
        float[] clientB = rgba(module.getClientColorB());
        boolean hasDepth = main.getDepthAttachment() != null;

        ByteBuffer data = MemoryUtil.memAlloc(COMPOSITE_UNIFORM_SIZE);
        putVec4(data, custom[0], custom[1], custom[2], custom[3]);
        putVec4(data, clientA[0], clientA[1], clientA[2], clientA[3]);
        putVec4(data, clientB[0], clientB[1], clientB[2], clientB[3]);
        putVec4(data, 1.0F / width, 1.0F / height, shaderTime(), 0.0F);
        putVec4(data, height / 1080.0F, module.getRise(), module.getBrightness(), module.getFireSpread());
        putVec4(data, module.getRainbowPeriod(), module.getPulseSpeed(), module.getModeIndex(), hasDepth ? 1.0F : 0.0F);
        for (int i = 0; i < 8; i++) data.putFloat(0.0F); // дополнение до std140-размера буфера
        data.flip();
        encoder.writeToBuffer(compositeUniforms.slice(), data);
        MemoryUtil.memFree(data);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "false:fire_hands_composite",
                main.getColorAttachmentView(),
                OptionalInt.empty(),
                null,
                OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE_COMPOSITE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("TrailSampler", trail[ping].getColorAttachmentView(), sampler);
            pass.bindTexture("BeforeColor", beforeColor.getColorAttachmentView(), sampler);
            pass.bindTexture("AfterColor", afterColor.getColorAttachmentView(), sampler);
            if (hasDepth) {
                pass.bindTexture("DepthSampler", main.getDepthAttachmentView(),
                        RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FireCompositeUniforms", compositeUniforms);
            pass.draw(0, 3);
        }
    }

    /** Копия цвета основного экрана в целевой фреймбуфер (GPU-копия, как в BlurPipeline). */
    private static void copyMainTo(Framebuffer source, SimpleFramebuffer destination, String label) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                source.getColorAttachment(),
                destination.getColorAttachment(),
                0, 0, 0, 0, 0,
                source.textureWidth, source.textureHeight);
    }

    private static boolean shouldRun(FireHands module, Framebuffer main) {
        if (!valid(main)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;
        if (mc.options.getCameraType().isFirstPerson() == false) return false;
        if (module.onlyWithItems()
                && mc.player.getMainHandStack().isEmpty()
                && mc.player.getOffHandStack().isEmpty()) {
            return false;
        }
        return true;
    }

    private static void ensureBuffers() {
        if (trailUniforms == null) {
            trailUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "false:fire_hands_trail_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    TRAIL_UNIFORM_SIZE);
        }
        if (compositeUniforms == null) {
            compositeUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "false:fire_hands_composite_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    COMPOSITE_UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "false:fire_hands_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
        }
    }

    private static void ensureTargets(int w, int h) {
        if (w == width && h == height && beforeColor != null && afterColor != null
                && trail[0] != null && trail[1] != null) {
            return;
        }
        releaseTargets();
        width = w;
        height = h;
        beforeColor = new SimpleFramebuffer("false:fire_hands_before", w, h, false);
        afterColor = new SimpleFramebuffer("false:fire_hands_after", w, h, false);
        trail[0] = new SimpleFramebuffer("false:fire_hands_trail_a", w, h, false);
        trail[1] = new SimpleFramebuffer("false:fire_hands_trail_b", w, h, false);
        ping = 0;
        pendingTrailClear = true;
    }

    private static boolean valid(Framebuffer target) {
        return target != null && target.textureWidth > 0 && target.textureHeight > 0
                && target.getColorAttachment() != null && target.getColorAttachmentView() != null;
    }

    private static float[] rgba(java.awt.Color color) {
        return new float[]{
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                color.getAlpha() / 255.0F
        };
    }

    private static void putVec4(ByteBuffer data, float x, float y, float z, float w) {
        data.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private static float shaderTime() {
        return (System.currentTimeMillis() % 1000000L) / 1000.0F;
    }

    public static void shutdown() {
        releaseTargets();
        if (trailUniforms != null) trailUniforms.close();
        if (compositeUniforms != null) compositeUniforms.close();
        if (dummyVertexBuffer != null) dummyVertexBuffer.close();
        trailUniforms = null;
        compositeUniforms = null;
        dummyVertexBuffer = null;
        prepared = false;
        lastFrameNanos = 0L;
        pendingTrailClear = true;
    }

    private static void releaseTargets() {
        if (beforeColor != null) beforeColor.delete();
        if (afterColor != null) afterColor.delete();
        if (trail[0] != null) trail[0].delete();
        if (trail[1] != null) trail[1].delete();
        beforeColor = null;
        afterColor = null;
        trail[0] = null;
        trail[1] = null;
        width = 0;
        height = 0;
    }
}
