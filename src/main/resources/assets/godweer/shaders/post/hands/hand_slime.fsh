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

float blob(vec2 x, float time) {
    vec2 n = floor(x);
    vec2 f = fract(x);
    float md = 8.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(n + g);
            o = 0.5 + 0.4 * sin(time * 0.8 + 6.2831 * o);
            vec2 r = g + o - f;
            md = min(md, dot(r, r));
        }
    }
    return sqrt(md);
}

void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float time = FillParams.x;
    vec2 st = uv * 9.0 + vec2(0.0, -time * 0.25);

    float b = blob(st, time);
    float body = 1.0 - smoothstep(0.0, 0.55, b);
    float ring = smoothstep(0.02, 0.16, b) * (1.0 - smoothstep(0.16, 0.34, b));
    float goo = vnoise(uv * 5.0 + vec2(time * 0.2, 0.0));

    vec3 col = FillColor.rgb * (0.22 + 0.45 * goo) + FillColor.rgb * body * 0.5;
    col += FillColor.rgb * 1.7 * ring;
    col += vec3(1.0) * pow(max(0.35 - b, 0.0) * 2.8, 2.0) * 0.9;

    finalColor = vec4(clamp(col, 0.0, 1.0), clamp(mask * FillColor.a, 0.0, 1.0));
}
