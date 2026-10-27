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

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * vnoise(p);
        p = p * 2.03 + 11.7;
        amp *= 0.5;
    }
    return sum;
}

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float starLayer(vec2 p, float time, float thresh) {
    vec2 c = floor(p);
    vec2 f = fract(p);
    float r = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(c + g);
            vec2 d = g + o - f;
            float tw = 0.5 + 0.5 * sin(time * 3.0 + o.x * 40.0);
            r += smoothstep(0.10, 0.0, dot(d, d)) * step(thresh, o.y) * (0.4 + 0.6 * tw);
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

    float ang = 0.4 + time * 0.05;
    mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
    vec2 sp = rot * (uv - 0.5);
    float neb = fbm(sp * 5.0 + vec2(time * 0.08, 0.0));
    float neb2 = fbm(sp * 9.0 - vec2(0.0, time * 0.06) + 4.2);

    vec3 deep = FillColor.rgb * 0.12 + vec3(0.02, 0.01, 0.05);
    vec3 col = mix(deep, FillColor.rgb * 0.9 + vec3(0.25, 0.1, 0.45), smoothstep(0.3, 0.85, neb * 0.7 + neb2 * 0.4));
    col += FillColor.rgb * pow(max(neb - 0.5, 0.0) * 2.0, 2.0) * 0.9;

    float st1 = starLayer(uv * 26.0, time, 0.55);
    float st2 = starLayer(uv * 52.0 + 7.3, time * 1.4, 0.72);
    col += vec3(1.0, 0.98, 0.95) * st1 * 0.9 + vec3(0.75, 0.85, 1.0) * st2;

    finalColor = vec4(clamp(col, 0.0, 1.0), clamp(mask * FillColor.a, 0.0, 1.0));
}
