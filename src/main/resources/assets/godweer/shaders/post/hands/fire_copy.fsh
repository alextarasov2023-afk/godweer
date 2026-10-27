#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D SourceSampler;

void main() {
    finalColor = texture(SourceSampler, uv);
}
