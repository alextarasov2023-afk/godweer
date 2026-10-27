#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D MaskSampler;

layout(std140) uniform HandFillUniforms {
    vec4 FillColor;
    vec4 FillParams;
};

float hash21(vec2 value) {
    value = fract(value * vec2(123.34, 345.45));
    value += dot(value, value + 34.345);
    return fract(value.x * value.y);
}

float vnoise(vec2 value) {
    vec2 cell = floor(value);
    vec2 local = fract(value);
    local = local * local * (3.0 - 2.0 * local);
    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float facet(vec2 x) {
    vec2 n = floor(x);
    vec2 f = fract(x);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(n + g);
            float d = length(g + o - f);
            if (d < f1) {
                f2 = f1;
                f1 = d;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    return f2 - f1;
}

void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float time = FillParams.x;
    vec2 st = uv * 12.0;

    float edge = facet(st + vec2(time * 0.05, -time * 0.03));
    float crystal = 1.0 - smoothstep(0.0, 0.25, edge);
    float frost = vnoise(uv * 6.0 + vec2(0.0, -time * 0.15));

    float glint = pow(vnoise(uv * 42.0 + vec2(time * 0.4, time * 0.23)), 18.0) * 3.0;

    float sweep = smoothstep(0.0, 0.45, fract((uv.x + uv.y * 0.6) - time * 0.12) * 1.4);

    vec3 iceDark = FillColor.rgb * 0.38 + vec3(0.08, 0.10, 0.13);
    vec3 iceLight = mix(vec3(1.0), FillColor.rgb * 1.7, 0.55);
    vec3 ice = mix(iceDark, iceLight, crystal * 0.7 + frost * 0.3);
    vec3 glintCol = mix(vec3(1.0), FillColor.rgb * 1.5, 0.4);
    vec3 col = ice * (0.55 + 0.45 * sweep) + glintCol * (crystal * 0.35 + glint);

    finalColor = vec4(clamp(col, 0.0, 1.0), clamp(mask * FillColor.a, 0.0, 1.0));
}
