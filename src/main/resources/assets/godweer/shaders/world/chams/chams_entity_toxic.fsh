#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

// Cheap cellular field: nearest cell centre in a 3x3 neighbourhood.
float cells(vec2 uv, float t) {
    vec2 g = floor(uv);
    vec2 f = fract(uv);
    float nearest = 8.0;
    float second = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(float(x), float(y));
            vec2 r = hash22(g + o);
            vec2 p = o + 0.5 + 0.42 * sin(t + 6.28318 * r) - f;
            float d = length(p);
            second = min(second, max(nearest, d));
            nearest = min(nearest, d);
        }
    }
    return second - nearest;
}

// Toxic: glossy slime cells that swell, pop and climb the body, with bright
// membranes between the bubbles and a wet highlight sliding over them.
// vertexColor.rgb = base colour, a = phase.
void main() {
    float phase = vertexColor.a * 6.28318;
    vec3 base = vertexColor.rgb;

    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    // Bubbles rise: the field is scrolled down, so cells climb the body.
    vec2 uv = vec2(texCoord0.x * 13.0, texCoord0.y * 9.0 - viewPos.y * 2.0 - phase * 0.9);
    float wall = cells(uv, phase * 1.3);
    float skin = smoothstep(0.06, 0.5, wall);
    float edge = 1.0 - smoothstep(0.0, 0.14, wall);

    vec3 dark = base * 0.22;
    vec3 body = mix(dark, base * 1.1, skin);
    vec3 col = body;
    col += mix(base, vec3(1.0, 1.0, 0.6), 0.65) * edge * 2.0;

    // Wet highlight sliding over the membrane.
    float gloss = pow(0.5 + 0.5 * sin(uv.x * 2.3 + uv.y * 1.7 + phase * 2.0), 10.0);
    col += mix(vec3(1.0), base, 0.5) * gloss * 0.55;

    float rim = pow(1.0 - ndv, 2.1);
    col += mix(base, vec3(0.8, 1.0, 0.6), 0.5) * rim * 1.8;

    float lum = max(col.r, max(col.g, col.b));
    fragColor = vec4(clamp(col, 0.0, 2.0), clamp(sqrt(max(lum * 0.95 + edge * 0.2, 0.05)), 0.0, 1.0));
}
