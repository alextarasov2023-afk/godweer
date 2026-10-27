#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Lava: the crust tears open along a slowly drifting noise field and the
// melt glows through the cracks. vertexColor.rgb = base colour, a = phase.
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

void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    // Domain warp: the field itself is pushed around by a second field, so
    // the cracks never look like a static grid.
    vec3 p = viewPos * 2.6;
    vec3 warp = vec3(vnoise(p + vec3(0.0, phase * 0.35, 0.0)),
                     vnoise(p * 1.7 + vec3(phase * 0.21, 0.0, 0.0)),
                     vnoise(p * 0.8 - vec3(0.0, 0.0, phase * 0.17)));
    float n = vnoise(p + warp * 1.9 + vec3(0.0, -phase * 0.25, 0.0));

    // Cracks: narrow bright ridges where the field sits at its mid value.
    float ridge = 1.0 - abs(n * 2.0 - 1.0);
    float crack = smoothstep(0.72, 0.99, ridge);
    float crust = 1.0 - crack * 0.86;

    vec3 melt = mix(base, vec3(1.0, 0.85, 0.45), crack * 0.9);
    vec3 col = melt * (0.22 + 0.78 * crust);
    col += melt * crack * crack * 2.1;

    // Embers crawling up the body.
    float ember = vnoise(vec3(p.x * 4.0, p.y * 4.0 - phase * 1.6, p.z * 4.0));
    col += base * smoothstep(0.82, 1.0, ember) * 0.9;

    float rim = pow(1.0 - ndv, 2.2);
    col += mix(base, vec3(1.0, 0.7, 0.3), 0.5) * rim * 1.7;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum * 0.95, 0.05)), 0.0, 1.0));
}
