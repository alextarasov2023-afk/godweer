package pub.godweer.util.render.menu;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fc;
import pub.godweer.util.render.pipeline.Pipelines;
import pub.godweer.util.render.MenuCompanionRenderState;

public final class TextureRenderState extends MenuCompanionRenderState {
    private final Identifier textureId;
    private final float x, y, width, height;
    private final float u0, v0, u1, v1;
    private final int color;
    private final float cornerRadius;
    private final FilterMode filterMode;

    public TextureRenderState(
            Matrix3x2fc pose, Identifier textureId,
            float x, float y, float width, float height,
            float u0, float v0, float u1, float v1,
            int color, float cornerRadius, FilterMode filterMode, ScreenRectangle scissor
    ) {
        super(pose, scissor, x, y, width, height);
        this.textureId = textureId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.color = color;
        this.cornerRadius = cornerRadius;
        this.filterMode = filterMode != null ? filterMode : FilterMode.LINEAR;
    }

    public TextureRenderState(
            Matrix3x2fc pose, Identifier textureId,
            float x, float y, float width, float height,
            float u0, float v0, float u1, float v1,
            int color, float cornerRadius, ScreenRectangle scissor
    ) {
        this(pose, textureId, x, y, width, height, u0, v0, u1, v1, color, cornerRadius, FilterMode.LINEAR, scissor);
    }

    public TextureRenderState(Matrix3x2fc pose, Identifier textureId, float x, float y, float width, float height, int color, ScreenRectangle scissor) {
        this(pose, textureId, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, color, 0.0F, FilterMode.LINEAR, scissor);
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float halfWidth = this.width * 0.5F;
        float halfHeight = this.height * 0.5F;
        int packedSizeX = PacksApex.packSize(this.width);
        int packedSizeY = PacksApex.packSize(this.height);

        addVertex(vertexConsumer, this.x, this.y, this.u0, this.v0, -halfWidth, -halfHeight, packedSizeX, packedSizeY);
        addVertex(vertexConsumer, this.x, this.y + this.height, this.u0, this.v1, -halfWidth, halfHeight, packedSizeX, packedSizeY);
        addVertex(vertexConsumer, this.x + this.width, this.y + this.height, this.u1, this.v1, halfWidth, halfHeight, packedSizeX, packedSizeY);
        addVertex(vertexConsumer, this.x + this.width, this.y, this.u1, this.v0, halfWidth, -halfHeight, packedSizeX, packedSizeY);
    }

    private void addVertex(VertexConsumer vertexConsumer, float x, float y, float u, float v, float localX, float localY, int packedSizeX, int packedSizeY) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), 0.0F)
                .setColor(this.color)
                .setUv(u, v)
                .setUv1(PacksApex.packSignedSize(localX), PacksApex.packSignedSize(localY))
                .setUv2(packedSizeX, packedSizeY)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setLineWidth(Math.max(this.cornerRadius, 0.0F));
    }

    @Override
    public RenderPipeline pipeline() {
        return Pipelines.TEXTURE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(
                Minecraft.getInstance().getTextureManager().getTexture(this.textureId).getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(this.filterMode, false)
        );
    }
}