#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D DepthSampler;

layout(std140) uniform LightningUniforms {
    mat4 uProj;
    mat4 uInvProj;
    mat4 uView;
    mat4 uInvView;
    vec4 uCameraPos;
    vec4 uLightningColor;
    vec4 uStrikePos;
    vec4 uStrikeParams;
};

vec2 getTrunkOffset(float y, float topY, float botY, float seed) {
    float t = clamp((topY - y) / max(topY - botY, 1.0), 0.0, 1.0);
    float env = sin(t * 3.14159265);

    float s = seed;
    vec2 offset = vec2(0.0);
    offset += vec2(sin(y * 0.075 + s * 1.3), cos(y * 0.068 + s * 1.7)) * 5.5;
    offset += vec2(sin(y * 0.180 + s * 3.1), cos(y * 0.165 + s * 2.8)) * 2.8;
    offset += vec2(sin(y * 0.480 + s * 7.4), cos(y * 0.420 + s * 6.2)) * 1.3;
    offset += vec2(sin(y * 1.250 + s * 15.2), cos(y * 1.150 + s * 14.1)) * 0.5;

    return offset * env;
}

vec2 getBranch1Offset(float y, float branchStartY, float topY, float botY, float seed) {
    if (y > branchStartY) return getTrunkOffset(y, topY, botY, seed);
    float dy = branchStartY - y;
    vec2 mainAtStart = getTrunkOffset(branchStartY, topY, botY, seed);

    vec2 branchDir = vec2(0.85, 0.52);
    vec2 wiggle = vec2(sin(y * 0.25 + seed * 5.0), cos(y * 0.22 + seed * 4.0)) * 1.6;
    return mainAtStart + branchDir * (dy * 0.45) + wiggle * smoothstep(0.0, 15.0, dy);
}

vec2 getBranch2Offset(float y, float branchStartY, float topY, float botY, float seed) {
    if (y > branchStartY) return getTrunkOffset(y, topY, botY, seed);
    float dy = branchStartY - y;
    vec2 mainAtStart = getTrunkOffset(branchStartY, topY, botY, seed);

    vec2 branchDir = vec2(-0.75, -0.65);
    vec2 wiggle = vec2(cos(y * 0.28 + seed * 8.0), sin(y * 0.24 + seed * 7.0)) * 1.4;
    return mainAtStart + branchDir * (dy * 0.40) + wiggle * smoothstep(0.0, 12.0, dy);
}

void main() {
    float intensity = uStrikeParams.y;
    if (intensity <= 0.001) {
        discard;
    }

    float depth = texture(DepthSampler, uv).r;
    bool isSky = (depth >= 0.99999);

    float zNdc = (uStrikeParams.w > 0.5) ? depth : (depth * 2.0 - 1.0);
    vec4 clipPos = vec4(uv * 2.0 - 1.0, zNdc, 1.0);
    vec4 viewPos = uInvProj * clipPos;
    viewPos /= viewPos.w;

    vec3 worldRel = (uInvView * vec4(viewPos.xyz, 0.0)).xyz;
    vec3 hitWorldPos = uCameraPos.xyz + worldRel;

    vec3 rayDir = hitWorldPos - uCameraPos.xyz;
    float sceneDist = length(rayDir);
    if (sceneDist <= 0.05) discard;
    rayDir /= sceneDist;

    float realSceneDist = isSky ? 650.0 : sceneDist;

    vec2 strikeXZ = uStrikePos.xy;
    float topY = uStrikePos.z;
    float botY = uStrikePos.w;
    float seed = uStrikeParams.z;
    float age = uStrikeParams.x;

    vec2 ro2 = uCameraPos.xz - strikeXZ;
    vec2 rd2 = rayDir.xz;
    float radius = 26.0;

    float A = dot(rd2, rd2);
    float B = 2.0 * dot(ro2, rd2);
    float C = dot(ro2, ro2) - radius * radius;
    float disc = B * B - 4.0 * A * C;

    if (disc < 0.0) {
        float distToCloud = length(hitWorldPos - vec3(strikeXZ.x, topY, strikeXZ.y));
        float cloudGlow = exp(-distToCloud * 0.015) * intensity * pow(1.0 - age, 1.4) * 0.25;
        if (cloudGlow > 0.002) {
            fragColor = vec4(uLightningColor.rgb * cloudGlow, cloudGlow * 0.6);
            return;
        }
        discard;
    }

    float tNear = max(0.5, (-B - sqrt(disc)) / (2.0 * A));
    float tFar = min(realSceneDist, (-B + sqrt(disc)) / (2.0 * A));

    if (tNear >= tFar) {
        discard;
    }

    const int STEPS = 28;
    float stepSize = (tFar - tNear) / float(STEPS);

    float coreSum = 0.0;
    float glowSum = 0.0;

    float b1Start = mix(topY, botY, 0.32);
    float b2Start = mix(topY, botY, 0.52);

    for (int i = 0; i < STEPS; i++) {
        float t = tNear + (float(i) + 0.5) * stepSize;
        vec3 p = uCameraPos.xyz + rayDir * t;

        if (p.y >= botY - 5.0 && p.y <= topY + 5.0) {
            vec2 trunkPos = strikeXZ + getTrunkOffset(p.y, topY, botY, seed);
            float dTrunk = length(p.xz - trunkPos);

            coreSum += exp(-(dTrunk * dTrunk) / 0.14) * stepSize;
            glowSum += exp(-dTrunk / 2.6) * stepSize;

            if (p.y < b1Start && p.y > botY + 10.0) {
                vec2 b1Pos = strikeXZ + getBranch1Offset(p.y, b1Start, topY, botY, seed);
                float dB1 = length(p.xz - b1Pos);
                coreSum += exp(-(dB1 * dB1) / 0.10) * 0.55 * stepSize;
                glowSum += exp(-dB1 / 1.9) * 0.45 * stepSize;
            }

            if (p.y < b2Start && p.y > botY + 4.0) {
                vec2 b2Pos = strikeXZ + getBranch2Offset(p.y, b2Start, topY, botY, seed);
                float dB2 = length(p.xz - b2Pos);
                coreSum += exp(-(dB2 * dB2) / 0.08) * 0.45 * stepSize;
                glowSum += exp(-dB2 / 1.7) * 0.35 * stepSize;
            }
        }
    }

    float coreFade = pow(clamp(1.0 - age * 1.25, 0.0, 1.0), 3.0);
    float glowFade = pow(clamp(1.0 - age, 0.0, 1.0), 1.35);

    float finalCore = coreSum * intensity * coreFade * 2.8;
    float finalGlow = glowSum * intensity * glowFade * 0.32;

    float distToCloud = length(hitWorldPos - vec3(strikeXZ.x, topY, strikeXZ.y));
    float cloudAura = exp(-distToCloud * 0.015) * intensity * glowFade * 0.28;

    float totalAlpha = clamp(finalCore * 1.6 + finalGlow + cloudAura, 0.0, 1.0);
    if (totalAlpha <= 0.001) {
        discard;
    }

    vec3 tint = uLightningColor.rgb;
    vec3 finalColor = vec3(1.0) * finalCore * 2.6 + tint * (finalGlow * 1.9 + cloudAura);

    fragColor = vec4(finalColor, totalAlpha);
}