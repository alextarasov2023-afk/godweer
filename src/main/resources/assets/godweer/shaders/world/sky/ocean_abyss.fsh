#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D DepthSampler;

#moj_import <godweer:blocks_common.glsl>
#moj_import <godweer:world_sky_common.glsl>

// Ocean Abyss: slow teal currents, caustic sparkle lines, drifting motes.
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
    float w1 = sin(dome.x * 2.6 + time * 0.5 + sin(dome.y * 3.4 - time * 0.35) * 1.6);
    float w2 = sin((dome.x + dome.y) * 4.4 - time * 0.6 + w1 * 1.2);
    float flow = w1 * 0.5 + w2 * 0.5;
    float bands = smoothstep(-0.2, 0.9, flow);

    float grad = clamp(up * 0.5 + 0.5, 0.0, 1.0);
    vec3 deep = mix(vec3(0.004, 0.020, 0.035), primary * 0.10 + vec3(0.005, 0.020, 0.035), grad);
    vec3 col = mix(deep, mix(primary, secondary, 0.5), bands * 0.7 * effect);

    float spark = pow(clamp(1.0 - abs(flow), 0.0, 1.0), 12.0);
    col += secondary * spark * 1.2 * effect;

    float rays = 0.5 + 0.5 * sin(atan(dir.z, dir.x) * 9.0 + time * 0.2);
    col += primary * rays * smoothstep(0.3, 0.9, up) * 0.15 * effect;

    col += vec3(0.8, 0.95, 1.0) * starGlow(dir, 200.0, 0.02, time) * starsAmt * 0.5;

    col = mix(col * 0.35, col, smoothstep(-1.0, -0.55, up));

    col *= intensity;
    col = col / (1.0 + col);
    col = pow(col, vec3(0.85)) + ditherRgb(gl_FragCoord.xy);
    finalColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
