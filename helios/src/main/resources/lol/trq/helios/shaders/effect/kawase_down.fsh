// Dual Kawase blur, downsample step: five taps.
uniform sampler2D u_texture;
uniform vec2 u_halfPixel;
uniform float u_offset;

varying vec2 v_uv;

void main() {
    vec2 o = u_halfPixel * u_offset;
    vec4 sum = texture2D(u_texture, v_uv) * 4.0;
    sum += texture2D(u_texture, v_uv - o);
    sum += texture2D(u_texture, v_uv + o);
    sum += texture2D(u_texture, v_uv + vec2(o.x, -o.y));
    sum += texture2D(u_texture, v_uv - vec2(o.x, -o.y));
    gl_FragColor = sum / 8.0;
}
