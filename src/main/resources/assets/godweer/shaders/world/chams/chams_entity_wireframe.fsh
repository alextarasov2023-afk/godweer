#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Wireframe: a clear shell with a sparse glowing mesh. Lines are one pixel wide
// (fwidth antialiasing) and spaced four skin texels apart, so the mesh reads as
// thin wires instead of a grid of cells. vertexColor.rgb = base colour, a = phase.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fres = 1.0 - ndv;

    vec2 meshUv = texCoord0 * 16.0;
    vec2 cell = fract(meshUv);
    vec2 dist = min(cell, 1.0 - cell);
    vec2 width = fwidth(meshUv) * 1.2 + vec2(1e-5);
    vec2 line = 1.0 - smoothstep(vec2(0.0), width, dist);
    float wire = max(line.x, line.y);
    float node = line.x * line.y;

    // Clear interior, thin wires, brighter nodes where they cross, hot rim.
    vec3 col = base * (0.04 + 0.10 * fres);
    col += base * wire * 1.35;
    col += mix(base, vec3(0.92, 0.97, 1.0), 0.6) * node * 1.4;

    float rim = pow(fres, 2.6);
    col += mix(base, vec3(1.0), 0.6) * rim * 2.2;

    float lum = clamp(dot(col, vec3(0.299, 0.587, 0.114)), 0.0, 1.5);
    float alpha = clamp(0.13 + wire * 0.5 + node * 0.2 + rim * 0.55 + lum * 0.15, 0.0, 1.0);
    alpha *= 0.94 + 0.06 * sin(phase);

    fragColor = vec4(clamp(col, 0.0, 2.0), alpha);
}
