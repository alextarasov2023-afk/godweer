#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D BeforeDepth;
uniform sampler2D AfterDepth;

layout(std140) uniform GlowHandsMaskUniforms {
    vec4 MaskParams; // texelX, texelY, left hand, right hand
};

// Hands are the closest geometry drawn after the scene snapshot, so a hand
// pixel is where the depth value jumped after the hand pass. abs-delta works
// for both depth conventions (reverse-Z: hand ~1, standard: hand ~0).
// 3x3 taps soften the silhouette edge.
void main() {
    float side = uv.x < 0.5 ? MaskParams.z : MaskParams.w;
    if (side < 0.5) {
        finalColor = vec4(0.0);
        return;
    }
    float m = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 c = uv + vec2(float(x), float(y)) * MaskParams.xy;
            float d0 = texture(BeforeDepth, c).r;
            float d1 = texture(AfterDepth, c).r;
            float d = d1 - d0;
            m += (d > 0.0012 || d < -0.0012) ? 1.0 : 0.0;
        }
    }
    finalColor = vec4(0.0, 0.0, 0.0, clamp(m / 9.0 * 1.35, 0.0, 1.0));
}
