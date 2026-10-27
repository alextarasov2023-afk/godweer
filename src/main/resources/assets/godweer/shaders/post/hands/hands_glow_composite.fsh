#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D MaskSampler;   // full-res hand coverage
uniform sampler2D NearSampler;   // tight blurred halo field
uniform sampler2D FarSampler;    // wide blurred halo field

layout(std140) uniform GlowCompositeUniforms {
    vec4 GlowColor;  // rgb
    vec4 Params0;    // texelX, texelY, time, gap in px
    vec4 Params1;    // intensity, pulse speed, sharpness, unused
};

// Glow ring only: the halo field is cut off at a dilated hand silhouette
// (gap), so the outline never touches the skin. Contrast curve kills the
// soapy look, pow() adds a hot core line right outside the gap.
void main() {
    float core = texture(MaskSampler, uv).a;

    vec2 g = max(Params0.w, 0.0) * Params0.xy;
    float sep = core;
    sep = max(sep, texture(MaskSampler, uv + vec2(g.x, 0.0)).a);
    sep = max(sep, texture(MaskSampler, uv - vec2(g.x, 0.0)).a);
    sep = max(sep, texture(MaskSampler, uv + vec2(0.0, g.y)).a);
    sep = max(sep, texture(MaskSampler, uv - vec2(0.0, g.y)).a);
    sep = max(sep, texture(MaskSampler, uv + g * vec2(0.7071, 0.7071)).a);
    sep = max(sep, texture(MaskSampler, uv + g * vec2(-0.7071, 0.7071)).a);
    sep = max(sep, texture(MaskSampler, uv + g * vec2(0.7071, -0.7071)).a);
    sep = max(sep, texture(MaskSampler, uv + g * vec2(-0.7071, -0.7071)).a);
    sep = clamp(sep, 0.0, 1.0);

    float near = texture(NearSampler, uv).a;
    float far = texture(FarSampler, uv).a;
    float field = clamp(near * 0.95 + far * 0.5, 0.0, 1.0);

    float glow = field * (1.0 - sep);

    float sharp = clamp(Params1.z, 0.0, 1.0);
    glow = smoothstep(0.02 + 0.10 * sharp, 0.62 - 0.30 * sharp, glow);

    float pulse = 0.85 + 0.15 * sin(Params0.z * Params1.y * 6.2831853);
    vec3 hot = mix(GlowColor.rgb, vec3(1.0), 0.5);

    vec3 col = GlowColor.rgb * glow * 0.85;
    col += hot * pow(glow, 3.0) * 0.9;
    col *= Params1.x * pulse;

    float lum = max(col.r, max(col.g, col.b));
    if (lum < 0.004) {
        discard;
    }
    finalColor = vec4(col, clamp(lum, 0.0, 1.0));
}
