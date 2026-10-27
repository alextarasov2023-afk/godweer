#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Glass: vertexColor.rgb = glass tint, vertexColor.a = opacity.
// The centre stays see-through, fresnel flares the edges, layered
// sine bands fake refraction and a sharp specular gives cut glints.
void main() {
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);

    float fres = pow(1.0 - ndv, 2.2);

    // Fake refraction shimmer that moves with the view.
    float shimmer = sin(viewPos.x * 8.0 + N.y * 5.0)
                  + sin(viewPos.y * 10.0 - N.x * 4.0)
                  + sin(viewPos.z * 6.0 + N.z * 3.0)
                  + sin((viewPos.y - viewPos.x) * 14.0);
    shimmer = shimmer * 0.125 + 0.5;

    // Specular glints from a fixed key light.
    vec3 L = normalize(vec3(0.45, 0.85, 0.4));
    vec3 H = normalize(L + V);
    float spec = pow(clamp(dot(N, H), 0.0, 1.0), 64.0);

    vec3 base = vertexColor.rgb;
    vec3 col = base * (0.45 + 0.55 * shimmer);
    col += base * fres * 1.4;
    col += vec3(1.0) * fres * fres * 0.75;
    col += spec * 1.5;

    float opacity = clamp(vertexColor.a, 0.0, 1.0);
    // Never pure glass in the middle of a surface: without this floor the body
    // fades out completely wherever the view hits it head-on, so entities and
    // the local player vanish while the camera turns. ChamsHook already floors
    // the opacity it sends, so the ghost pass stays weaker than the solid one.
    float body = opacity * (0.35 + 0.65 * fres);
    float alpha = max(body, opacity * 0.85)
                + fres * fres * 0.45
                + spec * 0.5;

    fragColor = vec4(clamp(col, 0.0, 1.5), clamp(alpha, 0.0, 1.0));
}
