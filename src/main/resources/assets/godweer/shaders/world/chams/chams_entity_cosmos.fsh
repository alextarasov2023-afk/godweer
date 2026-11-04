#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash13(i + vec3(0.0, 0.0, 0.0)), hash13(i + vec3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

// Cosmos: the body is a window into deep space — drifting nebula, a milky band
// and hard star points. vertexColor.rgb = base colour, a = phase.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    vec3 p = viewPos * 1.9;
    float drift = phase * 0.22;

    // Three octaves drifting at different rates read as depth.
    float neb = vnoise(p + vec3(0.0, drift, 0.0)) * 0.6
            + vnoise(p * 2.3 - vec3(drift * 0.7, 0.0, drift * 0.4)) * 0.3
            + vnoise(p * 4.7 + vec3(0.0, 0.0, drift * 1.3)) * 0.18;
    neb = smoothstep(0.28, 1.0, neb);

    // Milky band: a second field stretched along one axis of the body.
    float bandAxis = (p.y - p.x * 0.4) * 0.55;
    float band = exp(-bandAxis * bandAxis) * neb;

    vec3 deep = vec3(0.015, 0.015, 0.045);
    vec3 violet = mix(vec3(0.30, 0.12, 0.70), base, 0.5);
    vec3 cyan = mix(vec3(0.10, 0.45, 0.70), base, 0.35);
    vec3 col = deep + violet * neb * 1.1 + cyan * band * 0.9;

    // Stars: high-frequency grid, only the top of the hash survives, and they
    // twinkle off the phase instead of popping.
    vec3 sp = p * 9.0;
    float star = hash13(floor(sp));
    float twinkle = 0.55 + 0.45 * sin(phase * 3.0 + star * 6.28318);
    col += vec3(0.95, 0.97, 1.0) * step(0.994, star) * twinkle * 3.0;

    float rim = pow(1.0 - ndv, 2.4);
    col += mix(base, vec3(0.7, 0.8, 1.0), 0.5) * rim * 1.6;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(0.7 + lum * 0.3, 0.0, 1.0));
}
