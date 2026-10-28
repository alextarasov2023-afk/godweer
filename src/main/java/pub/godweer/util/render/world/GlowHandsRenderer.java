package pub.godweer.util.render.world;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.pipeline.FullscreenQuad;
import pub.godweer.util.render.pipeline.PiplinePost;
import pub.godweer.util.render.pipeline.Post;
import pub.godweer.module.impl.render.GlowHands;

import pub.godweer.util.render.world.HandGlowSettings;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Realistic glow outline renderer for first-person hands.
 *
 * Pipeline:
 * 1. Snapshot the depth buffer before hands are drawn.
 * 2. Draw hands (vanilla).
 * 3. Build a coverage mask from the depth difference.
 * 4. Run a Kawase blur cascade on a half-res chain: tight near-halo
 *    and wide far-halo.
 * 5. Additively composite core rim + near ring + far bloom over the scene.
 */
public final class GlowHandsRenderer {
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int BLUR_DIVISOR = 2;

    private static final int MASK_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final int BLUR_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final int COMPOSITE_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();

    private GpuBuffer maskUniforms;
    private GpuBuffer blurUniforms;
    private GpuBuffer compositeUniforms;

    private TextureTarget beforeTarget;
    private TextureTarget maskTarget;
    private TextureTarget blurA;
    private TextureTarget blurB;
    private TextureTarget blurC;
    private int width;
    private int height;
    private boolean prepared;

