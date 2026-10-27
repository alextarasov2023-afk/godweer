#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;
uniform sampler2D CloudSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

void main() {
    if (texture(DepthSampler, uv).r > 0.000001) {
        discard;
    }

    vec3 color = texture(CloudSampler, uv).rgb;

    color *= SkyParams.y;

    color = color / (1.0 + color * 0.50);
    color = pow(color, vec3(0.92)) + ditherRgb(gl_FragCoord.xy);

    finalColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}