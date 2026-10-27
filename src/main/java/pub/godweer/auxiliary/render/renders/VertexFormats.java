package pub.godweer.auxiliary.render.renders;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;

/**
 * Create by daun kvass
 */
public final class VertexFormats {
    public static final VertexFormat UI = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV1_SEMANTIC_NAME, GpuFormat.RG16_SINT)
            .addAttribute(DefaultVertexFormat.UV2_SEMANTIC_NAME, GpuFormat.RG16_SINT)
            .addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
            .addAttribute(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, GpuFormat.R32_FLOAT)
            .build();

    private VertexFormats() {
    }
}