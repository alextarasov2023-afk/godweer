#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D BeforeDepth;
uniform sampler2D AfterDepth;

layout(std140) uniform HandsMaskUniforms {
    vec4 MaskParams;
};

// Hand pixels are the ones that moved closer (larger depth in reverse-Z:
// sky is ~0, near geometry is ~1) after the hands were drawn.
// 3x3 taps soften the edge.
void main() {
    float side = uv.x < 0.5 ? MaskParams.z : MaskParams.w;
    if (side < 0.5) {
        discard;
    }
    float m = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 c = uv + vec2(float(x), float(y)) * MaskParams.xy;
            float d0 = texture(BeforeDepth, c).r;
            float d1 = texture(AfterDepth, c).r;
            m += (d0 + 0.0008 < d1) ? 1.0 : 0.0;
        }
    }
    m /= 9.0;
    if (m < 0.03) {
        discard;
    }
    finalColor = vec4(0.0, 0.0, 0.0, clamp(m * 1.6, 0.0, 1.0));
}
