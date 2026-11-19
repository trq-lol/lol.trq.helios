#version 410 core
// Draws a texture scaled by a colour.
uniform sampler2D u_texture;
uniform vec4 u_tint;

in vec2 v_uv;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(u_texture, v_uv) * u_tint;
}
