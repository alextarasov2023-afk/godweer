package pub.godweer.auxiliary.render.renders.menuhelper;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;
import pub.godweer.auxiliary.render.pipline.Pipelines;
import pub.godweer.auxiliary.render.renders.MenuCompanionRenderState;

/**
 * Create by daun kvass
 */
public final class RectRenderState extends MenuCompanionRenderState {
    private final float x0, y0, x1, y1;
    private final int topLeftColor, bottomLeftColor, bottomRightColor, topRightColor;
    private final float topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius;
    private final float borderThickness;
    private final int borderColor;
    private final float shadowBlur;
    private final int shadowColor;
    private final boolean shadow;

    public RectRenderState(Matrix3x2fc pose, float x0, float y0, float x1, float y1, int topLeftColor, int bottomLeftColor, int bottomRightColor, int topRightColor, float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius, float borderThickness, int borderColor, float shadowBlur, int shadowColor, boolean shadow, ScreenRectangle scissor) {
        super(pose, scissor, shadow ? x0 - Math.max(shadowBlur, 1.0F) : x0, shadow ? y0 - Math.max(shadowBlur, 1.0F) : y0,
                (x1 - x0) + (shadow ? Math.max(shadowBlur, 1.0F) * 2.0F : 0.0F),
                (y1 - y0) + (shadow ? Math.max(shadowBlur, 1.0F) * 2.0F : 0.0F));
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.topLeftColor = topLeftColor;
        this.bottomLeftColor = bottomLeftColor;
        this.bottomRightColor = bottomRightColor;
        this.topRightColor = topRightColor;
        this.topLeftRadius = Math.min(Math.max(0.0F, topLeftRadius), 255.0F);
        this.topRightRadius = Math.min(Math.max(0.0F, topRightRadius), 255.0F);
        this.bottomRightRadius = Math.min(Math.max(0.0F, bottomRightRadius), 255.0F);
        this.bottomLeftRadius = Math.min(Math.max(0.0F, bottomLeftRadius), 255.0F);
        this.borderThickness = borderThickness;
        this.borderColor = borderColor;
        this.shadowBlur = shadowBlur;
        this.shadowColor = shadowColor;
        this.shadow = shadow;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float halfWidth = (this.x1 - this.x0) * 0.5F;
        float halfHeight = (this.y1 - this.y0) * 0.5F;
        float spread = this.shadow ? Math.max(this.shadowBlur, 1.0F) : 0.0F;

        int effectColor = this.shadow ? this.shadowColor : this.borderColor;
        float packedZ = PacksApex.packZ(this.shadow ? this.shadowBlur : this.borderThickness, (effectColor >>> 24) & 0xFF, this.shadow);
        int packedSizeX = PacksApex.packSize(halfWidth * 2.0F);
        int packedSizeY = PacksApex.packSize(halfHeight * 2.0F);
        int packedTopLeft = PacksApex.packRadius(this.topLeftRadius);
        int packedTopRight = PacksApex.packRadius(this.topRightRadius);
        float packedBottom = PacksApex.packDual12(this.bottomRightRadius, this.bottomLeftRadius);
        float effectRed = PacksApex.snormChannel((effectColor >> 16) & 0xFF);
        float effectGreen = PacksApex.snormChannel((effectColor >> 8) & 0xFF);
        float effectBlue = PacksApex.snormChannel(effectColor & 0xFF);

        float localX = halfWidth + spread;
        float localY = halfHeight + spread;
        addVertex(vertexConsumer, this.x0 - spread, this.y0 - spread, -localX, -localY, packedZ, packedSizeX, packedSizeY, packedTopLeft, packedTopRight, packedBottom, effectRed, effectGreen, effectBlue, this.topLeftColor);
        addVertex(vertexConsumer, this.x0 - spread, this.y1 + spread, -localX, localY, packedZ, packedSizeX, packedSizeY, packedTopLeft, packedTopRight, packedBottom, effectRed, effectGreen, effectBlue, this.bottomLeftColor);
        addVertex(vertexConsumer, this.x1 + spread, this.y1 + spread, localX, localY, packedZ, packedSizeX, packedSizeY, packedTopLeft, packedTopRight, packedBottom, effectRed, effectGreen, effectBlue, this.bottomRightColor);
        addVertex(vertexConsumer, this.x1 + spread, this.y0 - spread, localX, -localY, packedZ, packedSizeX, packedSizeY, packedTopLeft, packedTopRight, packedBottom, effectRed, effectGreen, effectBlue, this.topRightColor);
    }

    private void addVertex(VertexConsumer vertexConsumer, float x, float y, float localX, float localY, float packedZ, int packedSizeX, int packedSizeY, int packedTopLeft, int packedTopRight, float packedBottom, float effectRed, float effectGreen, float effectBlue, int color) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), packedZ)
                .setColor(color)
                .setUv(localX, localY)
                .setUv1(packedSizeX, packedSizeY)
                .setUv2(packedTopLeft, packedTopRight)
                .setNormal(effectRed, effectGreen, effectBlue)
                .setLineWidth(packedBottom);
    }

    @Override
    public RenderPipeline pipeline() {
        return Pipelines.RECT;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}