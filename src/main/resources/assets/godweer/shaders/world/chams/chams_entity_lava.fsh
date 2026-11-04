#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash13(i + vec3(0.0, 0.0, 0.0)), hash13(i + vec3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

// Lava: a dark crust torn by a warped ridge field. Cracks go white-hot at their
// cores, embers crawl upward and the silhouette stays molten.
// vertexColor.rgb = base colour, a = phase.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    // Domain warp: the crack field is pushed around by a second field, so the
    // pattern never reads as a static grid.
    vec3 p = viewPos * 2.5;
    vec3 warp = vec3(vnoise(p + vec3(0.0, phase * 0.35, 0.0)),
                     vnoise(p * 1.7 + vec3(phase * 0.21, 0.0, 0.0)),
                     vnoise(p * 0.8 - vec3(0.0, 0.0, phase * 0.17)));
    float n = vnoise(p + warp * 2.0 + vec3(0.0, -phase * 0.25, 0.0));

    float ridge = 1.0 - abs(n * 2.0 - 1.0);
    float crack = smoothstep(0.74, 0.99, ridge);
    float crust = 1.0 - crack * 0.9;

    vec3 melt = mix(base, vec3(1.0, 0.78, 0.38), crack * 0.92);
    vec3 col = melt * (0.16 + 0.84 * crust);
    col += melt * crack * crack * 2.0;
    col += vec3(1.0, 0.95, 0.8) * pow(crack, 5.0) * 1.1;

    // Embers crawling up the body.
    float ember = vnoise(vec3(p.x * 4.0, p.y * 4.0 - phase * 1.6, p.z * 4.0));
    col += mix(base, vec3(1.0, 0.6, 0.2), 0.5) * smoothstep(0.84, 1.0, ember) * 1.1;

    float rim = pow(1.0 - ndv, 2.2);
    col += mix(base, vec3(1.0, 0.72, 0.3), 0.55) * rim * 1.8;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum * 0.95, 0.05)), 0.0, 1.0));
}