    public void prepareSnapshot() {
        // Вызывается ДО создания vanilla-пасса рук: encoder свободен.
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (!valid(mainTarget)) {
            this.prepared = false;
            return;
        }

        initBuffers();
        ensureTargets(mainTarget.width, mainTarget.height);

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                mainTarget.getDepthTexture(),
                this.beforeTarget.getDepthTexture(),
                0, 0, 0, 0, 0,
                this.width,
                this.height
        );
        encoder.clearColorTexture(this.maskTarget.getColorTexture(), CLEAR);
        this.prepared = true;
    }

    public void compose(HandGlowSettings module) {
        // Вызывается ПОСЛЕ закрытия vanilla-пасса рук: глубина уже содержит руки.
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (!this.prepared || !valid(mainTarget)) {
            this.prepared = false;
            return;
        }
        this.prepared = false;

        buildMask(module, mainTarget);
        buildHalo(module);
        composite(module, mainTarget);
    }

    private void buildMask(HandGlowSettings module, RenderTarget mainTarget) {
        boolean left = true;
        boolean right = true;
        Minecraft mc = Minecraft.getInstance();
        if (!module.bothHands() && mc.player != null) {
            left = mc.player.getMainArm() == HumanoidArm.LEFT;
            right = mc.player.getMainArm() == HumanoidArm.RIGHT;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, MASK_SIZE)
                    .putVec4(1.0F / this.width, 1.0F / this.height, left ? 1.0F : 0.0F, right ? 1.0F : 0.0F)
                    .get();
            write(this.maskUniforms, data);
        }

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = renderPass("glow hands mask", this.maskTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.HANDS_GLOW_MASK));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("GlowHandsMaskUniforms", this.maskUniforms);
            pass.setUniform("BeforeDepth", this.beforeTarget.getDepthTextureView(), depthSampler);
            pass.setUniform("AfterDepth", mainTarget.getDepthTextureView(), depthSampler);
            draw(pass);
        }
    }

    private void buildHalo(HandGlowSettings module) {
        float radius = Math.max(module.getRadius(), 1.0F);
        int blurWidth = blurSize(this.width);
        int blurHeight = blurSize(this.height);

        // Mask (full res) -> A: first, tight blur.
        blurTo(this.maskTarget, radius * 0.35F,
                1.0F / this.width, 1.0F / this.height, this.blurA, "glow hands blur 1");
        // A -> B: second level, kept as the near halo sample.
        blurTo(this.blurA, radius * 0.30F,
                1.0F / blurWidth, 1.0F / blurHeight, this.blurB, "glow hands blur 2");
        // B -> A: third level, overwrites A (no longer needed).
        blurTo(this.blurB, radius * 0.50F,
                1.0F / blurWidth, 1.0F / blurHeight, this.blurA, "glow hands blur 3");
        // A -> C: final wide bloom.
        blurTo(this.blurA, radius * 0.80F,
                1.0F / blurWidth, 1.0F / blurHeight, this.blurC, "glow hands blur 4");
    }

    private void blurTo(TextureTarget source, float offsetPx,
                       float srcTexelX, float srcTexelY, TextureTarget output, String label) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, BLUR_SIZE)
                    .putVec4(srcTexelX, srcTexelY, offsetPx, 0.0F)
                    .get();
            write(this.blurUniforms, data);
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = renderPass(label, output)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.HANDS_GLOW_BLUR));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("GlowBlurUniforms", this.blurUniforms);
            pass.setUniform("HaloSampler", source.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void composite(HandGlowSettings module, RenderTarget mainTarget) {
        int color = module.getColor();
        float[] rgba = ColorUtil.getRGBAFloat(color);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, COMPOSITE_SIZE)
                    .putVec4(rgba[0], rgba[1], rgba[2], 1.0F)
                    .putVec4(1.0F / this.width, 1.0F / this.height, Post.shaderTime(), module.getGap())
                    .putVec4(module.getIntensity(), module.getPulseSpeed(), module.getSharpness(), 0.0F)
                    .get();
            write(this.compositeUniforms, data);
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = renderPass("glow hands composite", mainTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.HANDS_GLOW_COMPOSITE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("GlowCompositeUniforms", this.compositeUniforms);
            pass.setUniform("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            pass.setUniform("NearSampler", this.blurB.getColorTextureView(), sampler);
            pass.setUniform("FarSampler", this.blurC.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void initBuffers() {
        if (this.maskUniforms == null) this.maskUniforms = ubo("GlowHands Mask UBO", MASK_SIZE);
        if (this.blurUniforms == null) this.blurUniforms = ubo("GlowHands Blur UBO", BLUR_SIZE);
        if (this.compositeUniforms == null) this.compositeUniforms = ubo("GlowHands Composite UBO", COMPOSITE_SIZE);
    }

    private void ensureTargets(int width, int height) {
        if (width == this.width && height == this.height
                && this.beforeTarget != null && this.maskTarget != null
                && this.blurA != null && this.blurB != null && this.blurC != null) {
            return;
        }
        releaseTargets();
        this.width = width;
        this.height = height;
        int blurWidth = blurSize(width);
        int blurHeight = blurSize(height);
        this.beforeTarget = new TextureTarget("glow hands before", width, height, PiplinePost.EFFECT_FORMAT, GpuFormat.D32_FLOAT);
        this.maskTarget = new TextureTarget("glow hands mask", width, height, FORMAT, null);
        this.blurA = new TextureTarget("glow hands blur a", blurWidth, blurHeight, FORMAT, null);
        this.blurB = new TextureTarget("glow hands blur b", blurWidth, blurHeight, FORMAT, null);
        this.blurC = new TextureTarget("glow hands blur c", blurWidth, blurHeight, FORMAT, null);
    }

    private static int blurSize(int size) {
        return Math.max(1, size / BLUR_DIVISOR);
    }

    private static RenderPass renderPass(String label, RenderTarget target) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, target.getColorTextureView(), Optional.empty());
    }

    private static void draw(RenderPass pass) {
        pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
        pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
    }

    private static void write(GpuBuffer buffer, ByteBuffer data) {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
    }

    private static GpuBuffer ubo(String label, int size) {
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    private static boolean valid(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0 && target.getColorTexture() != null && target.getDepthTexture() != null;
    }

    public void release() {
        releaseTargets();
        if (this.maskUniforms != null) this.maskUniforms.close();
        if (this.blurUniforms != null) this.blurUniforms.close();
        if (this.compositeUniforms != null) this.compositeUniforms.close();
        this.maskUniforms = null;
        this.blurUniforms = null;
        this.compositeUniforms = null;
    }

    private void releaseTargets() {
        if (this.beforeTarget != null) this.beforeTarget.destroyBuffers();
        if (this.maskTarget != null) this.maskTarget.destroyBuffers();
        if (this.blurA != null) this.blurA.destroyBuffers();
        if (this.blurB != null) this.blurB.destroyBuffers();
        if (this.blurC != null) this.blurC.destroyBuffers();
        this.beforeTarget = null;
        this.maskTarget = null;
        this.blurA = null;
        this.blurB = null;
        this.blurC = null;
    }
}
