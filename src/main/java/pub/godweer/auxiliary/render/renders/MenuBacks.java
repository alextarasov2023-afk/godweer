package pub.godweer.auxiliary.render.renders;

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
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryStack;
import pub.godweer.auxiliary.render.pipline.FullscreenQuad;
import pub.godweer.auxiliary.render.pipline.Pipelines;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Create by daun kvass
 */
public final class MenuBacks {
    private static final int PASS_COUNT = 3;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec2().putVec2().putFloat().get();

    private static TextureTarget halfTarget;
    private static TextureTarget quarterTarget;
    private static GpuBuffer[] passUniforms;

    private static float requestedRadius;
    private static float activeRadius = 16.0F;

    private MenuBacks() {}

    public static GpuTextureView acquireView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || RenderSystem.tryGetDevice() == null) {
            return null;
        }
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTexture() == null || mainTarget.width <= 1 || mainTarget.height <= 1) {
            return null;
        }

        int halfWidth = Math.max(1, mainTarget.width / 2);
        int halfHeight = Math.max(1, mainTarget.height / 2);
        if (halfTarget == null || halfTarget.width != halfWidth || halfTarget.height != halfHeight) {
            if (halfTarget != null) halfTarget.destroyBuffers();
            if (quarterTarget != null) {
                quarterTarget.destroyBuffers();
                quarterTarget = null;
            }
            halfTarget = new TextureTarget("godweer-gui-backdrop-half", halfWidth, halfHeight, GpuFormat.RGBA8_UNORM, null);
            quarterTarget = new TextureTarget(
                    "godweer-gui-backdrop-quarter",
                    Math.max(1, halfWidth / 2),
                    Math.max(1, halfHeight / 2),
                    GpuFormat.RGBA8_UNORM,
                    null
            );
        }
        return halfTarget.getColorTextureView();
    }

    public static void requestBlurRadius(float radiusPx) {
        requestedRadius = Math.max(requestedRadius, radiusPx);
    }

    public static void captureNow(RenderTarget mainTarget) {
        if (halfTarget == null || quarterTarget == null || mainTarget == null || mainTarget.getColorTexture() == null) {
            return;
        }
        if (RenderSystem.tryGetDevice() == null || mainTarget.width <= 1 || mainTarget.height <= 1) {
            return;
        }

        if (requestedRadius > 0.0F) {
            activeRadius = requestedRadius;
            requestedRadius = 0.0F;
        }
        ensureStaticResources();

        float offset = Mth.clamp(activeRadius / 8.0F, 0.5F, 4.0F);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        runPass(0, Pipelines.GUI_BLUR_DOWN, mainTarget.getColorTextureView(), mainTarget.width, mainTarget.height, halfTarget, 1.0F, sampler);

        if (activeRadius >= 4.0F) {
            runPass(1, Pipelines.GUI_BLUR_DOWN, halfTarget.getColorTextureView(), halfTarget.width, halfTarget.height, quarterTarget, offset, sampler);
            runPass(2, Pipelines.GUI_BLUR_UP, quarterTarget.getColorTextureView(), quarterTarget.width, quarterTarget.height, halfTarget, offset, sampler);
        }
    }

    private static void runPass(
            int passIndex,
            RenderPipeline pipeline,
            GpuTextureView input,
            int inputWidth,
            int inputHeight,
            TextureTarget destination,
            float offset,
            GpuSampler sampler
    ) {
        GpuBuffer uniforms = passUniforms[passIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putFloat(0.5F / inputWidth)
                    .putFloat(0.5F / inputHeight)
                    .putFloat(0.0F)
                    .putFloat(0.0F)
                    .putFloat(offset)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(), data);
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Godweer GUI backdrop blur",
                destination.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("GuiKawaseUniforms", uniforms);
            pass.setUniform("CurrentInput", input, sampler);
            pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
            pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
        }
    }

    private static void ensureStaticResources() {
        if (passUniforms == null) {
            passUniforms = new GpuBuffer[PASS_COUNT];
            for (int i = 0; i < PASS_COUNT; i++) {
                int index = i;
                passUniforms[i] = RenderSystem.getDevice().createBuffer(
                        () -> "Godweer Kawase UBO " + index,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_SIZE
                );
            }
        }
    }
}