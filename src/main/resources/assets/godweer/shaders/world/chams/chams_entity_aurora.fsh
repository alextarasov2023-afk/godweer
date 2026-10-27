#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Aurora: broad ribbons of light folding over the body, hue drifting along
// the flow. vertexColor.rgb = base colour, a = phase.
vec3 hueShift(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    float sa = sin(a);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    vec3 p = viewPos * 1.6;

    // Three ribbons at different scales and speeds, layered additively.
    float r1 = sin(p.y * 2.1 + p.x * 1.3 + phase);
    float r2 = sin(p.y * 3.7 - p.z * 1.9 + phase * 1.7);
    float r3 = sin((p.x + p.z) * 2.6 + phase * 0.6);
    float field = (r1 + r2 * 0.6 + r3 * 0.35) / 1.95;

    float band = smoothstep(-0.15, 0.85, field);
    float veil = smoothstep(0.35, 1.0, field * 0.5 + 0.5);

    vec3 cold = hueShift(base, -0.9 + 0.5 * sin(phase * 0.5));
    vec3 warm = hueShift(base, 1.1 + 0.4 * cos(phase * 0.7));
    vec3 col = mix(cold * 0.3, warm, band);
    col += cold * veil * 0.7;
    col *= 0.75 + 0.25 * sin(texCoord0.y * 24.0 - phase * 2.0);

    float rim = pow(1.0 - ndv, 1.8);
    col += mix(base, vec3(1.0), 0.4) * rim * 1.9;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum * 0.9 + rim * 0.25, 0.05)), 0.0, 1.0));
}
