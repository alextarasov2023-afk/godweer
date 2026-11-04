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

// Caustics: свет под водой. Три слоя искажённых синусов дают бегущую сетку
// бликов, глубокий синий фон и яркие гребни на изгибах кисти.
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

    vec2 q = p.xy * 7.0;
    float web = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        q = q * 1.62 + vec2(sin(time * 0.26 + fi * 1.7), cos(time * 0.22 + fi * 2.1));
        float v = sin(q.x + sin(q.y + time * 0.38)) * sin(q.y + sin(q.x - time * 0.31));
        web += pow(abs(v), 6.0);
    }
    web = smoothstep(0.08, 0.52, web);

    vec3 deep = mix(vec3(0.010, 0.050, 0.080), base * 0.35, 0.50);
    vec3 lit = mix(vec3(0.68, 1.0, 1.0), base * 1.60, 0.45);
    vec3 col = deep * 0.70 + lit * web * 1.05;
    col += lit * pow(web, 4.0) * 0.95;
    col += mix(lit, vec3(1.0), 0.4) * fres * 0.6;

    finalColor = vec4(clamp(col, 0.0, 1.5), clamp(mask * FillColor.a * (0.74 + 0.26 * fres), 0.0, 1.0));
}
