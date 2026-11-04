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

float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amp * vnoise3(p);
        p = p * 2.07 + 11.7;
        amp *= 0.5;
    }
    return sum;
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

// Ember: обугленная кисть, из трещин пробивается золото, ядра трещин уходят
// в белый. Трещины ползут по поверхности и повторяют её изгибы.
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
    float fres = pow(1.0 - ndv, 2.2);

    float flow = fbm3(p * 12.0 + vec3(time * 0.18, -time * 0.26, time * 0.10));
    float detail = fbm3(p * 26.0 - vec3(time * 0.12, time * 0.20, 0.0) + flow * 1.6);
    float crack = pow(max(1.0 - abs(flow * 0.62 + detail * 0.38 - 0.5) * 2.0, 0.0), 6.0);

    vec3 charred = vec3(0.045, 0.016, 0.010);
    vec3 gold = vec3(1.0, 0.70, 0.28);
    vec3 hot = mix(gold, base * 1.6, 0.40);
    vec3 col = mix(charred, hot * 0.50, smoothstep(0.18, 0.88, detail));
    col += hot * crack * 1.9;
    col += vec3(1.0, 0.94, 0.78) * pow(crack, 4.0) * 1.2;

    // Редкие искры, поднимающиеся вверх по экрану.
    float spark = step(0.9965, hash31(floor(p * 170.0 + vec3(0.0, time * 5.0, 0.0))));
    col += gold * spark * 1.5;
    col += mix(gold, vec3(1.0), 0.45) * fres * 0.85;

    finalColor = vec4(clamp(col, 0.0, 1.5), clamp(mask * FillColor.a * (0.78 + 0.22 * fres), 0.0, 1.0));
}
