#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D MaskSampler;   // full-res hand coverage (alpha = mask)

layout(std140) uniform HandsRimUniforms {
    vec4 RimColor;   // rgb + intensity (w)
    vec4 RimParams;  // texelX, texelY, width in px, unused
};

// Plain outline: a dilated mask minus the mask itself gives a crisp flat ring
// around the hands. Same depth-difference mask tech as GlowHands, no blur —
// just a hard colored border with a slight AA from the linear sampler.
void main() {
    vec2 texel = vec2(RimParams.x, RimParams.y);
    float width = max(RimParams.z, 1.0);

    float center = texture(MaskSampler, uv).a;
    float far = center;

    // 8 directions x 3 rings -> dilate by up to `width` pixels
    for (int i = 0; i < 8; i++) {
        float ang = float(i) * 0.7853981633974483;
        vec2 dir = vec2(cos(ang), sin(ang));
        for (int k = 1; k <= 3; k++) {
            float dist = width * (float(k) / 3.0);
            far = max(far, texture(MaskSampler, uv + dir * dist * texel).a);
        }
    }

    float band = clamp(far - center, 0.0, 1.0);
    // soften the hard binary edge a bit (mask is bilinear-sampled already)
    band = smoothstep(0.15, 0.85, band);
    if (band < 0.004) {
        discard;
    }

    vec3 col = RimColor.rgb * band * RimColor.w;
    finalColor = vec4(col, clamp(band * RimColor.w, 0.0, 1.0));
}
