#version 410 core
// Textured, optionally lit quads. See lol.trq.helios.mesh.MeshFormat.TEXTURED.
layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec2 a_uv;
layout(location = 2) in vec3 a_normal;
layout(location = 3) in vec4 a_color;

uniform mat4 u_mvp;
uniform mat4 u_model;

out Mesh {
    vec2 uv;
    vec3 normal;
    vec4 color;
} v;

void main() {
    v.uv = a_uv;
    v.normal = mat3(u_model) * a_normal;
    v.color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
