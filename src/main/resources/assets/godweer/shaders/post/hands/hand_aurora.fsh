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

// Позиция пикселя руки в пространстве вида: узор лежит на поверхности кисти
// и едет вместе с ней, а не плавает по экрану.
vec3 handPosition() {
    float depth = texture(DepthSampler, uv).r;
    vec4 clip = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 view = InvProj * clip;
    return view.xyz / max(abs(view.w), 1e-6);
}

// Нормаль из производных восстановленной позиции: fresnel считается по форме
// кисти (костяшки, большая плоскость ладони), а не по плоской маске.
vec3 handNormal(vec3 p) {
    vec3 n = cross(dFdx(p), dFdy(p));
    float len = length(n);
    return len > 1e-9 ? n / len : vec3(0.0, 0.0, -1.0);
}

// Aurora: медленные полосы света ползут по кисти снизу вверх, бирюза у края
// переходит в фиолет у силуэта. Никакой звёздной пыли — только чистое сияние.
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

    // Две бегущие волны вдоль кисти; фаза растянута, чтобы полос было немного.
    float flow = p.y * 9.0 + sin(p.x * 6.0 + time * 0.55) * 1.5;
    float fold = 0.5 + 0.5 * sin(flow - time * 1.15);
    float veil = smoothstep(0.22, 0.9, fold);
    float core = pow(veil, 3.0);

    vec3 teal = mix(base, vec3(0.16, 1.0, 0.74), 0.55);
    vec3 violet = mix(base, vec3(0.56, 0.36, 1.0), 0.60);

    vec3 col = mix(teal * 0.20, violet * 0.30, veil);
    col += teal * core * 0.90;
    col += violet * pow(core, 2.0) * 0.40;
    col += mix(teal, vec3(1.0), 0.55) * fres * 0.85;

    finalColor = vec4(clamp(col, 0.0, 1.5), clamp(mask * FillColor.a * (0.72 + 0.28 * fres), 0.0, 1.0));
}
