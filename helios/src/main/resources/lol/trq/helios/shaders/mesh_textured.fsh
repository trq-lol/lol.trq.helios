#version 410 core
in Mesh {
    vec2 uv;
    vec3 normal;
    vec4 color;
} v;

uniform sampler2D u_texture;
uniform float u_alphaCutoff;
uniform int u_lighting;
uniform vec4 u_light0; // xyz: direction, w: diffuse
uniform vec4 u_light1; // xyz: direction, w: ambient

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 c = texture(u_texture, v.uv) * v.color;
    if (c.a <= u_alphaCutoff) discard;
    if (u_lighting != 0) {
        vec3 n = normalize(v.normal);
        float light = u_light1.w + u_light0.w * (max(dot(n, u_light0.xyz), 0.0) + max(dot(n, u_light1.xyz), 0.0));
        c.rgb *= min(light, 1.0);
    }
    fragColor = c;
}
