#version 410 core
// Flat-coloured quads. See lol.trq.helios.mesh.MeshFormat.COLOR.
layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec4 a_color;

uniform mat4 u_mvp;

out vec4 v_color;

void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
