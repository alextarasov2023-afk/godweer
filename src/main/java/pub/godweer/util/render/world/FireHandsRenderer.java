package pub.godweer.util.render.world;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.pipeline.PiplinePost;
import pub.godweer.util.render.pipeline.Post;
import pub.godweer.module.impl.render.FireHands;

import pub.godweer.util.render.world.HandFireSettings;

import java.util.Optional;

/**
 * Fire Hands — анимированное пламя, свечение и огненный шлейф вокруг рук
 * и предметов от первого лица.
 *
 * Пайплайн каждого кадра:
 *   1) prepareSnapshot(): копия цвета экрана ДО рук -> beforeColor
 *   2) ванилла рисует руки
 *   3) compose():
 *      - копия цвета ПОСЛЕ рук        -> afterColor
 *      - Trail Pass: |after - before| = маска рук + история шлейфа (ping-pong)
 *      - Composite Pass: окраска, шум пламени, bloom, учёт глубины -> основной экран
 *
 * Портировано с 1.21.x (сырой OpenGL) на renderpearl-пайплайн 26.3.
 */
public final class FireHandsRenderer {
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static final GpuFormat COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final GpuFormat TRAIL_FORMAT = GpuFormat.RGBA16_FLOAT;

    private static final int TRAIL_SIZE = 3 * 16;
    private static final int COMPOSITE_SIZE = 6 * 16;

    public static final int MODE_ITEM = 0;
    public static final int MODE_CUSTOM = 1;
    public static final int MODE_CLIENT = 2;
    public static final int MODE_RAINBOW = 3;

    private GpuBuffer trailUniforms;
    private GpuBuffer compositeUniforms;

    private TextureTarget beforeColor;
    private TextureTarget afterColor;
    private final TextureTarget[] trail = new TextureTarget[2];
    private int ping = 0;

    private int width;
    private int height;
    private int preparedWidth;
    private int preparedHeight;
    private boolean prepared;
    private boolean pendingTrailClear = true;
    private long lastFrameNanos;

    /** Этап 1: вызывается ДО открытия vanilla-пасса рук (encoder свободен). */
    public void prepareSnapshot(HandFireSettings module) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (!shouldRun(module, mainTarget)) {
            this.prepared = false;
            this.lastFrameNanos = 0L;
            // Руки не рисуются (третье лицо, F1, пустые руки): старую историю
            // шлейфа не храним — иначе при возврате в первое лицо она вспыхнет
            // призрачным огнём там, где руки были много секунд назад.
            this.pendingTrailClear = true;
            return;
        }

