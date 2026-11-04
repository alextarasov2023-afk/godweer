#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

vec3 hueShift(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    float sa = sin(a);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Plasma: a domain-warped interference field. Where the waves fold over each
// other the body lights up with complementary filaments, so the palette keeps
// sliding instead of sitting on one hue. vertexColor.rgb = base, a = phase.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    vec3 p = viewPos * 2.0;
    vec3 warp = vec3(sin(p.y * 1.3 + phase * 0.7),
                     sin(p.z * 1.1 - phase * 0.5),
                     sin(p.x * 1.4 + phase * 0.3)) * 0.65;
    vec3 q = p + warp;

    float v = sin(q.x + phase)
            + sin(q.y * 1.3 - phase * 1.2)
            + sin(q.z * 1.7 + phase * 0.8)
            + sin((q.x + q.y - q.z) * 0.9 - phase * 1.4);
    float t = clamp(v * 0.125 + 0.5, 0.0, 1.0);

    // Filaments: thin ridges wherever the field folds over itself.
    float ridge = 1.0 - abs(fract(v * 0.5 + phase * 0.1) * 2.0 - 1.0);
    float filament = pow(ridge, 6.0);

    vec3 cool = hueShift(base, 2.1);
    vec3 hot = mix(base, vec3(1.0), 0.75);
    vec3 col = mix(cool * 0.35, hot, pow(t, 1.6));
    col += mix(base, cool, 0.5) * filament * 1.6;
    col += hot * pow(t, 8.0) * 0.8;

    float rim = pow(1.0 - ndv, 2.2);
    col += mix(base, vec3(1.0), 0.5) * rim * 1.8;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum * 0.9 + t * 0.15, 0.05)), 0.0, 1.0));
}
