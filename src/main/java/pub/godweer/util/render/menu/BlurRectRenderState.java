package pub.godweer.util.render.menu;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fc;
import pub.godweer.util.render.pipeline.Pipelines;
import pub.godweer.util.render.MenuBacks;
import pub.godweer.util.render.MenuCompanionRenderState;

/**
 * Create by daun kvass
 */
public final class BlurRectRenderState extends MenuCompanionRenderState {
    private final float x0, y0, x1, y1;
    private final int tintColor;
    private final float radius;
    private final float opacity;
    private final TextureSetup textureSetup;

    public BlurRectRenderState(
            Matrix3x2fc pose,
            float x0, float y0, float x1, float y1,
            int tintColor,
            float radius,
            float blurRadiusPx,
            float opacity,
            GpuTextureView backdropView,
            ScreenRectangle scissor
    ) {
        super(pose, scissor, x0, y0, x1 - x0, y1 - y0);
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.tintColor = tintColor;
        this.radius = radius;
        this.opacity = opacity;
        this.textureSetup = TextureSetup.singleTexture(
                backdropView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
        MenuBacks.requestBlurRadius(blurRadiusPx);
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float halfWidth = (this.x1 - this.x0) * 0.5F;
        float halfHeight = (this.y1 - this.y0) * 0.5F;
        int packedSizeX = PacksApex.packSize(halfWidth * 2.0F);
        int packedSizeY = PacksApex.packSize(halfHeight * 2.0F);
        int packedRadius = PacksApex.packRadius(this.radius);
        int packedOpacity = Mth.clamp(Math.round(this.opacity * 255.0F), 0, 255);

        addVertex(vertexConsumer, this.x0, this.y0, -halfWidth, -halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
        addVertex(vertexConsumer, this.x0, this.y1, -halfWidth, halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
        addVertex(vertexConsumer, this.x1, this.y1, halfWidth, halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
        addVertex(vertexConsumer, this.x1, this.y0, halfWidth, -halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
    }

    private void addVertex(
            VertexConsumer vertexConsumer,
            float x, float y,
            float localX, float localY,
            int packedSizeX, int packedSizeY,
            int packedRadius, int packedOpacity
    ) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), 0.0F)
                .setColor(this.tintColor)
                .setUv(localX, localY)
                .setUv1(packedSizeX, packedSizeY)
                .setUv2(packedRadius, packedOpacity)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setLineWidth(0.0F);
    }

    @Override
    public RenderPipeline pipeline() {
        return Pipelines.BLUR_RECT;
    }

    @Override
    public TextureSetup textureSetup() {
        return this.textureSetup;
    }
}