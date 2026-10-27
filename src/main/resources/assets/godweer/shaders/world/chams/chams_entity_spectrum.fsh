#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Spectrum: a saturated rainbow running along the body and cycling with the
// phase; base colour only sets where the band starts. vertexColor.rgb = base,
// a = phase.
vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    // Hue runs down the body and scrolls with time; the UV adds a twist so
    // the bands wrap around limbs instead of sitting flat.
    float hue = viewPos.y * 0.22 + texCoord0.x * 0.35 + phase * 0.16;
    float sat = 0.85 - 0.15 * ndv;
    vec3 rainbow = hsv2rgb(vec3(fract(hue), sat, 1.0));

    // Energy pulses travelling along the same axis.
    float pulse = sin(texCoord0.y * 60.0 - phase * 3.0) * 0.5 + 0.5;
    vec3 col = mix(rainbow, base, 0.18);
    col *= 0.72 + 0.42 * pulse * pulse;
    col += rainbow * pow(pulse, 6.0) * 0.8;

    float rim = pow(1.0 - ndv, 2.0);
    col += vec3(1.0) * rim * 0.9;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum * 0.92 + rim * 0.2, 0.05)), 0.0, 1.0));
}
