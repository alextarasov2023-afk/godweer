package pub.godweer.auxiliary.render.world.module;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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
import pub.godweer.kitty.module.impl.render.ShaderHands;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.pipline.FullscreenQuad;
import pub.godweer.auxiliary.render.pipline.PiplinePost;
import pub.godweer.auxiliary.render.pipline.Post;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Minimal hand shader pipeline: snapshot scene, draw hands, build a mask
 * from the depth difference, fill the mask with the selected shader.
 */
public final class ShaderHandsRenderer {
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    private static final int MASK_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final int FILL_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

    private GpuBuffer maskUniforms;
    private GpuBuffer fillUniforms;

    private TextureTarget beforeTarget;
    private TextureTarget maskTarget;
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
                mainTarget.width,
                mainTarget.height
        );
        encoder.clearColorTexture(this.maskTarget.getColorTexture(), CLEAR);
        this.prepared = true;
    }

    public void compose(ShaderHands module) {
        // Вызывается ПОСЛЕ закрытия vanilla-пасса рук: глубина уже содержит руки.
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (!this.prepared || !valid(mainTarget)) {
            this.prepared = false;
            return;
        }
        this.prepared = false;

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        writeMaskUniforms(module, mainTarget.width, mainTarget.height);
        try (RenderPass pass = renderPass("shader hands mask", this.maskTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.HANDS_MASK));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandsMaskUniforms", this.maskUniforms);
            pass.setUniform("BeforeDepth", this.beforeTarget.getDepthTextureView(), depthSampler);
            pass.setUniform("AfterDepth", mainTarget.getDepthTextureView(), depthSampler);
            draw(pass);
        }

        writeFillUniforms(module);
        try (RenderPass pass = renderPass("shader hands fill", mainTarget)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipelineFor(module)));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandFillUniforms", this.fillUniforms);
            pass.setUniform("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private static RenderPipeline pipelineFor(ShaderHands module) {
        return switch (module.getStyle()) {
            case ShaderHands.FROST -> PiplinePost.HAND_FROST;
            case ShaderHands.GALAXY -> PiplinePost.HAND_GALAXY;
            case ShaderHands.EMBER -> PiplinePost.HAND_EMBER;
            case ShaderHands.SLIME -> PiplinePost.HAND_SLIME;
            case ShaderHands.SMOKE -> PiplinePost.HAND_SMOKE;
            default -> PiplinePost.HAND_LAVA;
        };
    }

    private void writeMaskUniforms(ShaderHands module, int width, int height) {
        boolean left = true;
        boolean right = true;
        if (!module.bothHands() && Minecraft.getInstance().player != null) {
            left = Minecraft.getInstance().player.getMainArm() == HumanoidArm.LEFT;
            right = Minecraft.getInstance().player.getMainArm() == HumanoidArm.RIGHT;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, MASK_SIZE)
                    .putVec4(1.0F / width, 1.0F / height, left ? 1.0F : 0.0F, right ? 1.0F : 0.0F)
                    .get();
            write(this.maskUniforms, data);
        }
    }

    private void writeFillUniforms(ShaderHands module) {
        int color = module.getColor();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, FILL_SIZE)
                    .putVec4(ColorUtil.red(color) / 255.0F, ColorUtil.green(color) / 255.0F, ColorUtil.blue(color) / 255.0F, module.getOpacity())
                    .putVec4(Post.shaderTime() * module.getSpeed(), 0.0F, 0.0F, 0.0F)
                    .get();
            write(this.fillUniforms, data);
        }
    }

    private void initBuffers() {
        if (this.maskUniforms == null) this.maskUniforms = ubo("ShaderHands Mask UBO", MASK_SIZE);
        if (this.fillUniforms == null) this.fillUniforms = ubo("ShaderHands Fill UBO", FILL_SIZE);
    }

    private void ensureTargets(int width, int height) {
        this.beforeTarget = ensureTarget(this.beforeTarget, "shader hands before", width, height, PiplinePost.EFFECT_FORMAT, GpuFormat.D32_FLOAT);
        this.maskTarget = ensureTarget(this.maskTarget, "shader hands mask", width, height, PiplinePost.EFFECT_FORMAT, null);
    }

    private TextureTarget ensureTarget(TextureTarget target, String label, int width, int height, com.mojang.renderpearl.api.GpuFormat format, com.mojang.renderpearl.api.GpuFormat depthFormat) {
        if (target != null && target.width == width && target.height == height) return target;
        if (target != null) target.destroyBuffers();
        return new TextureTarget(label, width, height, format, depthFormat);
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
        if (this.beforeTarget != null) this.beforeTarget.destroyBuffers();
        if (this.maskTarget != null) this.maskTarget.destroyBuffers();
        if (this.maskUniforms != null) this.maskUniforms.close();
        if (this.fillUniforms != null) this.fillUniforms.close();
    }
}
