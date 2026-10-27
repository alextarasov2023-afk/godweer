#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D BeforeColor;
uniform sampler2D AfterColor;
uniform sampler2D History;

layout(std140) uniform FireTrailUniforms {
    vec4 ViewInfo; // texelX, texelY, time, scale (height / 1080)
    vec4 SimA;     // rise, wobble, turbulence, decay
    vec4 SimB;     // width, spread, strength, frameScale (dt * 60)
};

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Всё, что изменилось между «до» и «после», — руки/предмет
float diffMask(vec2 c) {
    vec3 a = texture(BeforeColor, c).rgb;
    vec3 b = texture(AfterColor,  c).rgb;
    return smoothstep(0.012, 0.05, length(b - a));
}

void main() {
    vec2 texel = ViewInfo.xy;
    vec2 p = uv / texel / ViewInfo.w;
    float s = SimB.w * ViewInfo.w;

    // Смещение истории: подъём вверх + колебания + турбулентность
    float wob = sin(p.y * 0.035 + ViewInfo.z * 5.0) * SimA.y;
    float tur = (noise(p * 0.04 + vec2(0.0, ViewInfo.z * 3.0)) - 0.5) * 4.0 * SimA.z;
    vec2 off = vec2(wob + tur, SimA.x) * s;
    vec2 src = uv - off * texel;

    // Лёгкое размытие следа (распространение огня)
    float r = (1.0 + SimB.y) * ViewInfo.w;
    vec4 h = texture(History, src) * 0.4
           + (texture(History, src + vec2( r, 0.0) * texel)
            + texture(History, src + vec2(-r, 0.0) * texel)
            + texture(History, src + vec2(0.0,  r) * texel)
            + texture(History, src + vec2(0.0, -r) * texel)) * 0.15;

    // Угасание + рваная эрозия языков пламени
    float erode = mix(1.0, 0.8 + 0.4 * noise(p * 0.08 - vec2(0.0, ViewInfo.z * 4.0)),
                      clamp(SimA.z * 0.35, 0.0, 1.0));
    h.a *= SimA.w * pow(clamp(erode, 0.0, 1.0), SimB.w);

    // Поджиг по внешнему контуру руки (обводка вокруг силуэта для второго плана)
    float center = diffMask(uv);
    float best = center * 0.45;
    vec3 col = texture(AfterColor, uv).rgb;
    float w = max(SimB.x, 2.5) * ViewInfo.w;
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * 0.5235988;
        vec2 d = vec2(cos(ang), sin(ang));
        for (int k = 1; k <= 3; k++) {
            vec2 c = uv + d * w * (float(k) / 3.0) * texel;
            float mm = diffMask(c) * (1.0 - 0.22 * float(k - 1));
            if (mm > best) {
                best = mm;
                col = texture(AfterColor, c).rgb;
            }
        }
    }

    // Усиливаем горение именно на границе (обводка позади руки)
    float edgeBoost = clamp(best - center * 0.65, 0.0, 1.0);
    float ignite = max(best * 0.75, edgeBoost * 1.35);

    float heat = ignite * SimB.z * (0.78 + 0.48 * noise(p * 0.1 + vec2(0.0, -ViewInfo.z * 6.0)));
    float a = max(h.a, heat);
    float wNew = heat / (heat + h.a + 1e-4);
    vec3 rgb = mix(h.rgb, col, wNew); // rgb хранит цвет предмета (для режима Item)

    finalColor = vec4(rgb, clamp(a, 0.0, 4.0));
}
