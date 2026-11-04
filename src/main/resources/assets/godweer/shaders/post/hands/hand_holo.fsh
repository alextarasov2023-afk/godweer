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

// Holo: голографическая плёнка. Толщина плёнки берётся из fresnel и положения
// на кисти, поэтому радуга идёт по форме руки — как мыльный пузырь, — а по
// поверхности время от времени проходит световой блик.
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
    float fres = pow(1.0 - ndv, 2.0);

    float thickness = fres * 2.2
                    + (p.x * 4.2 + p.y * 3.4)
                    + sin(time * 0.45) * 0.20;

    vec3 film = 0.5 + 0.5 * cos(6.28318 * (vec3(0.0, 0.33, 0.67) + thickness));
    film = pow(film, vec3(1.15));

    // Цвет темы подмешивается в плёнку, чтобы радуга не была чужой.
    vec3 col = mix(film, film * base * 1.55, 0.35);

    // Диагональный блик проезжает по поверхности и читается как отражение.
    float sweep = pow(0.5 + 0.5 * sin((p.y * 11.0 - p.x * 4.0) - time * 1.3), 12.0);
    col += vec3(1.0) * sweep * 0.45;
    col += vec3(1.0) * fres * 0.55;

    finalColor = vec4(clamp(col, 0.0, 1.5), clamp(mask * FillColor.a * (0.76 + 0.24 * fres), 0.0, 1.0));
}
