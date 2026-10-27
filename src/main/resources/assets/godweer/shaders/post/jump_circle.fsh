#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

struct CircleData {
    vec4 originAndProgress;
    vec4 colorAndRadius;
    vec4 fxParams;
};

layout(std140) uniform WorldJumpCircleUniforms {
    mat4 InvProjMat;
    mat4 InvViewMat;
    vec4 CameraPos;
    vec4 ScreenParams;
    CircleData Circles[16];
};

vec3 reconstructWorldPos(vec2 texCoord, float depth) {
    float z = depth;
    float clipZ = (CameraPos.w > 0.5) ? z : z * 2.0 - 1.0;
    vec4 clipSpace = vec4(texCoord * 2.0 - 1.0, clipZ, 1.0);
    vec4 viewSpace = InvProjMat * clipSpace;
    viewSpace /= viewSpace.w;
    vec4 worldSpace = InvViewMat * viewSpace;
    return worldSpace.xyz + CameraPos.xyz;
}

void main() {
    float depth = texture(DepthSampler, uv).r;

    if (depth >= 1.0) {
        fragColor = texture(SceneSampler, uv);
        return;
    }

    vec3 worldPos = reconstructWorldPos(uv, depth);

    vec2 totalDistortion = vec2(0.0);
    vec3 totalGlow = vec3(0.0);

    int count = int(ScreenParams.w);

    for (int i = 0; i < count; i++) {
        CircleData circle = Circles[i];

        vec3 origin = circle.originAndProgress.xyz;
        float progress = circle.originAndProgress.w;
        vec3 circleColor = circle.colorAndRadius.rgb;
        float maxRadius = circle.colorAndRadius.w;

        float distortionPower = circle.fxParams.x;
        float glowPower = circle.fxParams.y;
        float ringWidth = max(circle.fxParams.z, 0.05);
        float heightFalloff = circle.fxParams.w;

        vec3 deltaWorld = worldPos - origin;
        float distXZ = length(deltaWorld.xz);
        float distY = abs(deltaWorld.y);

        float easedProgress = sin(progress * 1.5707963);
        float currentRadius = easedProgress * maxRadius;

        float d = distXZ - currentRadius;
        float absD = abs(d);

        float verticalMask = smoothstep(heightFalloff, 0.0, distY);

        float fadeIn = smoothstep(0.0, 0.06, progress);
        float fadeOut = pow(1.0 - progress, 1.2);
        float lifeMask = fadeIn * fadeOut * verticalMask;

        if (lifeMask > 0.0005) {
            float coreLineWidth = ringWidth * 0.09;
            float coreLine = exp(-pow(d / coreLineWidth, 2.0));

            float auraNear = exp(-absD / (ringWidth * 0.35)) * 1.3;

            float auraMid  = exp(-absD / (ringWidth * 0.90)) * 0.65;

            float auraFar  = exp(-absD / (ringWidth * 2.20)) * 0.25;

            float fullAura = auraNear + auraMid + auraFar;

            vec3 coreColor = mix(circleColor, vec3(1.0), 0.7);
            vec3 neonLight = (circleColor * fullAura + coreColor * (coreLine * 2.8)) * glowPower;
            totalGlow += neonLight * lifeMask;

            if (distortionPower > 0.01) {
                vec2 dirXZ = (distXZ > 0.0001) ? (deltaWorld.xz / distXZ) : vec2(0.0, 1.0);

                float waveFactor = (d / ringWidth) * exp(-pow(d / (ringWidth * 0.65), 2.0));

                vec2 screenDir = vec2(dirXZ.x, dirXZ.y * (ScreenParams.y / ScreenParams.x));

                vec2 distortOffset = screenDir * (-waveFactor) * (distortionPower * 0.045) * lifeMask;
                totalDistortion += distortOffset;
            }
        }
    }

    vec2 finalUV = uv + totalDistortion;

    vec3 sceneColor;
    float distMag = length(totalDistortion);
    if (distMag > 0.0001) {
        float ca = distMag * 0.45;
        sceneColor.r = texture(SceneSampler, finalUV + totalDistortion * ca).r;
        sceneColor.g = texture(SceneSampler, finalUV).g;
        sceneColor.b = texture(SceneSampler, finalUV - totalDistortion * ca).b;
    } else {
        sceneColor = texture(SceneSampler, uv).rgb;
    }

    fragColor = vec4(sceneColor + totalGlow, 1.0);
}