#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D MaskSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform HandFillUniforms {
    vec4 FillColor;
    vec4 FillParams;
    mat4 InvProj;
};

float hash21(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash31(i), hash31(i + vec3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hash31(i + vec3(0.0, 1.0, 0.0)), hash31(i + vec3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hash31(i + vec3(0.0, 0.0, 1.0)), hash31(i + vec3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hash31(i + vec3(0.0, 1.0, 1.0)), hash31(i + vec3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

vec3 handPosition() {
    float depth = texture(DepthSampler, uv).r;
    vec4 clip = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 view = InvProj * clip;
    return view.xyz / max(abs(view.w), 1e-6);
}

vec3 handNormal(vec3 p) {
    vec3 n = cross(dFdx(p), dFdy(p));
    float len = length(n);
    return len > 1e-9 ? n / len : vec3(0.0, 0.0, -1.0);
}

// Frost: ледяная глазурь с кристаллическими гранями. Каждая грань чуть
// отличается по тону, по поверхности идут морозные прожилки, силуэт горит
// холодным белым.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float time = FillParams.x;
    vec3 base = FillColor.rgb;
    vec3 p = handPosition();
    vec3 N = handNormal(p);
    float ndv = clamp(abs(dot(N, normalize(-p))), 0.0, 1.0);
    float fres = pow(1.0 - ndv, 2.4);

    // Грани: ближайший центр ячейки в проекции кисти.
    vec2 g = p.xy * 26.0;
    vec2 id = floor(g);
    vec2 local = fract(g) - 0.5;
    vec2 jitter = vec2(hash21(id), hash21(id + 19.7)) - 0.5;
    float facet = length(local - jitter * 0.55);
    float shard = 1.0 - smoothstep(0.18, 0.46, facet);
    float tone = hash21(id + 7.3);

    float grain = vnoise3(p * 60.0 + vec3(0.0, -time * 0.08, 0.0));
    float vein = pow(1.0 - abs(vnoise3(p * 24.0 + 3.7) * 2.0 - 1.0), 6.0);

    vec3 iceDeep = base * 0.30 + vec3(0.05, 0.08, 0.13);
    vec3 iceLit = mix(vec3(0.85, 0.95, 1.0), base * 1.6, 0.45);
    vec3 col = mix(iceDeep, iceLit, grain * 0.35 + shard * 0.30 + tone * 0.15);
    col += iceLit * vein * 0.75;

    float sweep = pow(0.5 + 0.5 * sin((p.x + p.y * 0.6) * 10.0 - time * 0.9), 14.0);
    col += iceLit * sweep * 0.35;
    col += vec3(0.92, 0.98, 1.0) * fres * 1.15;

    finalColor = vec4(clamp(col, 0.0, 1.5), clamp(mask * FillColor.a * (0.76 + 0.24 * fres), 0.0, 1.0));
}
