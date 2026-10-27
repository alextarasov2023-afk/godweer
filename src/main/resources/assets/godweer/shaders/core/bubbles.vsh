#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(std140) uniform Projection {
    mat4 ProjMat;
};

out vec2 uv;
out vec4 tint;
out vec3 viewNormal;
out vec3 viewPos;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    uv = UV0;
    tint = Color;

    // Billboard quad in view space: reconstruct the sphere-facing normal
    // from the quad UV so the fragment shader can do Fresnel shading.
    vec2 centered = UV0 * 2.0 - 1.0;
    float r2 = dot(centered, centered);
    vec3 localNormal = normalize(vec3(centered, sqrt(max(1.0 - r2, 0.0))));

    viewNormal = localNormal;
    viewPos = Position;
}
