#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Sunset Bloom: warm horizon band, low sun with glow, streaky clouds.
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

    vec3 sunDir = normalize(vec3(0.35 + 0.05 * sin(time * 0.05), 0.10, -0.85));
    float s = max(dot(dir, sunDir), 0.0);

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.030, 0.020, 0.060), primary * 0.15 + vec3(0.030, 0.020, 0.050), grad);

    float band = exp(-abs(up - 0.02) * 4.5);
    col = mix(col, mix(primary, secondary, 0.65), band * 0.85);

    float disc = smoothstep(0.99925, 0.99965, s);
    float glow = pow(s, 24.0) * 0.9 + pow(s, 350.0) * 1.6;
    vec3 sunCol = secondary * 1.2 + vec3(1.0, 0.9, 0.75);
    col += sunCol * (disc * 2.0 + glow) * effect;

    vec2 sk = dir.xz / (abs(up) + 0.4);
    float cl = fbm(vec3(sk * vec2(1.0, 3.2) + vec2(time * 0.03, 0.0), 2.3));
    float clouds = smoothstep(0.45, 0.8, cl) * smoothstep(-0.05, 0.2, up) * (1.0 - smoothstep(0.35, 0.8, up));
    col = mix(col, mix(primary, vec3(1.0, 0.75, 0.6), 0.4) + secondary * 0.3, clouds * 0.55 * effect);

    col += vec3(0.9, 0.93, 1.0) * starGlow(dir, 230.0, 0.04, time) * smoothstep(0.3, 0.7, abs(up)) * starsAmt;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
