#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Spectrum: holographic foil. The film thickness follows the viewing angle, so
// the body runs through the full spectrum as the camera turns — the same trick
// a soap bubble uses — with fine foil grain and a slow drift on top.
// vertexColor.rgb = base hue seed, a = phase.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fres = 1.0 - ndv;

    float thickness = fres * 2.4
                    + sin(phase * 0.35) * 0.25
                    + texCoord0.y * 0.55
                    + texCoord0.x * 0.2;

    // Thin-film interference: a cosine palette tuned to the visible spectrum.
    vec3 film = 0.5 + 0.5 * cos(6.28318 * (vec3(0.0, 0.33, 0.67) + thickness));
    film = pow(film, vec3(1.25));

    // The accent colour only seeds the tint, so every user still sees their own
    // colour while the rainbow stays readable.
    float seed = dot(base, vec3(0.2126, 0.7152, 0.0722));
    vec3 tint = mix(film, film * base * 1.45, 0.3 + 0.25 * seed);

    // Fine foil grain + angle-boosted edges.
    float grain = 0.04 * sin(texCoord0.x * 140.0 + texCoord0.y * 37.0);
    vec3 col = tint * (1.05 + grain);
    col += tint * pow(fres, 3.0) * 1.2;
    col += vec3(1.0) * pow(fres, 6.0) * 0.5;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 1.6), clamp(0.55 + lum * 0.45, 0.0, 1.0));
}
