package pub.godweer.util.render.pipeline;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Create by daun kvass
 */
public class PiplinePost {
    private static final BindGroupLayout PUDDLES_LAYOUT = BindGroupLayout.builder().withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("WorldPuddlesUniforms", UniformType.UNIFORM_BUFFER).build();
    public static final RenderPipeline WORLD_PUDDLES = worldPostPipeline("puddles", "godweer:post/world_puddles", PUDDLES_LAYOUT);
    private static final BindGroupLayout HAND_FILL_LAYOUT = BindGroupLayout.builder().withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("HandFillUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout CHAMS_ENTITY_LAYOUT = BindGroupLayout.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout HANDS_MASK_LAYOUT = BindGroupLayout.builder().withUniform("BeforeDepth", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("AfterDepth", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("HandsMaskUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout GLOW_HANDS_MASK_LAYOUT = BindGroupLayout.builder().withUniform("BeforeDepth", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("AfterDepth", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("GlowHandsMaskUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout GLOW_HANDS_BLUR_LAYOUT = BindGroupLayout.builder().withUniform("HaloSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("GlowBlurUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout GLOW_HANDS_COMPOSITE_LAYOUT = BindGroupLayout.builder().withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("NearSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("FarSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("GlowCompositeUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout BLOCK_HIGHLIGHT_LAYOUT = BindGroupLayout.builder().withUniform("BlockHighLightTransform", UniformType.UNIFORM_BUFFER).withUniform("BlockHighLightStyle", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout WORLD_SKY_CLOUDS_LAYOUT = BindGroupLayout.builder().withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("WorldSkyUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout WORLD_SKY_LAYOUT = BindGroupLayout.builder().withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("CloudSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("WorldSkyUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout SATURATION_LAYOUT = BindGroupLayout.builder().withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("SaturationUniforms", UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout JUMP_CIRCLES_LAYOUT = BindGroupLayout.builder()
            .withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("WorldJumpCircleUniforms", UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline WORLD_JUMP_CIRCLES = worldPostPipeline("jump_circles", "godweer:post/jump_circle", JUMP_CIRCLES_LAYOUT);

    public static final RenderPipeline HANDS_MASK = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/post/hands_mask"))
            .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("godweer:post/hands/hands_mask"))
            .withBindGroupLayout(HANDS_MASK_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_LAVA = handFillPipeline("hand_lava", "godweer:post/hands/hand_lava");    public static final RenderPipeline HAND_FROST = handFillPipeline("hand_frost", "godweer:post/hands/hand_frost");
    public static final RenderPipeline HAND_GALAXY = handFillPipeline("hand_galaxy", "godweer:post/hands/hand_galaxy");
    public static final RenderPipeline HAND_EMBER = handFillPipeline("hand_ember", "godweer:post/hands/hand_ember");
    public static final RenderPipeline HAND_SLIME = handFillPipeline("hand_slime", "godweer:post/hands/hand_slime");
    public static final RenderPipeline HAND_SMOKE = handFillPipeline("hand_smoke", "godweer:post/hands/hand_smoke");
    public static final RenderPipeline HANDS_GLOW_MASK = handsGlowPipeline("hands_glow_mask", "godweer:post/hands/hands_glow_mask", GLOW_HANDS_MASK_LAYOUT, glowFloatTarget());
    public static final RenderPipeline HANDS_GLOW_BLUR = handsGlowPipeline("hands_glow_blur", "godweer:post/hands/hands_glow_blur", GLOW_HANDS_BLUR_LAYOUT, glowFloatTarget());
    public static final RenderPipeline HANDS_GLOW_COMPOSITE = handsGlowPipeline("hands_glow_composite", "godweer:post/hands/hands_glow_composite", GLOW_HANDS_COMPOSITE_LAYOUT, new ColorTargetState(BlendFunction.ADDITIVE));

    private static final BindGroupLayout FIRE_COPY_LAYOUT = BindGroupLayout.builder()
            .withUniform("SourceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final BindGroupLayout FIRE_TRAIL_LAYOUT = BindGroupLayout.builder()
            .withUniform("BeforeColor", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("AfterColor", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("History", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("FireTrailUniforms", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout FIRE_COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withUniform("TrailSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("BeforeColor", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("AfterColor", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("FireCompositeUniforms", UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline FIRE_HANDS_COPY = handsGlowPipeline("fire_hands_copy", "godweer:post/hands/fire_copy", FIRE_COPY_LAYOUT, ColorTargetState.DEFAULT);
    public static final RenderPipeline FIRE_HANDS_TRAIL = handsGlowPipeline("fire_hands_trail", "godweer:post/hands/fire_trail", FIRE_TRAIL_LAYOUT, glowFloatTarget());
    public static final RenderPipeline FIRE_HANDS_COMPOSITE = handsGlowPipeline("fire_hands_composite", "godweer:post/hands/fire_composite", FIRE_COMPOSITE_LAYOUT, new ColorTargetState(BlendFunction.ADDITIVE));

    private static final BindGroupLayout HANDS_RIM_LAYOUT = BindGroupLayout.builder()
            .withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("HandsRimUniforms", UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline HANDS_RIM = handsGlowPipeline("hands_rim", "godweer:post/hands/hands_rim", HANDS_RIM_LAYOUT, new ColorTargetState(BlendFunction.ADDITIVE));

    private static ColorTargetState glowFloatTarget() {
        return new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL);
    }



    public static final RenderPipeline BLOCK_HIGHLIGHT_CAUSTICS = blockOutlinePipeline("caustics", "caustics", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_HIGHLIGHT_CAUSTICS_THROUGH = blockOutlinePipeline("caustics_through", "caustics", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_HIGHLIGHT_GLOSSY = blockOutlinePipeline("glossy", "glossy", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_HIGHLIGHT_GLOSSY_THROUGH = blockOutlinePipeline("glossy_through", "glossy", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_HIGHLIGHT_DEEP_SPACE = blockOutlinePipeline("deep_space", "deep_space", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_HIGHLIGHT_DEEP_SPACE_THROUGH = blockOutlinePipeline("deep_space_through", "deep_space", CompareOp.ALWAYS_PASS);

    // Один и тот же шейдер тела в двух вариантах: обычный (с проверкой глубины)
    // и through-walls (ALWAYS_PASS). Стили перечислены здесь — файл с суффиксом
    // стиля ищет ChamsHook, а ChamsRenderTypes строит по ним RenderType.
    public static final String[] CHAMS_STYLES = {
            "glass", "glow", "plasma", "lava", "hologram", "glitch", "aurora", "spectrum", "cosmos", "toxic"};

    public record ChamsPasses(RenderPipeline normal, RenderPipeline through) {
    }

    public static final Map<String, ChamsPasses> CHAMS_ENTITY = chamsEntityPipelines();

    private static Map<String, ChamsPasses> chamsEntityPipelines() {
        Map<String, ChamsPasses> passes = new LinkedHashMap<>();
        for (String style : CHAMS_STYLES) {
            String fragment = "chams/chams_entity_" + style;
            passes.put(style, new ChamsPasses(
                    chamsEntityPipeline("entity_" + style, fragment, CompareOp.GREATER_THAN_OR_EQUAL),
                    chamsEntityPipeline("entity_" + style + "_through", fragment, CompareOp.ALWAYS_PASS)));
        }
        return Collections.unmodifiableMap(passes);
    }

    public static final GpuFormat SKY_CLOUDS_FORMAT = GpuFormat.RGBA16_FLOAT;

    public static final RenderPipeline WORLD_SKY_STARRY_SKY_SPACE = skyCloudsPipeline("sky_clouds_starry_sky", "godweer:world/sky/clouds_starry_sky");
    public static final RenderPipeline WORLD_SKY_CLOUDS_NEBULA = skyCloudsPipeline("sky_clouds_nebula", "godweer:world/sky/clouds_nebula");
    public static final RenderPipeline WORLD_SKY_CLOUDS_PLASMA = skyCloudsPipeline("sky_clouds_plasma", "godweer:world/sky/clouds_plasma");
    public static final RenderPipeline WORLD_SKY_CLOUDS_CAUSTIC = skyCloudsPipeline("sky_clouds_caustic", "godweer:world/sky/clouds_caustic");
    public static final RenderPipeline WORLD_SKY_CAUSTIC = worldPostPipeline("sky_caustic", "godweer:world/sky/caustic", WORLD_SKY_LAYOUT);
    public static final RenderPipeline WORLD_SKY_STARRY_SKY = worldPostPipeline("sky_starry_sky", "godweer:world/sky/starry_sky", WORLD_SKY_LAYOUT);
    public static final RenderPipeline WORLD_SKY_NEBULA = worldPostPipeline("sky_nebula", "godweer:world/sky/nebula", WORLD_SKY_LAYOUT);
    public static final RenderPipeline WORLD_SKY_PLASMA = worldPostPipeline("sky_plasma", "godweer:world/sky/plasma", WORLD_SKY_LAYOUT);
    public static final RenderPipeline WORLD_SKY_AURORA = worldPostPipeline("sky_aurora_veil", "godweer:world/sky/aurora_veil", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_CRIMSON = worldPostPipeline("sky_crimson_nebula", "godweer:world/sky/crimson_nebula", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_OCEAN = worldPostPipeline("sky_ocean_abyss", "godweer:world/sky/ocean_abyss", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_SUNSET = worldPostPipeline("sky_sunset_bloom", "godweer:world/sky/sunset_bloom", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_STORM = worldPostPipeline("sky_storm_veil", "godweer:world/sky/storm_veil", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_EMERALD = worldPostPipeline("sky_emerald_flow", "godweer:world/sky/emerald_flow", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_SOLAR = worldPostPipeline("sky_solar_winds", "godweer:world/sky/solar_winds", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SKY_VOID = worldPostPipeline("sky_void_pulse", "godweer:world/sky/void_pulse", WORLD_SKY_CLOUDS_LAYOUT);
    public static final RenderPipeline WORLD_SATURATION = worldPostPipeline("saturation", "godweer:post/world_saturation", SATURATION_LAYOUT);
    private static final BindGroupLayout VOLUMETRIC_FOG_LAYOUT = BindGroupLayout.builder()
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("VolumetricFogUniforms", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout LIGHTNING_LAYOUT = BindGroupLayout.builder()
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("LightningUniforms", UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline WORLD_LIGHTNING = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/lightning"))
            .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("godweer:post/world_lightning"))
            .withBindGroupLayout(LIGHTNING_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();
    public static final RenderPipeline WORLD_VOLUMETRIC_FOG = RenderPipeline.builder()
            .withLocation(Identifier.parse("godweer:pipeline/world/volumetric_fog"))
            .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("godweer:post/world_volumetric_fog"))
            .withBindGroupLayout(VOLUMETRIC_FOG_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final GpuFormat EFFECT_FORMAT = GpuFormat.RGBA8_UNORM;

    private static RenderPipeline chamsEntityPipeline(String name, String fragment, CompareOp depthCompare) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/world/chams/" + name))
                .withVertexShader(Identifier.parse("godweer:world/chams_entity"))
                .withFragmentShader(Identifier.parse("godweer:world/" + fragment))
                .withBindGroupLayout(CHAMS_ENTITY_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depthCompare, false))
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(true)
                .build();
    }

    private static RenderPipeline blockOutlinePipeline(String name, String fragment, CompareOp depthCompare) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/world/blocks/" + name))
                .withVertexShader(Identifier.parse("godweer:world/blocks"))
                .withFragmentShader(Identifier.parse("godweer:world/blocks/" + fragment))
                .withBindGroupLayout(BLOCK_HIGHLIGHT_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depthCompare, false))
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline handFillPipeline(String name, String fragment) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/post/" + name))
                .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(HAND_FILL_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(Optional.empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline handsGlowPipeline(String name, String fragment, BindGroupLayout layout, ColorTargetState colorTarget) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/post/" + name))
                .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(layout)
                .withColorTargetState(colorTarget)
                .withDepthStencilState(Optional.empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline worldPostPipeline(String name,
                                                    String fragment,
                                                    BindGroupLayout layout) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(layout)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline skyCloudsPipeline(String name, String fragment) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("godweer:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(WORLD_SKY_CLOUDS_LAYOUT)
                .withColorTargetState(new ColorTargetState(Optional.empty(), SKY_CLOUDS_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }




    private PiplinePost() {
    }
}
