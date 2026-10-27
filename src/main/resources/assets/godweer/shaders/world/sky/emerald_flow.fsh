#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Emerald Flow: glowing green plasma ribbons drifting across the dome.
// SkyParams = (time, intensity, speed, zFlag); SkyTexel = (1/w, 1/h, effect, stars).

void main() {
    if (texture(DepthSampler, uv).r > 0.000001) {
        discard;
    }
    vec3 dir = skyDirection(uv);
    float time = SkyParams.x * SkyParams.z;
    float intensity = SkyParams.y;
    float effect = SkyTexel.z;
    float starsAmt = SkyTexel.w;
    vec3 primary = PrimaryColor.rgb;
    vec3 secondary = SecondaryColor.rgb;
    float up = dir.y;

    vec2 dome = dir.xz / (abs(up) + 0.3);
    float f = fbm(vec3(dome * 1.8 + vec2(time * 0.04, -time * 0.02), time * 0.06));
    float rib = 1.0 - abs(f * 2.0 - 1.0);
    rib = pow(rib, 3.0);
    float flow2 = 0.5 + 0.5 * sin(dome.x * 5.0 + f * 9.0 + time * 0.5);

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.006, 0.016, 0.012), primary * 0.10 + vec3(0.006, 0.018, 0.014), grad);
    col += primary * rib * (0.4 + 0.6 * flow2) * 1.4 * effect;
    col += secondary * pow(rib, 8.0) * 1.5 * effect;
    col += secondary * exp(-abs(up) * 5.0) * 0.35;

    float starVis = smoothstep(0.015, 0.22, abs(up)) * (1.0 - clamp(rib * 0.6, 0.0, 1.0));
    col += vec3(0.85, 0.95, 0.9) * starGlow(dir, 240.0, 0.045, time) * starVis * starsAmt;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
