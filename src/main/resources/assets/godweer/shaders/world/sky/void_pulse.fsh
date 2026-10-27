#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Void Pulse: concentric shockwave rings expanding from the zenith.
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

    vec2 sheet = dir.xz / (abs(up) + 0.6);
    float r = length(sheet);
    float wave = sin(r * 20.0 - time * 2.4);
    float rings = pow(0.5 + 0.5 * wave, 6.0);
    float front = exp(-r * 1.1);
    float wobble = fbm(vec3(sheet * 2.0, time * 0.1));

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.006, 0.007, 0.020), primary * 0.10 + vec3(0.008, 0.009, 0.028), grad);
    col = mix(col, mix(primary, secondary, 0.5), rings * front * 0.8 * effect);
    col += secondary * rings * rings * front * 1.4 * effect;
    col += primary * (0.5 + 0.5 * wobble) * 0.12;

    float starVis = smoothstep(0.015, 0.25, abs(up));
    col += vec3(0.88, 0.91, 1.0) * starGlow(dir, 250.0, 0.05, time) * starVis * starsAmt;
    col += vec3(1.0, 0.96, 0.9) * starGlow(dir * 1.6 + 23.0, 150.0, 0.03, time * 1.2) * starVis * 1.2 * starsAmt;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
