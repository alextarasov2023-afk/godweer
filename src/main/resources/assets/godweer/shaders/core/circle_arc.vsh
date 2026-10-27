#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec2 localPos;
out vec4 arcColor;
flat out float outerRadius;
flat out float thickness;
flat out float startAngleRad;
flat out float sweepAngleRad;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);

    localPos = UV0;
    arcColor = Color;

    outerRadius = float(UV1.x) / 100.0;
    thickness = float(UV1.y) / 100.0;
    startAngleRad = radians(float(UV2.x) / 10.0);
    sweepAngleRad = radians(float(UV2.y) / 10.0);
}