#version 150

// port of the original fancybox shader: animated diagonal stripes

uniform float MMTime;

in vec4 vColor;
in vec2 vUV;

out vec4 fragColor;

void main() {
    float t = MMTime / 2.5 * 2.0 * 3.14159;

    float theta = (vUV.x + vUV.y) * 5.0;
    float k = (sin(theta + t) + 1.0) * 0.5 + 0.25;

    fragColor = mix(vColor * 0.25, vColor, k);
}
