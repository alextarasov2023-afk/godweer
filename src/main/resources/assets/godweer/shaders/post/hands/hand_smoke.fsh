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

void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float time = FillParams.x;
    vec2 st = vec2(uv.x * 5.0, uv.y * 3.0 - time * 0.35);

    float warp = fbm(st * 1.5 + vec2(0.0, -time * 0.2));
    float s = fbm(st + warp * 2.2);
    float wisps = smoothstep(0.32, 0.78, s);
    float edges = pow(wisps, 3.0);

    vec3 col = mix(vec3(0.015, 0.015, 0.02), FillColor.rgb * (0.30 + 0.55 * wisps), wisps);
    col += FillColor.rgb * 1.5 * edges;
    col += FillColor.rgb * pow(max(s - 0.62, 0.0) * 2.6, 2.0);

    finalColor = vec4(clamp(col, 0.0, 1.0), clamp(mask * FillColor.a, 0.0, 1.0));
}
