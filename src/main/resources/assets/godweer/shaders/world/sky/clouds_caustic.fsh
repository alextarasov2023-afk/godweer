#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

#define MAX_ITER 5

float smoothWaterCaustic(vec2 p, float t) {
    vec2 i = p;
    float c = 1.0;
    float inten = 0.06;

    for (int n = 0; n < MAX_ITER; n++) {
        float tShift = t * (1.0 - (3.2 / float(n + 1)));
        i = p + vec2(
            cos(tShift - i.x) + sin(tShift + i.y * 1.25),
            sin(tShift - i.y) + cos(tShift + i.x * 1.25)
        );
        c += 1.0 / length(vec2(
            p.x / (sin(i.x + tShift) / inten),
            p.y / (cos(i.y + tShift) / inten)
        ));
    }

    c /= float(MAX_ITER);
    c = 1.5 - sqrt(max(c, 0.0));
    return pow(max(c, 0.0), 3.0);
}

void main() {
    if (!skyNearby(DepthSampler, uv)) {
        finalColor = vec4(0.0);
        return;
    }

    vec3 direction = skyDirection(uv);
    float time = SkyParams.x * SkyParams.z * 0.35;

    float horizonFade = smoothstep(-0.06, 0.38, direction.y);

    vec2 skyPlaneUV = (direction.xz / max(direction.y + 0.10, 0.025)) * 2.6;

    float caustic1 = smoothWaterCaustic(skyPlaneUV, time);
    float caustic2 = smoothWaterCaustic(skyPlaneUV * 1.6 + vec2(time * 0.15, -time * 0.12), time * 1.15) * 0.4;

    float brightness = (caustic1 + caustic2) * horizonFade;

    vec3 deepBase = PrimaryColor.rgb * 0.22;
    vec3 waveGlow = mix(PrimaryColor.rgb, SecondaryColor.rgb, clamp(brightness * 0.8, 0.0, 1.0));
    vec3 highlight = mix(SecondaryColor.rgb, vec3(1.0), 0.35) * 1.8;

    vec3 skyColor = deepBase;
    skyColor += waveGlow * brightness * 1.4;
    skyColor += highlight * pow(brightness, 2.5) * 1.2;

    finalColor = vec4(skyColor * horizonFade, 0.0);
}