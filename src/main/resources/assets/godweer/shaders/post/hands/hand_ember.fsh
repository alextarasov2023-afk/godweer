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

float embers(vec2 p, float time) {
    vec2 c = floor(p);
    vec2 f = fract(p);
    float r = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(c + g);
            vec2 d = vec2((g.x + o.x - f.x) * 2.0, (g.y + o.y - f.y) * 0.6);
            float flick = 0.5 + 0.5 * sin(time * 4.0 + o.x * 50.0);
            r += smoothstep(0.12, 0.0, dot(d, d)) * step(0.6, o.y) * (0.3 + 0.7 * flick);
        }
    }
    return r;
}

void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float time = FillParams.x;
    float heat = vnoise(uv * 5.0 + vec2(0.0, -time * 0.4));

    vec3 col = mix(vec3(0.05, 0.02, 0.01), FillColor.rgb * 0.28, heat);
    col += FillColor.rgb * pow(heat, 3.0) * 0.8;

    vec2 sp = vec2(uv.x * 14.0, uv.y * 22.0 - time * 1.6);
    float em = embers(sp, time) + embers(sp * 1.7 + 3.1, time * 1.3) * 0.6;
    vec3 spark = mix(vec3(1.0, 0.55, 0.15), FillColor.rgb * 1.8, 0.5);
    col += spark * em * 1.5;

    finalColor = vec4(clamp(col, 0.0, 1.0), clamp(mask * FillColor.a, 0.0, 1.0));
}
