package pub.godweer.auxiliary.render.pipline;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Create by daun kvass
 */
public class Post {
    private Post() {
    }

    public static void pass(String label, RenderPipeline pipeline, GpuTextureView target, Consumer<RenderPass> setup) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label,
                target,
                Optional.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            setup.accept(pass);
            pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
            pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
        }
    }

    public static void pass(String label, RenderPipeline pipeline, RenderTarget target, Consumer<RenderPass> setup) {
        pass(label, pipeline, target.getColorTextureView(), setup);
    }

    public static GpuBuffer createUniforms(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
    }

    public static void writeUniforms(GpuBuffer buffer, int size, Consumer<Std140Builder> writer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, size);
            writer.accept(builder);
            ByteBuffer data = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    public static GpuSampler linearSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuSampler nearestSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    public static float shaderTime() {
        return (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
    }
}