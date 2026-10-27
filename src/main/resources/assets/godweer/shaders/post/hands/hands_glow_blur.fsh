#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D HaloSampler;

layout(std140) uniform GlowBlurUniforms {
    vec4 BlurParams; // src texelX, src texelY, offset in px, unused
};

// One Kawase-style blur iteration over the mask alpha: center tap,
// 4 axis taps and 4 diagonal taps around the offset radius.
void main() {
    vec2 o = BlurParams.z * BlurParams.xy;

    float sum = texture(HaloSampler, uv).a;
    sum += texture(HaloSampler, uv + vec2(o.x, 0.0)).a;
    sum += texture(HaloSampler, uv - vec2(o.x, 0.0)).a;
    sum += texture(HaloSampler, uv + vec2(0.0, o.y)).a;
    sum += texture(HaloSampler, uv - vec2(0.0, o.y)).a;
    sum += texture(HaloSampler, uv + o).a;
    sum += texture(HaloSampler, uv - o).a;
    sum += texture(HaloSampler, uv + vec2(o.x, -o.y)).a;
    sum += texture(HaloSampler, uv + vec2(-o.x, o.y)).a;

    finalColor = vec4(0.0, 0.0, 0.0, sum / 9.0);
}
