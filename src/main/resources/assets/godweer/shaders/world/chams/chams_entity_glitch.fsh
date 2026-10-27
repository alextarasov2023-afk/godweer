#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Glitch: the body is cut into cells that jump sideways, split into RGB and
// drop to black bars. Time is quantised into coarse steps, so the pattern
// re-shuffles instead of drifting. vertexColor.rgb = base colour, a = phase.
float hash12(float p) {
    vec3 p3 = fract(vec3(p) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    float jump = floor(phase * 5.0);

    vec2 cell = floor(vec2(texCoord0.x * 26.0, viewPos.y * 13.0));
    float r = hash13(vec3(cell, jump));

    // Horizontal tear: whole bands of the model shift sideways for one step.
    float bandJump = (hash12(cell.y * 7.0 + jump) - 0.5) * 0.7;
    float shifted = texCoord0.x + bandJump;
    float bars = step(fract(shifted * 0.5 + r * 0.4), 0.62);

    // RGB split: the channels sample the same cell with opposite offsets.
    float split = (r - 0.5) * 0.9;
    vec3 tint = base * (0.55 + 0.45 * r);
    vec3 col = vec3(
            tint.r * (0.6 + 0.4 * step(fract(texCoord0.x * 33.0 + split), 0.5)),
            tint.g * 0.95,
            tint.b * (0.6 + 0.4 * step(fract(texCoord0.x * 33.0 - split), 0.5)));

    col *= 0.55 + 0.45 * bars;
    col += base * 0.5 * step(fract(texCoord0.y * 9.0 - jump * 0.31), 0.14);

    float rim = pow(1.0 - ndv, 2.0);
    col += mix(base, vec3(1.0), 0.5) * rim * 1.5;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(0.4 + lum * 0.6 + rim * 0.2, 0.0, 1.0));
}
