#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Hologram: thin scanlines over the skin, one bright sweep band climbing the
// body, edge charge and a light flicker. vertexColor.rgb = base colour,
// a = phase (one full cycle).
float hash21(vec2 p) {
    p = fract(p * vec2(233.34, 851.73));
    p += dot(p, p + 23.45);
    return fract(p.x * p.y);
}

void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    // Scanlines ride the model UVs so they wrap the limbs instead of the screen.
    float lines = sin(texCoord0.y * 420.0 + phase * 5.0);
    lines = 0.62 + 0.38 * (lines * 0.5 + 0.5);

    // Sweep: a band that climbs the body and fades its own tail. Squared
    // distance instead of pow: pow с отрицательным основанием в GLSL не определён.
    float y = viewPos.y * 0.6 - phase * 0.16;
    float band = fract(y);
    float tail = (band - 0.5) * 5.5;
    float sweep = exp(-tail * tail);

    // Per-line sparkle, cheap and stable.
    float cell = floor(texCoord0.y * 420.0);
    float flick = 0.82 + 0.18 * hash21(vec2(cell, floor(phase * 6.0)));

    vec3 col = base * lines * flick;
    col += mix(base, vec3(1.0), 0.55) * sweep * 1.5;
    col += base * 0.25 * (0.5 + 0.5 * sin(phase * 2.0));

    // Charge concentrated at the silhouette.
    float rim = pow(1.0 - ndv, 1.6);
    col += mix(base, vec3(0.65, 0.9, 1.0), 0.6) * rim * 2.6;

    float lum = max(col.r, max(col.g, col.b));
    // Hologram stays translucent by design, but a flat floor keeps the body on
    // screen instead of dissolving between the scanlines.
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(0.35 + lum * 0.65 + rim * 0.3, 0.0, 1.0));
}
