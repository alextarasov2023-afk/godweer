#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D TrailSampler;
uniform sampler2D BeforeColor;
uniform sampler2D AfterColor;
uniform sampler2D DepthSampler;

layout(std140) uniform FireCompositeUniforms {
    vec4 ColorInfo; // custom rgb + прозрачность пламени (alpha)
    vec4 ClientA;   // режим Client, цвет A
    vec4 ClientB;   // режим Client, цвет B
    vec4 ViewInfo;  // texelX, texelY, time, 0
    vec4 FxInfo;    // scale (height / 1080), rise, brightness, spread
    vec4 ModeInfo;  // rainbowPeriod, pulseSpeed, mode, hasDepth
};

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
    return v;
}

float handMaskAt(vec2 c) {
    float diff = length(texture(AfterColor, c).rgb - texture(BeforeColor, c).rgb);
    float m = smoothstep(0.008, 0.036, diff);
    if (ModeInfo.w > 0.5) {
        m *= step(texture(DepthSampler, c).r, 0.999999);
    }
    return m;
}

void main() {
    vec2 texel = ViewInfo.xy;
    float scale = max(FxInfo.x, 0.5);

    // Маска самой руки (передний план). Там, где нарисована рука/предмет,
    // пламя полностью отсекается — сначала рука на переднем плане, а сзади неё огненная обводка.
    float handCenter = handMaskAt(uv);
    float handCore = handCenter;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(float(x), float(y)) * texel * 0.75;
            handCore = max(handCore, handMaskAt(uv + o) * 0.92);
        }
    }
    float bgPlaneMask = 1.0 - smoothstep(0.18, 0.72, handCore);
    if (bgPlaneMask <= 0.002) {
        finalColor = vec4(0.0);
        return;
    }

    vec2 p = uv / texel / scale;
    vec4 tr = texture(TrailSampler, uv);

    // Огненная обводка вокруг силуэта руки на втором плане
    float rimNear = 0.0;
    float rimMid = 0.0;
    vec3 rimItemCol = vec3(0.0);
    float rimWeight = 0.0;
    float rimWidth = (3.5 + FxInfo.w * 1.4) * scale;
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * 0.5235988;
        vec2 dir = vec2(cos(ang), sin(ang));

        vec2 c1 = uv + dir * (rimWidth * 0.45) * texel;
        float m1 = handMaskAt(c1);
        rimNear = max(rimNear, m1);

        vec2 c2 = uv + dir * rimWidth * texel;
        float m2 = handMaskAt(c2);
        rimMid = max(rimMid, m2);

        if (m1 > 0.05) {
            rimItemCol += texture(AfterColor, c1).rgb * m1;
            rimWeight += m1;
        }
    }
    if (rimWeight > 1e-4) {
        rimItemCol /= rimWeight;
    }

    // Языки пламени: шум, бегущий вверх по контуру и шлейфу позади руки
    float f1 = fbm(p * vec2(0.022, 0.014) + vec2(0.0, -ViewInfo.z * (0.75 + FxInfo.y * 0.55)));
    float f2 = fbm(p * vec2(0.045, 0.030) + vec2(1.7, -ViewInfo.z * (1.1 + FxInfo.y * 0.75)));
    float flameNoise = clamp(f1 * 0.65 + f2 * 0.55, 0.0, 1.2);

    float contourRing = clamp(max(rimNear, rimMid * (0.55 + 0.65 * flameNoise)), 0.0, 1.0);
    float trailFlame = smoothstep(0.06, 0.95, tr.a * (0.45 + 1.15 * flameNoise));
    float outlineFlame = contourRing * (0.65 + 0.55 * flameNoise);
    float flame = clamp(max(trailFlame, outlineFlame), 0.0, 1.25);

    // Мягкое свечение (bloom) на втором плане вокруг огненной обводки
    float glow = 0.0;
    vec3 glowCol = vec3(0.0);
    float R = (5.5 + FxInfo.w * 5.5) * scale;
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * 0.5235988;
        float rr = R * (0.35 + 0.65 * fract(float(i) * 0.618034));
        vec4 s = texture(TrailSampler, uv + vec2(cos(ang), sin(ang)) * rr * texel);
        glow += s.a;
        glowCol += s.rgb * s.a;
    }
    glowCol /= max(glow, 1e-4);
    glow = clamp(glow / 12.0 + contourRing * 0.45, 0.0, 1.5);

    // Цветовой режим: 0 = Item, иначе = Client
    vec3 base;
    if (ModeInfo.z < 0.5) {
        vec3 c = rimWeight > 0.05 ? rimItemCol : (tr.a > 0.001 ? tr.rgb : glowCol);
        float mx = max(max(c.r, c.g), max(c.b, 1e-3));
        base = mix(vec3(1.0, 0.45, 0.1), c / mx, step(0.08, mx));
    } else {
        float wave = 0.5 + 0.5 * sin(ViewInfo.z * ModeInfo.y + uv.y * 2.2 - uv.x * 1.4);
        base = mix(ClientA.rgb, ClientB.rgb, wave);
    }

    // Яркая кромка прямо у границы руки + языки пламени позади
    float innerRim = clamp(rimNear * (0.75 + 0.45 * f2), 0.0, 1.0);
    vec3 hot = mix(base, vec3(1.0, 0.97, 0.92), clamp(smoothstep(0.55, 1.0, flame) * 0.55 + innerRim * 0.35, 0.0, 0.85));

    // Полностью на втором плане: рука спереди остаётся чистой (умножается на bgPlaneMask)
    vec3 outc = (hot * flame + base * innerRim * 0.65 + base * glow * 0.5) * FxInfo.z * bgPlaneMask;
    finalColor = vec4(outc, 0.0);
}
