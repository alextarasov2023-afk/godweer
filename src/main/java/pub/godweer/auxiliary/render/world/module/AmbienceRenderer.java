package pub.godweer.auxiliary.render.world.module;

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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.kitty.module.impl.render.Ambience;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.pipline.FullscreenQuad;
import pub.godweer.auxiliary.render.pipline.PiplinePost;
import pub.godweer.auxiliary.render.pipline.Post;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Create by daun kvass
 */
public class AmbienceRenderer {
    private static final int SKY_UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final int SATURATION_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private static final int PUDDLES_UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final int VOLUMETRIC_FOG_UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final int LIGHTNING_UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private final GpuBuffer skyUniforms = uniformBuffer("skys ubo ambience", SKY_UNIFORM_SIZE);
    private final GpuBuffer saturationUniforms = uniformBuffer("satur ubo ambience", SATURATION_UNIFORM_SIZE);
    private final GpuBuffer puddlesUniforms = uniformBuffer("puddles ubo ambience", PUDDLES_UNIFORM_SIZE);
    private final GpuBuffer volumetricFogUniforms = uniformBuffer("volumetric fog ubo ambience", VOLUMETRIC_FOG_UNIFORM_SIZE);
    private final GpuBuffer lightningUniforms = uniformBuffer("lightning ubo ambience", LIGHTNING_UNIFORM_SIZE);

    private TextureTarget sceneCopy;
    private TextureTarget skyClouds;

    public static class LightningStrikeInfo {
        public float intensity = 0.0f;
        public float age = 0.0f;
        public float strikeX = 0.0f;
        public float strikeZ = 0.0f;
        public float topY = 0.0f;
        public float botY = 0.0f;
        public float seed = 0.0f;
    }

    public static LightningStrikeInfo getStrikeInfo(Ambience module, CameraRenderState cameraState) {
        LightningStrikeInfo info = new LightningStrikeInfo();
        if (!module.usesLightning() || cameraState == null) return info;

        float time = Post.shaderTime();
        float freq = Math.max(2.5f, module.lightningFrequency.getValue());

        float cycle = time / freq;
        float cycleId = (float) Math.floor(cycle);
        float tInCycle = (cycle - cycleId) * freq;

        float seed = (float) Math.sin(cycleId * 127.1f + 311.7f) * 43758.5453f;
        seed = seed - (float) Math.floor(seed);

        float randOffset = seed * (freq * 0.55f);
        float flashT = tInCycle - randOffset;

        float totalLife = 2.0f;
        if (flashT < 0.0f || flashT > totalLife) {
            return info;
        }

        info.age = flashT / totalLife;

        float intensity = 0.0f;
        if (flashT < 0.05f) {
            intensity = flashT / 0.05f;
        } else if (flashT < 0.10f) {
            intensity = 1.0f - (flashT - 0.05f) / 0.05f * 0.50f;
        } else if (flashT < 0.16f) {
            intensity = 0.50f + (flashT - 0.10f) / 0.06f * 0.50f;
        } else if (flashT < 0.22f) {
            intensity = 1.0f - (flashT - 0.16f) / 0.06f * 0.40f;
        } else if (flashT < 0.28f) {
            intensity = 0.60f + (flashT - 0.22f) / 0.06f * 0.35f;
        } else {
            float decayT = (flashT - 0.28f) / (totalLife - 0.28f);
            intensity = 0.95f * (float) Math.exp(-decayT * 2.2f);
        }

        info.intensity = Math.max(0.0f, intensity * module.lightningIntensity.getValue());
        info.seed = cycleId * 23.17f + 5.12f;

        float angle = seed * 6.2831853f;
        float dist = 70.0f + (seed * 8.0f - (float) Math.floor(seed * 8.0f)) * 60.0f;

        info.strikeX = (float) cameraState.pos.x + (float) Math.cos(angle) * dist;
        info.strikeZ = (float) cameraState.pos.z + (float) Math.sin(angle) * dist;
        info.topY = (float) cameraState.pos.y + 115.0f;
        info.botY = (float) cameraState.pos.y - 12.0f;

        return info;
    }

