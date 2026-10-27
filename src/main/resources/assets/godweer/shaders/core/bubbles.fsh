#version 330 core

in vec2 uv;
in vec4 tint;
out vec4 finalColor;

// Realistic soap-bubble look:
//  - Fresnel rim (view-angle dependent opacity)
//  - Thin-film iridescent interference swirls
//  - Specular highlight spot
//  - Soft alpha falloff toward the edge interior

layout(std140) uniform BubbleUniforms {
    vec4 params; // x = time, y = wobble, z = brightness, w = iridescence
};

in vec3 viewNormal;
in vec3 viewPos;

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float ndv = clamp(dot(n, v), 0.0, 1.0);

    // Fresnel: edge-on the film becomes mirror-like and opaque.
    float fresnel = pow(1.0 - ndv, 2.6);

    // Wobble the normal slightly for a liquid feel.
    float wob = sin(uv.x * 9.0 + params.x * 2.1) * cos(uv.y * 7.0 - params.x * 1.7);
    n = normalize(n + vec3(wob * params.y * 0.35, wob * params.y * 0.28, 0.0));
    ndv = clamp(dot(n, v), 0.0, 1.0);
    fresnel = max(fresnel, pow(1.0 - ndv, 2.6) * 0.85);

    // Thin-film interference: iridescent bands swirling across the surface.
    float film = uv.x * 6.2831 + uv.y * 3.1415 + params.x * 0.9 + wob * 2.0;
    vec3 iridescence = 0.5 + 0.5 * vec3(
        sin(film),
        sin(film + 2.094),
        sin(film + 4.188)
    );
    iridescence = mix(vec3(1.0), iridescence, params.w);

    // Specular highlight from an overhead light.
    vec3 lightDir = normalize(vec3(0.35, 0.85, 0.4));
    float spec = pow(max(dot(reflect(-lightDir, n), v), 0.0), 64.0);
    float specSoft = pow(max(dot(reflect(-lightDir, n), v), 0.0), 8.0) * 0.25;

    // Base body: mostly transparent, dense at the rim.
    float body = 0.06 + fresnel * 0.75;

    vec3 rimColor = mix(tint.rgb, iridescence * tint.rgb, 0.55);
    vec3 color = rimColor * (0.35 + fresnel) + iridescence * fresnel * 0.5;
    color += vec3(1.0) * (spec + specSoft);

    float alpha = clamp(body * tint.a, 0.0, 1.0);
    // Soft inner core so the bubble never looks like a flat disc.
    alpha *= smoothstep(0.0, 0.18, ndv) * 0.35 + 0.65;

    if (alpha <= 0.004) discard;
    finalColor = vec4(color * params.z, alpha);
}
