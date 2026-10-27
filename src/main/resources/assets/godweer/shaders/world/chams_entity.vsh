#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;

void main() {
    vec4 mvPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * mvPos;
    vertexColor = Color;
    texCoord0 = UV0;
    viewPos = mvPos.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
}
