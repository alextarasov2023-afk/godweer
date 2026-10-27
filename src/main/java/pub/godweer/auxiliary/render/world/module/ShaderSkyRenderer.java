package pub.godweer.auxiliary.render.world.module;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.pipline.FullscreenQuad;
import pub.godweer.auxiliary.render.pipline.PiplinePost;
import pub.godweer.auxiliary.render.pipline.Post;
import pub.godweer.kitty.module.impl.render.ShaderSky;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Standalone renderer for the ShaderSky module: single fullscreen pass
 * with the Aurora Veil shader, masked by the depth buffer (sky only).
 */
public class ShaderSkyRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private final GpuBuffer uniforms = uniformBuffer("shader sky ubo", UNIFORM_SIZE);

    public void render(ShaderSky module, CameraRenderState cameraState) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target) || cameraState == null || !cameraState.initialized) {
            return;
        }
        Matrix4f projection = cameraState.projectionMatrix != null
                ? new Matrix4f(cameraState.projectionMatrix)
                : Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }
        Matrix4f inverseViewProjection = new Matrix4f(projection).mul(cameraState.viewRotationMatrix).invert();
        if (inverseViewProjection == null) {
            return;
        }

        int primary = module.getColor1();
        int secondary = module.getColor2();
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putMat4f(inverseViewProjection)
                    .putVec4(ColorUtil.red(primary) / 255.0F, ColorUtil.green(primary) / 255.0F, ColorUtil.blue(primary) / 255.0F, 1.0F)
                    .putVec4(ColorUtil.red(secondary) / 255.0F, ColorUtil.green(secondary) / 255.0F, ColorUtil.blue(secondary) / 255.0F, 1.0F)
                    .putVec4(Post.shaderTime(), module.getIntensity(), module.getSpeed(), zZeroToOne ? 1.0F : 0.0F)
                    .putVec4(1.0F / target.width, 1.0F / target.height, module.getAurora(), module.getStars())
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
        }

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "shader sky",
                target.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipelineFor(module)));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldSkyUniforms", this.uniforms);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), depthSampler);
            pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
            pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
        }
    }

    private static com.mojang.renderpearl.api.pipeline.RenderPipeline pipelineFor(ShaderSky module) {
        return switch (module.getStyle()) {
            case ShaderSky.STYLE_CRIMSON -> PiplinePost.WORLD_SKY_CRIMSON;
            case ShaderSky.STYLE_OCEAN -> PiplinePost.WORLD_SKY_OCEAN;
            case ShaderSky.STYLE_SUNSET -> PiplinePost.WORLD_SKY_SUNSET;
            case ShaderSky.STYLE_STORM -> PiplinePost.WORLD_SKY_STORM;
            case ShaderSky.STYLE_EMERALD -> PiplinePost.WORLD_SKY_EMERALD;
            case ShaderSky.STYLE_SOLAR -> PiplinePost.WORLD_SKY_SOLAR;
            case ShaderSky.STYLE_VOID -> PiplinePost.WORLD_SKY_VOID;
            default -> PiplinePost.WORLD_SKY_AURORA;
        };
    }

    private static boolean valid(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0 && target.getColorTexture() != null && target.getColorTextureView() != null && target.getDepthTexture() != null && target.getDepthTextureView() != null;
    }

    private static GpuBuffer uniformBuffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    public void release() {
        this.uniforms.close();
    }
}
