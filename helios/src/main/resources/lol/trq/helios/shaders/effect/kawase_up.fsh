#version 410 core
// Dual Kawase blur, upsample step: eight taps.
uniform sampler2D u_texture;
uniform vec2 u_halfPixel;
uniform float u_offset;

in vec2 v_uv;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = u_halfPixel * u_offset;
    vec4 sum = texture(u_texture, v_uv + vec2(-o.x * 2.0, 0.0));
    sum += texture(u_texture, v_uv + vec2(-o.x, o.y)) * 2.0;
    sum += texture(u_texture, v_uv + vec2(0.0, o.y * 2.0));
    sum += texture(u_texture, v_uv + vec2(o.x, o.y)) * 2.0;
    sum += texture(u_texture, v_uv + vec2(o.x * 2.0, 0.0));
    sum += texture(u_texture, v_uv + vec2(o.x, -o.y)) * 2.0;
    sum += texture(u_texture, v_uv + vec2(0.0, -o.y * 2.0));
    sum += texture(u_texture, v_uv + vec2(-o.x, -o.y)) * 2.0;
    fragColor = sum / 12.0;
}
