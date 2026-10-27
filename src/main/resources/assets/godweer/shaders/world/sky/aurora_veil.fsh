#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Aurora Veil: night gradient + flowing aurora curtains + twinkling stars.
// SkyParams = (time, intensity, speed, zFlag); SkyTexel = (1/w, 1/h, aurora, stars).

float veilBands(vec2 p, float t) {
    float drift = fbm(vec3(p * 1.4 + vec2(t * 0.06, t * 0.016), t * 0.05));
    float rays = 0.55 + 0.45 * sin(p.x * 7.0 + drift * 8.0 + t * 0.4);
    return clamp(drift * 0.75 + drift * rays * 0.55, 0.0, 1.5);
}

void main() {
    if (texture(DepthSampler, uv).r > 0.000001) {
        discard;
    }
    vec3 dir = skyDirection(uv);
    float time = SkyParams.x * SkyParams.z;
    float intensity = SkyParams.y;
    float auroraAmt = SkyTexel.z;
    float starsAmt = SkyTexel.w;
    vec3 primary = PrimaryColor.rgb;
    vec3 secondary = SecondaryColor.rgb;
    float up = dir.y;

    // Deep night base with a vertical gradient.
    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.008, 0.011, 0.026), primary * 0.12 + vec3(0.012, 0.016, 0.038), grad);
    // Horizon glow in the secondary color.
    float horizon = exp(-abs(up) * 5.0);
    col += secondary * horizon * 0.5;
    // Zenith tint in the primary color.
    col = mix(col, primary * 0.30 + vec3(0.015, 0.022, 0.06), smoothstep(0.05, 0.9, up) * 0.65);

    // Aurora curtains across the sky dome (full sphere: mirrored below horizon via abs(up)).
    vec2 dome = dir.xz / (abs(up) + 0.28);
    float bands = veilBands(dome, time);
    float bd = (abs(up) - 0.30) * 3.0;
    float belt = exp(-bd * bd);
    float shape = smoothstep(0.42, 0.95, bands) * belt
        * smoothstep(0.06, 0.16, abs(up)) * (1.0 - smoothstep(0.42, 0.95, abs(up)));
    vec3 veilCol = mix(primary, secondary, clamp(bands * 1.1 - 0.15, 0.0, 1.0));
    col += veilCol * shape * 1.7 * auroraAmt;
    // Bright lower rim of the curtains.
    float rim = smoothstep(0.36, 0.55, bands) * (1.0 - smoothstep(0.55, 0.8, bands));
    col += secondary * rim * belt * smoothstep(0.06, 0.16, abs(up)) * 0.8 * auroraAmt;

    // Twinkling star layers, hidden near the horizon and inside bright curtains.
    float starVis = smoothstep(0.015, 0.22, abs(up)) * (1.0 - clamp(shape * 0.75, 0.0, 1.0));
    col += vec3(0.82, 0.89, 1.0) * starGlow(dir, 250.0, 0.05, time) * starVis * starsAmt;
    col += vec3(1.0, 0.94, 0.86) * starGlow(dir * 1.6 + 17.0, 140.0, 0.028, time * 1.25) * starVis * 1.3 * starsAmt;

    // Gentle darkening only right at the nadir so the shader covers the whole sky.
    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
