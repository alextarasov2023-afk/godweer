package pub.godweer.util.render.world;

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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.module.impl.render.JumpCircles;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.pipeline.FullscreenQuad;
import pub.godweer.util.render.pipeline.PiplinePost;
import pub.godweer.util.render.pipeline.Post;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

/**
 * Create by daun kvass
 */
public class JumpCircleRenderer {
    private static final int MAX_CIRCLES = 16;
    private static final int JUMP_CIRCLES_UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get() + (MAX_CIRCLES * 3 * 16);

    private final GpuBuffer jumpUniforms = uniformBuffer("jump circles ubo", JUMP_CIRCLES_UNIFORM_SIZE);
    private TextureTarget sceneCopy;

    public void render(JumpCircles module, CameraRenderState cameraState) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target) || cameraState == null || !cameraState.initialized) {
            return;
        }

        module.update();
        if (!module.hasActiveCircles()) {
            return;
        }

        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f viewMatrix = new Matrix4f(cameraState.viewRotationMatrix);
        Matrix4f invViewMatrix = new Matrix4f(viewMatrix).invert();

        ensureSceneCopy(target.width, target.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getColorTexture(),
                this.sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0,
                target.width,
                target.height
        );

        writeUniforms(module, cameraState, invProjection, invViewMatrix, target.width, target.height);

        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "jump circles pass",
                target.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.WORLD_JUMP_CIRCLES));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldJumpCircleUniforms", this.jumpUniforms);
            pass.setUniform("SceneSampler", this.sceneCopy.getColorTextureView(), linearSampler);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), nearestSampler);
            drawFullscreen(pass);
        }
    }

    private void writeUniforms(JumpCircles module, CameraRenderState cameraState,
                               Matrix4f invProj, Matrix4f invView, int width, int height) {
        List<JumpCircles.CircleData> list = module.getCircles();
        int count = Math.min(list.size(), MAX_CIRCLES);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, JUMP_CIRCLES_UNIFORM_SIZE)
                    .putMat4f(invProj)
                    .putMat4f(invView)
                    .putVec4(
                            (float) cameraState.pos.x,
                            (float) cameraState.pos.y,
                            (float) cameraState.pos.z,
                            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0F : 0.0F
                    )
                    .putVec4(
                            1.0F / width,
                            1.0F / height,
                            Post.shaderTime(),
                            (float) count
                    );

            for (int i = 0; i < MAX_CIRCLES; i++) {
                if (i < count) {
                    JumpCircles.CircleData c = list.get(i);
                    builder.putVec4(
                            (float) c.getOrigin().x,
                            (float) c.getOrigin().y,
                            (float) c.getOrigin().z,
                            c.getProgress()
                    );
                    builder.putVec4(
                            ColorUtil.red(c.getColor()) / 255.0F,
                            ColorUtil.green(c.getColor()) / 255.0F,
                            ColorUtil.blue(c.getColor()) / 255.0F,
                            c.getMaxRadius()
                    );
                    builder.putVec4(
                            c.getDistortion(),
                            c.getGlow(),
                            c.getRingWidth(),
                            1.5F
                    );
                } else {
                    builder.putVec4(0, 0, 0, 0);
                    builder.putVec4(0, 0, 0, 0);
                    builder.putVec4(0, 0, 0, 0);
                }
            }

            ByteBuffer data = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.jumpUniforms.slice(), data);
        }
    }

    private static void drawFullscreen(RenderPass pass) {
        pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
        pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
    }

    private void ensureSceneCopy(int width, int height) {
        if (this.sceneCopy != null && this.sceneCopy.width == width && this.sceneCopy.height == height) {
            return;
        }
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
        }
        this.sceneCopy = new TextureTarget("jump scene copy", width, height, PiplinePost.EFFECT_FORMAT, null);
    }

    private static boolean valid(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0
                && target.getColorTexture() != null && target.getDepthTexture() != null;
    }

    private static GpuBuffer uniformBuffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
    }

    public void release() {
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
            this.sceneCopy = null;
        }
        this.jumpUniforms.close();
    }
}