#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Glitch: broken signal, not a mosaic. The body is cut into horizontal strips
// only — each strip shifts its colour balance in coarse time steps, thin tears
// run through the seams and the silhouette splits into RGB. Strips are
// continuous along their length, so the pattern can never turn into blocks.
// vertexColor.rgb = base colour, a = phase.
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

void main() {
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fres = 1.0 - ndv;

    float phase = vertexColor.a * 6.28318;
    // Time quantised into coarse steps: strips re-shuffle instead of drifting.
    float tick = floor(vertexColor.a * 7.0);

    float strip = viewPos.y * 15.0;
    float edge = fract(strip);
    float r = hash11(floor(strip) * 5.13 + tick * 91.7);

    // Only some strips tear; the rest keep the body readable.
    float torn = smoothstep(0.6, 0.9, r);
    float width = clamp(fwidth(strip) * 1.5, 0.05, 0.3);
    float seam = (1.0 - smoothstep(1.0 - width, 1.0, edge)) * torn;

    // RGB split grows on torn strips and towards the rim.
    float split = torn * 0.45 + fres * 0.25;
    vec3 col;
    col.r = base.r * (1.0 + split);
    col.g = base.g * (1.0 - split * 0.1);
    col.b = base.b * (1.0 - split * 0.6);
    col *= 0.55 + 0.45 * mix(1.0, r, torn);

    // Slow rolling sweep, wide enough to read as light instead of a bar.
    float sweep = 0.5 + 0.5 * sin(viewPos.y * 3.0 - phase * 1.4);
    col *= 0.84 + 0.16 * sweep;

    col += base * seam * 1.5;
    col += mix(base, vec3(1.0), 0.45) * pow(fres, 2.0) * 1.3;

    float lum = clamp(dot(col, vec3(0.299, 0.587, 0.114)), 0.0, 1.6);
    float alpha = clamp(0.34 + lum * 0.5 + pow(fres, 2.0) * 0.3, 0.0, 1.0);
    fragColor = vec4(clamp(col, 0.0, 2.0), alpha);
}
