#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Solar Winds: bright streams radiating across the dome with hot filaments.
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

    vec2 dome = dir.xz / (abs(up) + 0.32);
    float flow = fbm(vec3(dome * 1.3 + vec2(time * 0.04, -time * 0.02), time * 0.05));
    float streams = sin(dome.x * 3.5 + dome.y * 2.0 + flow * 7.0 + time * 0.45);
    streams = streams * 0.5 + 0.5;
    float glow = pow(streams, 5.0);

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.010, 0.010, 0.028), primary * 0.12 + vec3(0.012, 0.012, 0.036), grad);
    col = mix(col, mix(primary, secondary, 0.55), streams * 0.65 * effect);
    col += secondary * glow * 1.6 * effect;
    col += primary * pow(glow, 2.0) * 1.2 * effect;
    col += secondary * exp(-abs(up) * 5.0) * 0.4;

    float starVis = smoothstep(0.015, 0.22, abs(up)) * (1.0 - clamp(glow * 0.8, 0.0, 1.0));
    col += vec3(0.9, 0.93, 1.0) * starGlow(dir, 240.0, 0.045, time) * starVis * starsAmt;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
