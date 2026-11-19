#version 410 core
// Dual Kawase blur, downsample step: five taps.
uniform sampler2D u_texture;
uniform vec2 u_halfPixel;
uniform float u_offset;

in vec2 v_uv;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = u_halfPixel * u_offset;
    vec4 sum = texture(u_texture, v_uv) * 4.0;
    sum += texture(u_texture, v_uv - o);
    sum += texture(u_texture, v_uv + o);
    sum += texture(u_texture, v_uv + vec2(o.x, -o.y));
    sum += texture(u_texture, v_uv - vec2(o.x, -o.y));
    fragColor = sum / 8.0;
}
