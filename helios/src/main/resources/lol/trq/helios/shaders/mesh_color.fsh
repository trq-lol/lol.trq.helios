#version 410 core
in vec4 v_color;

layout(location = 0) out vec4 fragColor;

void main() {
    if (v_color.a <= 0.0) discard;
    fragColor = v_color;
}
