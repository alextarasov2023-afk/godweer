#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// Glass: clear crystal body. The silhouette lights up, a prism splits the rim
// into a chromatic fringe (red first, blue last) and two key lights throw cut
// glints. vertexColor.rgb = tint, a = opacity.
void main() {
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fres = 1.0 - ndv;

    // Prism dispersion: three rim powers, one per channel.
    vec3 fringe = vec3(pow(fres, 2.0), pow(fres, 2.35), pow(fres, 2.75));

    // Fake refraction: layered sine bands crawling over the surface.
    float shimmer = sin(viewPos.x * 7.0 + N.y * 6.0 + viewPos.z * 2.0)
                  + sin(viewPos.y * 9.0 - N.x * 5.0)
                  + sin((viewPos.y - viewPos.z) * 13.0 + N.z * 3.0);
    shimmer = shimmer * 0.1666 + 0.5;

    // Two sharp key lights for cut glints.
    vec3 L1 = normalize(vec3(0.45, 0.85, 0.4));
    vec3 L2 = normalize(vec3(-0.6, 0.35, 0.7));
    float spec = pow(clamp(dot(N, normalize(L1 + V)), 0.0, 1.0), 72.0)
               + pow(clamp(dot(N, normalize(L2 + V)), 0.0, 1.0), 48.0) * 0.7;

    vec3 base = vertexColor.rgb;
    vec3 col = base * (0.42 + 0.58 * shimmer);
    col += base * fringe * 1.25;
    col += fringe * vec3(1.0, 0.85, 1.0) * 0.55;
    col += vec3(0.85, 0.95, 1.0) * spec * 1.8;

    float opacity = clamp(vertexColor.a, 0.0, 1.0);
    // Never pure glass in the middle of a surface: without the floor the body
    // fades out wherever the view hits it head-on. ChamsHook already floors the
    // opacity it sends, so the ghost pass stays weaker than the solid one.
    float body = opacity * (0.30 + 0.70 * fres);
    float alpha = max(body, opacity * 0.85) + fres * fres * 0.35 + spec * 0.4;

    fragColor = vec4(clamp(col, 0.0, 1.6), clamp(alpha, 0.0, 1.0));
}
