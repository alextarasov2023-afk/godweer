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

// Nebula: облако фиолетового и синего газа медленно вращается прямо на кисти,
// редкие звёзды вспыхивают по одной. Плотность держится низкой, чтобы рука
// не превращалась в кашу.
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

    vec3 q = p * 9.0 + vec3(time * 0.05, -time * 0.04, time * 0.03);
    float cloudA = fbm3(q);
    float cloudB = fbm3(q * 1.9 - vec3(time * 0.03) + cloudA * 1.4);
    float cloud = smoothstep(0.34, 0.82, cloudA * 0.65 + cloudB * 0.40);

    vec3 deep = vec3(0.010, 0.012, 0.040);
    vec3 blue = mix(base, vec3(0.28, 0.48, 1.0), 0.55);
    vec3 magenta = mix(base, vec3(0.84, 0.30, 0.92), 0.60);
    vec3 col = deep
            + blue * pow(cloudA, 1.6) * 0.75
            + magenta * pow(cloudB, 2.0) * 0.55
            + mix(blue, vec3(1.0), 0.35) * pow(cloud, 3.0) * 0.45;

    vec3 starCell = floor(p * 140.0);
    float star = step(0.9975, hash31(starCell));
    col += vec3(0.86, 0.93, 1.0) * star * (0.5 + 0.5 * sin(time * 2.2 + hash31(starCell + 7.0) * 40.0)) * 1.4;
    col += mix(blue, vec3(1.0), 0.5) * fres * 0.7;

    finalColor = vec4(clamp(col, 0.0, 1.5), clamp(mask * FillColor.a * (0.74 + 0.26 * fres), 0.0, 1.0));
}
