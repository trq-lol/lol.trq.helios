// Test pass for the post-processing chain: inverts colour, keeps alpha.
uniform sampler2D u_texture;
uniform vec2 u_texel;

varying vec2 v_uv;

void main() {
    vec4 c = texture2D(u_texture, v_uv);
    gl_FragColor = vec4(1.0 - c.rgb, c.a);
}
