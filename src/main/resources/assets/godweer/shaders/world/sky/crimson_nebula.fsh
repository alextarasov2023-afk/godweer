#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Crimson Nebula: dark red clouds with glowing cores and dark dust lanes.
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

    vec2 sk = dir.xz / (abs(up) + 0.35);
    float n1 = fbm(vec3(sk * 1.1 + vec2(time * 0.02, -time * 0.013), time * 0.03));
    float n2 = fbm(vec3(sk * 2.7 - vec2(time * 0.015, 0.0), 4.7));
    float neb = smoothstep(0.28, 0.85, n1 * 0.62 + n2 * 0.48);
    float lanes = 1.0 - smoothstep(0.45, 0.9, n2) * 0.45 * smoothstep(0.2, 0.6, neb);

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.012, 0.007, 0.018), primary * 0.10 + vec3(0.014, 0.009, 0.026), grad);
    col = mix(col, secondary * 0.9, neb * 0.75 * effect);
    col += primary * neb * neb * 1.3 * effect;
    col *= lanes;
    float ember = max(neb - 0.55, 0.0) * 2.2;
    col += secondary * (ember * ember) * 0.8 * effect;
    col += secondary * exp(-abs(up) * 5.0) * 0.35;

    float starVis = smoothstep(0.015, 0.22, abs(up)) * (1.0 - clamp(neb * 0.7, 0.0, 1.0));
    col += vec3(1.0, 0.9, 0.88) * starGlow(dir, 240.0, 0.05, time) * starVis * starsAmt;
    col += vec3(0.85, 0.9, 1.0) * starGlow(dir * 1.6 + 11.0, 150.0, 0.03, time * 1.2) * starVis * 1.2 * starsAmt;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
