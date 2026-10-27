#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

void main() {
    if (!skyNearby(DepthSampler, uv)) {
        finalColor = vec4(0.0);
        return;
    }
    vec3 direction = skyDirection(uv);
    float time = SkyParams.x * SkyParams.z;
    vec3 bandNormal = normalize(vec3(0.30, 0.62, 0.72));
    float band = exp(-pow(dot(direction, bandNormal) * 2.2, 2.0));
    float haze = fbm(direction * 3.0 + vec3(time * 0.01, 0.0, 0.0));
    vec3 cloud = vec3(0.006, 0.008, 0.016)
            + mix(PrimaryColor.rgb, SecondaryColor.rgb, 0.5) * band * (0.10 + 0.28 * haze);
    finalColor = vec4(cloud, band);
}
