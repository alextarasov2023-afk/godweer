#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D DepthSampler;

layout(std140) uniform VolumetricFogUniforms {
    mat4 uProj;
    mat4 uInvProj;
    mat4 uView;
    mat4 uInvView;
    vec4 uCameraPos;
    vec4 uFogColor;
    vec4 uFogParams;
    vec4 uScreen;
    vec4 uLightning;
    vec4 uLightningPos;
};

void main() {
    float depth = texture(DepthSampler, uv).r;
    bool isSky = (depth >= 0.99999);

    float zNdc = (uScreen.z > 0.5) ? depth : (depth * 2.0 - 1.0);
    vec4 clipPos = vec4(uv * 2.0 - 1.0, zNdc, 1.0);
    vec4 viewPos = uInvProj * clipPos;
    viewPos /= viewPos.w;

    vec3 worldRel = (uInvView * vec4(viewPos.xyz, 0.0)).xyz;
    vec3 hitWorldPos = uCameraPos.xyz + worldRel;

    vec3 ray = hitWorldPos - uCameraPos.xyz;
    float totalDist = length(ray);

    if (totalDist <= 0.1) {
        discard;
    }
    vec3 dir = ray / totalDist;

    float maxDist = isSky ? 220.0 : totalDist;
    float clearRadius = mix(22.0, 0.0, clamp(uFogParams.z, 0.0, 1.0));
    float t0 = max(0.0, clearRadius);
    float t1 = maxDist;

    if (t0 >= t1) {
        discard;
    }

    float halfHeight = max(uFogParams.w * 0.5, 1.0);
    float centerY = uCameraPos.y;
    float time = uFogParams.x * 0.35;
    float dt = (t1 - t0) * 0.25;

    float totalDensity = 0.0;
    vec3 accumulatedColor = vec3(0.0);

    float flashIntensity = uLightning.x;
    vec3 lightningColor = uLightning.yzw;

    vec3 strikePos = uLightningPos.xyz;
    float strikeRadius = (uLightningPos.w > 0.0) ? uLightningPos.w : 140.0;

    for (int i = 0; i < 4; i++) {
        float s = t0 + (float(i) + 0.5) * dt;
        vec3 p = uCameraPos.xyz + dir * s;

        float hRel = clamp(abs(p.y - centerY) / halfHeight, 0.0, 1.0);
        float hFactor = 1.0 - hRel * hRel * (3.0 - 2.0 * hRel);

        float wave1 = sin(p.x * 0.025 + time * 0.5) * cos(p.z * 0.025 + time * 0.4);
        float wave2 = sin(p.x * 0.055 - time * 0.3 + p.y * 0.04) * cos(p.z * 0.055 + time * 0.35);
        float mistWaves = 0.82 + 0.18 * (wave1 + wave2 * 0.5);

        float stepDensity = hFactor * mistWaves * dt;

        vec3 stepColor = uFogColor.rgb;
        stepColor += stepColor * (0.12 * smoothstep(10.0, 180.0, s));

        if (flashIntensity > 0.001) {
            float distXZ = length(p.xz - strikePos.xz);
            float dist3D = length(p - strikePos);

            float effectiveDist = mix(distXZ * 0.7, dist3D, 0.5);

            float falloff = smoothstep(strikeRadius, 0.0, effectiveDist);
            falloff = falloff * falloff;

            float localFlash = flashIntensity * falloff;

            stepColor = mix(stepColor, lightningColor * 1.6, clamp(localFlash * 0.75, 0.0, 1.0));
            stepColor += lightningColor * (localFlash * 1.1);
        }

        accumulatedColor += stepColor * stepDensity;
        totalDensity += stepDensity;
    }

    float finalDensity = totalDensity * (uFogParams.y * 0.038);
    float fogAlpha = clamp(1.0 - exp(-finalDensity), 0.0, 1.0);

    if (fogAlpha <= 0.001) {
        discard;
    }

    vec3 finalColor = (totalDensity > 0.0001) ? (accumulatedColor / totalDensity) : uFogColor.rgb;

    fragColor = vec4(finalColor, fogAlpha);
}