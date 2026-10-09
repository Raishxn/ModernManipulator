#version 150

uniform sampler2D Sampler0;

in vec4 vColor;
in vec2 vUV;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, vUV) * vColor;

    if (color.a < 0.01) discard;

    fragColor = color;
}
