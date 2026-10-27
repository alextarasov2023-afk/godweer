#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <godweer:ui_common.glsl>

in vec2 localPos;
in vec4 arcColor;
flat in float outerRadius;
flat in float thickness;
flat in float startAngleRad;
flat in float sweepAngleRad;

out vec4 fragColor;

#define PI 3.14159265358979323846
#define TWO_PI 6.28318530717958647692

float normalizeAngle(float a) {
    a = mod(a, TWO_PI);
    return a < 0.0 ? a + TWO_PI : a;
}

void main() {
    vec4 col = arcColor * ColorModulator;
    float r = length(localPos);

    float halfThick = thickness * 0.5;
    float midRadius = outerRadius - halfThick;
    float dist = 0.0;

    if (sweepAngleRad >= TWO_PI - 0.001) {
        if (thickness >= outerRadius) {
            dist = r - outerRadius;
        } else {
            dist = abs(r - midRadius) - halfThick;
        }
    } else {
        float pAngle = normalizeAngle(atan(localPos.y, localPos.x));
        float sAngle = normalizeAngle(startAngleRad);
        float swAngle = clamp(sweepAngleRad, 0.0001, TWO_PI);

        float dAngle = normalizeAngle(pAngle - sAngle);

        if (dAngle <= swAngle) {
            dist = abs(r - midRadius) - halfThick;
        } else {
            vec2 startCap = midRadius * vec2(cos(sAngle), sin(sAngle));
            float endA = sAngle + swAngle;
            vec2 endCap = midRadius * vec2(cos(endA), sin(endA));

            float dStart = length(localPos - startCap) - halfThick;
            float dEnd = length(localPos - endCap) - halfThick;
            dist = min(dStart, dEnd);
        }
    }

    float delta = fwidth(dist);
    float aa = max(delta, 0.7);
    float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);

    if (alpha <= 0.001 || col.a * alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(col.rgb, col.a * alpha);
}