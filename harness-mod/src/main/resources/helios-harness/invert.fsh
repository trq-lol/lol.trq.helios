#version 410 core
// Test pass for the post-processing chain: inverts colour, keeps alpha.
uniform sampler2D u_texture;
uniform vec2 u_texel;

in vec2 v_uv;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 c = texture(u_texture, v_uv);
    fragColor = vec4(1.0 - c.rgb, c.a);
}
