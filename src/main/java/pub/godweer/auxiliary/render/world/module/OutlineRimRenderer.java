package pub.godweer.auxiliary.render.world.module;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.pipline.FullscreenQuad;
import pub.godweer.auxiliary.render.pipline.PiplinePost;
import pub.godweer.auxiliary.render.pipline.Post;
import pub.godweer.kitty.module.impl.render.OutlineHands;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Plain colored outline around first-person hands.
 *
 * Same mask tech as GlowHands (depth snapshot before hands -> depth-difference
 * mask after), but no blur halo: a single additive composite dilates the mask
 * and subtracts the mask itself -> a flat, crisp colored ring.
 */
public final class OutlineRimRenderer {
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static final GpuFormat MASK_FORMAT = GpuFormat.RGBA16_FLOAT;

    private static final int MASK_SIZE = new com.mojang.blaze3d.buffers.Std140SizeCalculator().putVec4().get();
    private static final int RIM_SIZE = new com.mojang.blaze3d.buffers.Std140SizeCalculator()
            .putVec4().putVec4().get();

    private GpuBuffer maskUniforms;
    private GpuBuffer rimUniforms;

    private TextureTarget beforeTarget;
    private TextureTarget maskTarget;
    private int width;
    private int height;
    private boolean prepared;

    /** Вызывается ДО создания vanilla-пасса рук: encoder свободен. */
    public void prepareSnapshot() {
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

    /** Вызывается ПОСЛЕ закрытия vanilla-пасса рук. */
    public void compose(OutlineHands module) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (!this.prepared || !valid(mainTarget)) {
            this.prepared = false;
            return;
        }
        this.prepared = false;

        buildMask(module, mainTarget);
        composite(module, mainTarget);
    }

    private void buildMask(OutlineHands module, RenderTarget mainTarget) {
        boolean left = true;
        boolean right = true;
        Minecraft mc = Minecraft.getInstance();
        if (!module.bothHands() && mc.player != null) {
            left = mc.player.getMainArm() == HumanoidArm.LEFT;
            right = mc.player.getMainArm() == HumanoidArm.RIGHT;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, MASK_SIZE)
                    .putVec4(1.0F / this.width, 1.0F / this.height, left ? 1.0F : 0.0F, right ? 1.0F : 0.0F)
                    .get();
            write(this.maskUniforms, data);
        }

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = renderPass("outline hands mask", this.maskTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.HANDS_GLOW_MASK));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("GlowHandsMaskUniforms", this.maskUniforms);
            pass.setUniform("BeforeDepth", this.beforeTarget.getDepthTextureView(), depthSampler);
            pass.setUniform("AfterDepth", mainTarget.getDepthTextureView(), depthSampler);
            draw(pass);
        }
    }

    private void composite(OutlineHands module, RenderTarget mainTarget) {
        int color = module.getOutlineColor();
        float[] rgba = ColorUtil.getRGBAFloat(color);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, RIM_SIZE)
                    .putVec4(rgba[0], rgba[1], rgba[2], module.getOutlineIntensity())
                    .putVec4(1.0F / this.width, 1.0F / this.height, module.getOutlineWidth(), 0.0F)
                    .get();
            write(this.rimUniforms, data);
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = renderPass("outline hands composite", mainTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.HANDS_RIM));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandsRimUniforms", this.rimUniforms);
            pass.setUniform("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void initBuffers() {
        if (this.maskUniforms == null) this.maskUniforms = Post.createUniforms("OutlineHands Mask UBO", MASK_SIZE);
        if (this.rimUniforms == null) this.rimUniforms = Post.createUniforms("OutlineHands Rim UBO", RIM_SIZE);
    }

    private void ensureTargets(int width, int height) {
        if (width == this.width && height == this.height
                && this.beforeTarget != null && this.maskTarget != null) {
            return;
        }
        releaseTargets();
        this.width = width;
        this.height = height;
        this.beforeTarget = new TextureTarget("outline hands before", width, height, PiplinePost.EFFECT_FORMAT, GpuFormat.D32_FLOAT);
        this.maskTarget = new TextureTarget("outline hands mask", width, height, MASK_FORMAT, null);
    }

    private static RenderPass renderPass(String label, RenderTarget target) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label,
                target.getColorTextureView(),
                Optional.empty()
        );
    }

    private static void draw(RenderPass pass) {
        pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
        pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
    }

    private static void write(GpuBuffer buffer, ByteBuffer data) {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
    }

    private static boolean valid(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0
                && target.getColorTexture() != null && target.getDepthTexture() != null;
    }

    public void release() {
        releaseTargets();
        if (this.maskUniforms != null) this.maskUniforms.close();
        if (this.rimUniforms != null) this.rimUniforms.close();
        this.maskUniforms = null;
        this.rimUniforms = null;
    }

    private void releaseTargets() {
        if (this.beforeTarget != null) this.beforeTarget.destroyBuffers();
        if (this.maskTarget != null) this.maskTarget.destroyBuffers();
        this.beforeTarget = null;
        this.maskTarget = null;
        this.width = 0;
        this.height = 0;
    }
}
