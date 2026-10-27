#version 330 core

in vec2 uv;
in vec4 tint;
out vec4 finalColor;

uniform sampler2D BloomSampler;

layout(std140) uniform WorldParticleUniforms {
    vec4 params;
};

void main() {
    float intensity = texture(BloomSampler, uv).r;
    float strength = intensity * tint.a;
    if (strength <= 0.004) {
        discard;
    }
    vec3 glow = tint.rgb * strength;
    glow = mix(glow, vec3(strength), pow(intensity, 4.0) * 0.85);
    finalColor = vec4(glow * params.x, strength);
}
