#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Rotate the colour around the grey axis for a cheap two-tone gradient.
vec3 hueShift(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    float sa = sin(a);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Glow: vertexColor.rgb = CPU-pulsed base colour, vertexColor.a = scroll
// phase (0..1). Scrolling energy bands over the skin UVs, a flowing
// two-tone gradient along the body and a heavy fresnel halo that reads
// as a shader outline around the silhouette. Alpha follows luminance so
// the depth-ignoring ghost pass stays dimmer than the solid pass.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    // Scrolling diagonal energy bands.
    float bands = sin((texCoord0.x * 1.35 + texCoord0.y) * 30.0 - phase * 2.0);
    bands = bands * 0.5 + 0.5;
    float core = smoothstep(0.35, 1.0, bands);

    // Two-tone gradient flowing along the body height.
    float grad = 0.5 + 0.5 * sin(viewPos.y * 2.5 + phase);
    vec3 second = hueShift(base, 0.55 + 0.35 * sin(phase * 0.5));
    vec3 col = mix(base, second, grad * 0.6);

    col *= 0.45 + 0.55 * core;
    col += col * core * core * 1.3;

    // Glow outline: bright fresnel halo at the silhouette.
    float rim = pow(1.0 - ndv, 2.4);
    col += mix(base, vec3(1.0), 0.4) * rim * 2.4;

    float lum = max(col.r, max(col.g, col.b));
    // sqrt keeps the pattern but lifts the dark troughs: raw luminance made the
    // model fade to nothing on surfaces facing the camera.
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum, 0.05)), 0.0, 1.0));
}