        initBuffers();
        ensureTargets(mainTarget.width, mainTarget.height);
        copyMainTo(mainTarget, this.beforeColor, "fire hands before");
        this.preparedWidth = mainTarget.width;
        this.preparedHeight = mainTarget.height;
        this.prepared = true;
    }

    /** Этап 3: вызывается ПОСЛЕ vanilla-пасса рук. */
    public void compose(HandFireSettings module) {
        if (!this.prepared) {
            return;
        }
        this.prepared = false;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (!valid(mainTarget)) {
            this.pendingTrailClear = true;
            return;
        }
        // Снимок «до рук» сделан в другом разрешении (ресайз окна между
        // этапами): маска рук из такой пары кадров накрыла бы весь экран.
        if (mainTarget.width != this.preparedWidth || mainTarget.height != this.preparedHeight) {
            this.pendingTrailClear = true;
            this.lastFrameNanos = 0L;
            return;
        }
        ensureTargets(mainTarget.width, mainTarget.height);

        float elapsed = this.lastFrameNanos == 0L ? 0.0F
                : (float) ((now() - this.lastFrameNanos) / 1.0E9);
        // Долгая пауза (окно, лаг, переключение модуля): старый шлейф уже не
        // соответствует положению рук, начинаем историю заново.
        if (elapsed > 0.5F) {
            this.pendingTrailClear = true;
        }

        if (this.pendingTrailClear) {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            for (TextureTarget target : this.trail) {
                encoder.clearColorTexture(target.getColorTexture(), CLEAR);
            }
            this.pendingTrailClear = false;
            this.lastFrameNanos = 0L;
        }

        copyMainTo(mainTarget, this.afterColor, "fire hands after");

        float dt = this.lastFrameNanos == 0L ? 1.0F / 60.0F
                : (float) ((now() - this.lastFrameNanos) / 1.0E9);
        dt = Mth.clamp(dt, 1.0E-3F, 0.1F);
        this.lastFrameNanos = now();
        float frameScale = dt * 60.0F;
        float decay = (float) Math.pow(0.02, dt / Math.max(module.getTrailLength(), 0.05F));

        trailPass(module, decay, frameScale);
        compositePass(module, mainTarget);
    }

    /** Сбросить историю шлейфа (безопасный старт). */
    public void resetTrails() {
        this.pendingTrailClear = true;
        this.lastFrameNanos = 0L;
    }

    private void trailPass(HandFireSettings module, float decay, float frameScale) {
        int src = this.ping;
        int dst = 1 - this.ping;

        Post.writeUniforms(this.trailUniforms, TRAIL_SIZE, builder -> builder
                .putVec4(1.0F / this.width, 1.0F / this.height, Post.shaderTime(), this.height / 1080.0F)
                .putVec4(module.getRise(), module.getWobble(), module.getTurbulence(), decay)
                .putVec4(module.getAuraWidth(), module.getFireSpread(), module.getStrength(), frameScale)
        );

        try (RenderPass pass = renderPass("fire hands trail", this.trail[dst])) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.FIRE_HANDS_TRAIL));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FireTrailUniforms", this.trailUniforms);
            pass.setUniform("BeforeColor", this.beforeColor.getColorTextureView(), Post.linearSampler());
            pass.setUniform("AfterColor", this.afterColor.getColorTextureView(), Post.linearSampler());
            pass.setUniform("History", this.trail[src].getColorTextureView(), Post.linearSampler());
            draw(pass);
        }
        this.ping = dst; // ping-pong: новый кадр становится историей
    }

    private void compositePass(HandFireSettings module, RenderTarget mainTarget) {
        float[] custom = ColorUtil.getRGBAFloat(module.getCustomColor());
        float[] clientA = ColorUtil.getRGBAFloat(module.getClientColorA());
        float[] clientB = ColorUtil.getRGBAFloat(module.getClientColorB());
        boolean hasDepth = mainTarget.getDepthTexture() != null;

        Post.writeUniforms(this.compositeUniforms, COMPOSITE_SIZE, builder -> builder
                .putVec4(custom[0], custom[1], custom[2], custom[3])
                .putVec4(clientA[0], clientA[1], clientA[2], clientA[3])
                .putVec4(clientB[0], clientB[1], clientB[2], clientB[3])
                .putVec4(1.0F / this.width, 1.0F / this.height, Post.shaderTime(), 0.0F)
                .putVec4(this.height / 1080.0F, module.getRise(), module.getBrightness(), module.getFireSpread())
                .putVec4(module.getRainbowPeriod(), module.getPulseSpeed(), module.getModeIndex(),
                        hasDepth ? 1.0F : 0.0F)
        );

        try (RenderPass pass = renderPass("fire hands composite", mainTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.FIRE_HANDS_COMPOSITE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FireCompositeUniforms", this.compositeUniforms);
            pass.setUniform("TrailSampler", this.trail[this.ping].getColorTextureView(), Post.linearSampler());
            pass.setUniform("BeforeColor", this.beforeColor.getColorTextureView(), Post.linearSampler());
            pass.setUniform("AfterColor", this.afterColor.getColorTextureView(), Post.linearSampler());
            if (hasDepth) {
                pass.setUniform("DepthSampler", mainTarget.getDepthTextureView(), Post.nearestSampler());
            }
            draw(pass);
        }
    }

    /** Копия цвета основного экрана: render pass сэмплирует view главного таргета (как MenuBacks.captureNow). */
    private static void copyMainTo(RenderTarget source, TextureTarget destination, String label) {
        try (RenderPass pass = renderPass(label, destination)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.FIRE_HANDS_COPY));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("SourceSampler", source.getColorTextureView(), Post.linearSampler());
            draw(pass);
        }
    }

    private boolean shouldRun(HandFireSettings module, RenderTarget mainTarget) {
        if (!valid(mainTarget)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        if (module.onlyWithItems()
                && mc.player.getMainHandItem().isEmpty()
                && mc.player.getOffhandItem().isEmpty()) {
            return false;
        }
        return true;
    }

    private void initBuffers() {
        if (this.trailUniforms == null) this.trailUniforms = Post.createUniforms("FireHands Trail UBO", TRAIL_SIZE);
        if (this.compositeUniforms == null) this.compositeUniforms = Post.createUniforms("FireHands Composite UBO", COMPOSITE_SIZE);
    }

    private void ensureTargets(int width, int height) {
        if (width == this.width && height == this.height
                && this.beforeColor != null && this.afterColor != null
                && this.trail[0] != null && this.trail[1] != null) {
            return;
        }
        releaseTargets();
        this.width = width;
        this.height = height;
        this.beforeColor = new TextureTarget("fire hands before", width, height, COLOR_FORMAT, null);
        this.afterColor = new TextureTarget("fire hands after", width, height, COLOR_FORMAT, null);
        this.trail[0] = new TextureTarget("fire hands trail a", width, height, TRAIL_FORMAT, null);
        this.trail[1] = new TextureTarget("fire hands trail b", width, height, TRAIL_FORMAT, null);
        this.ping = 0;
        this.pendingTrailClear = true;
    }

    private static RenderPass renderPass(String label, RenderTarget target) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label,
                target.getColorTextureView(),
                Optional.empty()
        );
    }

    private static void draw(RenderPass pass) {
        pass.setVertexBuffer(0, pub.godweer.util.render.pipeline.FullscreenQuad.buffer().slice());
        pass.draw(pub.godweer.util.render.pipeline.FullscreenQuad.vertexCount(), 1, 0, 0);
    }

    private static boolean valid(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0
                && target.getColorTexture() != null && target.getColorTextureView() != null;
    }

    private static long now() {
        return System.nanoTime();
    }

    public void release() {
        releaseTargets();
        if (this.trailUniforms != null) this.trailUniforms.close();
        if (this.compositeUniforms != null) this.compositeUniforms.close();
        this.trailUniforms = null;
        this.compositeUniforms = null;
        this.lastFrameNanos = 0L;
        this.pendingTrailClear = true;
    }

    private void releaseTargets() {
        if (this.beforeColor != null) this.beforeColor.destroyBuffers();
        if (this.afterColor != null) this.afterColor.destroyBuffers();
        if (this.trail[0] != null) this.trail[0].destroyBuffers();
        if (this.trail[1] != null) this.trail[1].destroyBuffers();
        this.beforeColor = null;
        this.afterColor = null;
        this.trail[0] = null;
        this.trail[1] = null;
        this.width = 0;
        this.height = 0;
    }
}
