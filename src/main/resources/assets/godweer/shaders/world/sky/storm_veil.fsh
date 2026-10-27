#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Storm Veil: rolling dark clouds with occasional electric flashes.
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
    float n = fbm(vec3(sk * 1.5 + vec2(time * 0.05, time * 0.02), time * 0.04));
    float n2 = fbm(vec3(sk * 3.4 - vec2(time * 0.03, 0.0), 8.1));
    float clouds = smoothstep(0.25, 0.8, n * 0.7 + n2 * 0.4);

    float fl = max(0.0, sin(time * 0.9) + sin(time * 0.53 + 1.7) - 1.15);
    float flash = smoothstep(0.3, 0.9, fl);

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.008, 0.008, 0.020), primary * 0.10 + vec3(0.010, 0.010, 0.030), grad);
    col = mix(col, mix(primary, secondary, 0.35) * 0.7, clouds * 0.8);
    col += (secondary + vec3(0.5, 0.55, 0.7)) * flash * (0.25 + clouds) * effect;
    col += primary * pow(max(n - 0.5, 0.0) * 2.0, 2.0) * 0.4;

    float starVis = smoothstep(0.2, 0.6, abs(up)) * (1.0 - clouds * 0.8);
    col += vec3(0.85, 0.89, 1.0) * starGlow(dir, 260.0, 0.04, time) * starVis * starsAmt * 0.8;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
