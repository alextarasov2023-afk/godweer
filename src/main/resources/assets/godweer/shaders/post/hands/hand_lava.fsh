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
    vec2 st = uv * vec2(7.0, 11.0);

    float flow = fbm(st * 0.7 + vec2(time * 0.25, -time * 0.35));
    float detail = fbm(st * 1.6 - flow * 1.8 + vec2(-time * 0.2, time * 0.3));
    float rivers = pow(1.0 - abs(flow * 0.65 + detail * 0.35 - 0.5) * 2.0, 6.0);

    float pulse = 0.75 + 0.25 * sin(time * 2.0 + flow * 10.0);
    vec3 crust = mix(vec3(0.09, 0.03, 0.02), FillColor.rgb * 0.35, detail);
    vec3 hot = mix(vec3(1.0, 0.78, 0.30), FillColor.rgb * 1.8, 0.45);
    vec3 col = mix(crust, hot, clamp(rivers * 1.4, 0.0, 1.0) * pulse);
    col += hot * pow(rivers, 3.0) * 0.8 * pulse;

    finalColor = vec4(clamp(col, 0.0, 1.0), clamp(mask * FillColor.a, 0.0, 1.0));
}
