package pub.godweer.util.render.menu;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;
import pub.godweer.util.render.pipeline.Pipelines;
import pub.godweer.util.render.MenuCompanionRenderState;

public final class CircleArcRenderState extends MenuCompanionRenderState {
    private final float centerX;
    private final float centerY;
    private final float radius;
    private final float thickness;
    private final float startAngleDeg;
    private final float sweepAngleDeg;
    private final int color;

    public CircleArcRenderState(
            Matrix3x2fc pose,
            float centerX,
            float centerY,
            float radius,
            float thickness,
            float startAngleDeg,
            float sweepAngleDeg,
            int color,
            ScreenRectangle scissor
    ) {
        super(pose, scissor, centerX - radius - 1.5F, centerY - radius - 1.5F, (radius + 1.5F) * 2.0F, (radius + 1.5F) * 2.0F);
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.thickness = thickness;
        this.startAngleDeg = startAngleDeg;
        this.sweepAngleDeg = sweepAngleDeg;
        this.color = color;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float pad = 1.5F;
        float x0 = centerX - radius - pad;
        float y0 = centerY - radius - pad;
        float x1 = centerX + radius + pad;
        float y1 = centerY + radius + pad;

        float maxExtent = radius + pad;

        int packedSizeX = Math.round(radius * 100.0F);
        int packedSizeY = Math.round(thickness * 100.0F);
        int packedAngleStart = Math.round(startAngleDeg * 10.0F);
        int packedAngleSweep = Math.round(sweepAngleDeg * 10.0F);

        addVertex(vertexConsumer, x0, y0, -maxExtent, -maxExtent, packedSizeX, packedSizeY, packedAngleStart, packedAngleSweep, color);
        addVertex(vertexConsumer, x0, y1, -maxExtent, maxExtent, packedSizeX, packedSizeY, packedAngleStart, packedAngleSweep, color);
        addVertex(vertexConsumer, x1, y1, maxExtent, maxExtent, packedSizeX, packedSizeY, packedAngleStart, packedAngleSweep, color);
        addVertex(vertexConsumer, x1, y0, maxExtent, -maxExtent, packedSizeX, packedSizeY, packedAngleStart, packedAngleSweep, color);
    }

    private void addVertex(VertexConsumer vertexConsumer, float x, float y, float lx, float ly, int uv1X, int uv1Y, int uv2X, int uv2Y, int color) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), 0.0F)
                .setColor(color)
                .setUv(lx, ly)
                .setUv1(uv1X, uv1Y)
                .setUv2(uv2X, uv2Y)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setLineWidth(0.0F);
    }

    @Override
    public RenderPipeline pipeline() {
        return Pipelines.CIRCLE_ARC;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}