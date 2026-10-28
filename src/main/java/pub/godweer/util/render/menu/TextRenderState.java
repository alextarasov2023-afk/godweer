package pub.godweer.util.render.menu;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;
import pub.godweer.util.render.pipeline.Pipelines;
import pub.godweer.util.render.MenuCompanionRenderState;
import pub.godweer.util.render.font.MsdfFont;
import pub.godweer.util.render.font.TextAlign;

/**
 * Create by daun kvass
 */
public final class TextRenderState extends MenuCompanionRenderState {
    private final MsdfFont font;
    private final float x, y, size;
    private final MsdfFont.Paragraph paragraph;
    private final int color;
    private final int outlineColor;
    private final float outlineThickness;
    private final float fontWeight;
    private final TextAlign align;
    private final float letterSpacing;

    public TextRenderState(
            Matrix3x2fc pose,
            MsdfFont font,
            float x, float y, float size,
            MsdfFont.Paragraph paragraph,
            int color,
            int outlineColor,
            float outlineThickness,
            float fontWeight,
            TextAlign align,
            float letterSpacing,
            ScreenRectangle scissor
    ) {
        super(
                pose,
                scissor,
                boundsX(x, paragraph.maxWidth(size, letterSpacing), align) - (outlineThickness + 1.0F),
                y - (outlineThickness + 1.0F),
                paragraph.maxWidth(size, letterSpacing) + (outlineThickness + 1.0F) * 2.0F,
                font.lineHeight(size) * Math.max(1, paragraph.lines().size()) + (outlineThickness + 1.0F) * 2.0F
        );
        this.font = font;
        this.x = x;
        this.y = y;
        this.size = size;
        this.paragraph = paragraph;
        this.color = color;
        this.outlineColor = outlineColor;
        this.outlineThickness = outlineThickness;
        this.fontWeight = fontWeight;
        this.align = align;
        this.letterSpacing = letterSpacing;
    }

    public TextRenderState(
            Matrix3x2fc pose,
            MsdfFont font,
            float x, float y, float size,
            MsdfFont.Paragraph paragraph,
            int color,
            TextAlign align,
            float letterSpacing,
            ScreenRectangle scissor
    ) {
        this(pose, font, x, y, size, paragraph, color, 0, 0.0F, 0.0F, align, letterSpacing, scissor);
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float baseline = this.y + this.font.ascender(this.size);
        float lineHeight = this.font.lineHeight(this.size);

        int packedOutlineRg = PacksApex.packU8Pair((this.outlineColor >> 16) & 0xFF, (this.outlineColor >> 8) & 0xFF);
        int packedOutlineBa = PacksApex.packU8Pair(this.outlineColor & 0xFF, (this.outlineColor >>> 24) & 0xFF);
        int packedPxRange = PacksApex.packRadius(this.font.distanceRange());
        float localPxPerSdfUnit = this.font.localPxPerSdfUnit(this.size);

        float paddingPx = this.outlineThickness > 0.0F ? this.outlineThickness : 0.0F;
        float paddingEm = paddingPx / this.size;
        float paddingU = paddingPx * (this.font.atlasSize() / this.size) / this.font.atlasWidth();
        float paddingV = paddingPx * (this.font.atlasSize() / this.size) / this.font.atlasHeight();

        var lines = this.paragraph.lines();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            MsdfFont.Line line = lines.get(lineIndex);
            float originX = alignedX(line.width(this.size, this.letterSpacing));
            float penY = baseline + lineHeight * lineIndex;

            for (int glyphIndex = 0; glyphIndex < line.glyphCount(); glyphIndex++) {
                MsdfFont.Glyph glyph = line.glyphAt(glyphIndex);
                MsdfFont.Bounds plane = glyph.planeBounds();
                if (plane == null) continue;

                float penX = originX + line.penX(glyphIndex, this.size, this.letterSpacing);
                float x0 = penX + (plane.left() - paddingEm) * this.size;
                float x1 = penX + (plane.right() + paddingEm) * this.size;
                float y0 = penY - (plane.top() + paddingEm) * this.size;
                float y1 = penY - (plane.bottom() - paddingEm) * this.size;

                float u0 = glyph.u0() - paddingU;
                float u1 = glyph.u1() + paddingU;
                float v0 = glyph.v0() - paddingV;
                float v1 = glyph.v1() + paddingV;

                addVertex(vertexConsumer, x0, y0, u0, v0, packedOutlineRg, packedOutlineBa, packedPxRange, localPxPerSdfUnit);
                addVertex(vertexConsumer, x0, y1, u0, v1, packedOutlineRg, packedOutlineBa, packedPxRange, localPxPerSdfUnit);
                addVertex(vertexConsumer, x1, y1, u1, v1, packedOutlineRg, packedOutlineBa, packedPxRange, localPxPerSdfUnit);
                addVertex(vertexConsumer, x1, y0, u1, v0, packedOutlineRg, packedOutlineBa, packedPxRange, localPxPerSdfUnit);
            }
        }
    }

    private void addVertex(
            VertexConsumer vertexConsumer,
            float x, float y,
            float u, float v,
            int packedOutlineRg, int packedOutlineBa,
            int packedPxRange, float localPxPerSdfUnit
    ) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), localPxPerSdfUnit)
                .setColor(this.color)
                .setUv(u, v)
                .setUv1(packedOutlineRg, packedOutlineBa)
                .setUv2(packedPxRange, 0)
                .setNormal(this.fontWeight, 0.0F, 0.0F)
                .setLineWidth(this.outlineThickness);
    }

    @Override
    public RenderPipeline pipeline() {
        return Pipelines.TEXT;
    }

    @Override
    public TextureSetup textureSetup() {
        return this.font.textureSetup();
    }

    private float alignedX(float width) {
        return switch (this.align) {
            case LEFT -> this.x;
            case CENTER -> this.x - width * 0.5F;
            case RIGHT -> this.x - width;
        };
    }

    private static float boundsX(float x, float width, TextAlign align) {
        return switch (align) {
            case LEFT -> x;
            case CENTER -> x - width * 0.5F;
            case RIGHT -> x - width;
        };
    }
}