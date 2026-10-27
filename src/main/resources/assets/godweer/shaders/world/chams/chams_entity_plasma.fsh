#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Plasma: vertexColor.rgb = pulsed base colour, vertexColor.a = phase
// (0..1). A classic flowing interference field over the body, hot cores
// where the waves line up, plus a fresnel rim. Alpha follows luminance
// so the ghost pass stays dimmer than the solid pass.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    vec3 p = viewPos * 2.2;
    float v = sin(p.x + phase)
            + sin(p.y * 0.9 - phase * 1.31)
            + sin(p.z * 1.1 + phase * 0.73)
            + sin((p.x + p.y - p.z) * 0.8 - phase * 1.17);
    v *= 0.25;
    float t = clamp(v * 0.5 + 0.5, 0.0, 1.0);

    vec3 hot = mix(base, vec3(1.0), 0.7);
    vec3 col = mix(base * 0.35, hot, pow(t, 1.5));
    col += base * 0.35 * (0.5 + 0.5 * sin(phase + t * 6.28318));

    float rim = pow(1.0 - ndv, 2.3);
    col += mix(base, vec3(1.0), 0.45) * rim * 1.8;

    float lum = max(col.r, max(col.g, col.b));
    float alpha = clamp(sqrt(max(lum * 0.9 + t * 0.2, 0.05)), 0.0, 1.0);
    fragColor = vec4(clamp(col, 0.0, 2.0), alpha);
}