    public void renderLightning(Ambience module, CameraRenderState cameraState) {
        if (!module.usesLightning() || cameraState == null || !cameraState.initialized) {
            return;
        }

        LightningStrikeInfo strike = getStrikeInfo(module, cameraState);
        if (strike.intensity <= 0.001f) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target)) {
            return;
        }

        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f viewMatrix = new Matrix4f(cameraState.viewRotationMatrix);
        Matrix4f invViewMatrix = new Matrix4f(viewMatrix).invert();
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();

        int color = module.getLightningColorRGB();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, LIGHTNING_UNIFORM_SIZE)
                    .putMat4f(projection)
                    .putMat4f(invProjection)
                    .putMat4f(viewMatrix)
                    .putMat4f(invViewMatrix)
                    .putVec4((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z, 1.0F)
                    .putVec4(ColorUtil.red(color) / 255.0F, ColorUtil.green(color) / 255.0F, ColorUtil.blue(color) / 255.0F, strike.intensity)
                    .putVec4(strike.strikeX, strike.strikeZ, strike.topY, strike.botY)
                    .putVec4(strike.age, strike.intensity, strike.seed, zZeroToOne ? 1.0F : 0.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.lightningUniforms.slice(), data);
        }

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "ambience 3d lightning",
                target.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.WORLD_LIGHTNING));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("LightningUniforms", this.lightningUniforms);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), depthSampler);
            drawFullscreen(pass);
        }
    }

    public void renderVolumetricFog(Ambience module, CameraRenderState cameraState) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target) || cameraState == null || !cameraState.initialized) {
            return;
        }

        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f viewMatrix = new Matrix4f(cameraState.viewRotationMatrix);
        Matrix4f invViewMatrix = new Matrix4f(viewMatrix).invert();

        writeVolumetricFogUniforms(module, cameraState, projection, invProjection, viewMatrix, invViewMatrix, target.width, target.height);

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "ambience volumetric fog",
                target.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.WORLD_VOLUMETRIC_FOG));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("VolumetricFogUniforms", this.volumetricFogUniforms);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), depthSampler);
            drawFullscreen(pass);
        }
    }

    private void writeVolumetricFogUniforms(Ambience module, CameraRenderState cameraState, Matrix4f proj, Matrix4f invProj, Matrix4f view, Matrix4f invView, int width, int height) {
        int color = module.getVolumetricFogColor();
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();

        LightningStrikeInfo strike = getStrikeInfo(module, cameraState);
        int lColor = module.getLightningColorRGB();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, VOLUMETRIC_FOG_UNIFORM_SIZE)
                    .putMat4f(proj)
                    .putMat4f(invProj)
                    .putMat4f(view)
                    .putMat4f(invView)
                    .putVec4((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z, 1.0F)
                    .putVec4(ColorUtil.red(color) / 255.0F, ColorUtil.green(color) / 255.0F, ColorUtil.blue(color) / 255.0F, 1.0F)
                    .putVec4(Post.shaderTime(), module.volFogDensity.getValue().floatValue(), module.volFogThickness.getValue().floatValue(), module.volFogHeight.getValue().floatValue())
                    .putVec4(1.0F / width, 1.0F / height, zZeroToOne ? 1.0F : 0.0F, 0.0F)
                    .putVec4(strike.intensity, ColorUtil.red(lColor) / 255.0F, ColorUtil.green(lColor) / 255.0F, ColorUtil.blue(lColor) / 255.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.volumetricFogUniforms.slice(), data);
        }
    }

    public void renderSky(Ambience module, CameraRenderState cameraState) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target) || cameraState == null || !cameraState.initialized) {
            return;
        }
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }
        Matrix4f inverseViewProjection = new Matrix4f(projection).mul(cameraState.viewRotationMatrix).invert();
        writeSkyUniforms(module, inverseViewProjection, target.width, target.height);
        ensureSkyClouds(target.width, target.height);

        RenderPipeline cloudsPipeline;
        RenderPipeline compositePipeline;
        switch (module.custsky.getValue()) {
            case Ambience.SKY_NEBULA -> {
                cloudsPipeline = PiplinePost.WORLD_SKY_CLOUDS_NEBULA;
                compositePipeline = PiplinePost.WORLD_SKY_NEBULA;
            }
            case Ambience.SKY_PLASMA -> {
                cloudsPipeline = PiplinePost.WORLD_SKY_CLOUDS_PLASMA;
                compositePipeline = PiplinePost.WORLD_SKY_PLASMA;
            }
            case Ambience.SKY_CAUSTIC -> {
                cloudsPipeline = PiplinePost.WORLD_SKY_CLOUDS_CAUSTIC;
                compositePipeline = PiplinePost.WORLD_SKY_CAUSTIC;
            }
            default -> {
                cloudsPipeline = PiplinePost.WORLD_SKY_STARRY_SKY_SPACE;
                compositePipeline = PiplinePost.WORLD_SKY_STARRY_SKY;
            }
        }

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler cloudSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ambience sky cl", this.skyClouds.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(cloudsPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldSkyUniforms", this.skyUniforms);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), depthSampler);
            drawFullscreen(pass);
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ambience sky", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldSkyUniforms", this.skyUniforms);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), depthSampler);
            pass.setUniform("CloudSampler", this.skyClouds.getColorTextureView(), cloudSampler);
            drawFullscreen(pass);
        }
    }

    private void writeSkyUniforms(Ambience module, Matrix4f inverseViewProjection, int width, int height) {
        int primary = module.thusm.get() ? Theme.getAccentColor() : module.skyColor1.getValue();
        int secondary = module.thusm.get() ? Theme.getAccentColor() : module.skyColor2.getValue();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, SKY_UNIFORM_SIZE)
                    .putMat4f(inverseViewProjection)
                    .putVec4(ColorUtil.red(primary) / 255.0F, ColorUtil.green(primary) / 255.0F, ColorUtil.blue(primary) / 255.0F, 1.0F)
                    .putVec4(ColorUtil.red(secondary) / 255.0F, ColorUtil.green(secondary) / 255.0F, ColorUtil.blue(secondary) / 255.0F, 1.0F)
                    .putVec4(Post.shaderTime(), module.skyIntensity.getValue().floatValue(), module.skySpeed.getValue().floatValue(), RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0F : 0.0F)
                    .putVec4(1.0F / width, 1.0F / height, 0.0F, 0.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.skyUniforms.slice(), data);
        }
    }

    public void renderSaturation(Ambience module) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target)) {
            return;
        }
        ensureSceneCopy(target.width, target.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(target.getColorTexture(), this.sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, target.width, target.height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, SATURATION_UNIFORM_SIZE)
                    .putVec4(Math.clamp(1.0F + module.sts.getValue().floatValue(), 0.0F, 3.0F), 0.0F, 0.0F, 0.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.saturationUniforms.slice(), data);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ambienc sat", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.WORLD_SATURATION));
            RenderSystem.bindDefaultUniforms(pass);
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            pass.setUniform("SaturationUniforms", this.saturationUniforms);
            pass.setUniform("SceneSampler", this.sceneCopy.getColorTextureView(), sampler);
            drawFullscreen(pass);
        }
    }

    public void renderPuddles(Ambience module, CameraRenderState cameraState) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (!valid(target) || cameraState == null || !cameraState.initialized) {
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
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(target.getColorTexture(), this.sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, target.width, target.height);

        writePuddlesUniforms(module, cameraState, projection, invProjection, viewMatrix, invViewMatrix, target.width, target.height);

        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ambience puddles", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PiplinePost.WORLD_PUDDLES));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldPuddlesUniforms", this.puddlesUniforms);
            pass.setUniform("SceneSampler", this.sceneCopy.getColorTextureView(), linearSampler);
            pass.setUniform("DepthSampler", target.getDepthTextureView(), nearestSampler);
            drawFullscreen(pass);
        }
    }

    private void writePuddlesUniforms(Ambience module, CameraRenderState cameraState, Matrix4f proj, Matrix4f invProj, Matrix4f view, Matrix4f invView, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, PUDDLES_UNIFORM_SIZE)
                    .putMat4f(proj)
                    .putMat4f(invProj)
                    .putMat4f(view)
                    .putMat4f(invView)
                    .putVec4((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z, 1.0F)
                    .putVec4(Post.shaderTime(), module.puddleCoverage.getValue().floatValue(), module.puddleWaveSpeed.getValue().floatValue(), module.puddleReflect.getValue().floatValue())
                    .putVec4(module.puddleScale.getValue().floatValue(), module.puddleWaveStrength.getValue().floatValue(), 1.0F / width, 1.0F / height)
                    .putVec4(rainRipple(module), 0.0F, 0.0F, 0.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.puddlesUniforms.slice(), data);
        }
    }

    private static float rainRipple(Ambience module) {
        return module.usesRainRipples() ? 0.85F : 0.0F;
    }

    private static void drawFullscreen(RenderPass pass) {
        pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
        pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
    }

    private void ensureSceneCopy(int width, int height) {
        if (this.sceneCopy != null && this.sceneCopy.width == width && this.sceneCopy.height == height) return;
        if (this.sceneCopy != null) this.sceneCopy.destroyBuffers();
        this.sceneCopy = new TextureTarget("scene", width, height, PiplinePost.EFFECT_FORMAT, null);
    }

    private void ensureSkyClouds(int width, int height) {
        int halfWidth = Math.max(1, width / 2);
        int halfHeight = Math.max(1, height / 2);
        if (this.skyClouds != null && this.skyClouds.width == halfWidth && this.skyClouds.height == halfHeight) return;
        if (this.skyClouds != null) this.skyClouds.destroyBuffers();
        this.skyClouds = new TextureTarget("sky cloud", halfWidth, halfHeight, PiplinePost.SKY_CLOUDS_FORMAT, null);
    }

    private static boolean valid(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0 && target.getColorTexture() != null && target.getColorTextureView() != null && target.getDepthTexture() != null && target.getDepthTextureView() != null;
    }

    private static GpuBuffer uniformBuffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    public void release() {
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
            this.sceneCopy = null;
        }
        if (this.skyClouds != null) {
            this.skyClouds.destroyBuffers();
            this.skyClouds = null;
        }
        this.skyUniforms.close();
        this.saturationUniforms.close();
        this.puddlesUniforms.close();
        this.volumetricFogUniforms.close();
        this.lightningUniforms.close();
    }
}