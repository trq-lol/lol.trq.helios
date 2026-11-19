#version 410 core
// Batched shapes. See lol.trq.helios.batch.ShapeBatch for the vertex layout.
layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_local;
layout(location = 2) in vec4 a_shape;
layout(location = 3) in vec4 a_params;
layout(location = 4) in vec2 a_uv;
layout(location = 5) in vec4 a_color0;
layout(location = 6) in vec4 a_color1;
layout(location = 7) in vec4 a_color2;
layout(location = 8) in vec4 a_color3;

uniform mat4 u_mvp;

out Shape {
    vec2 local;
    vec4 shape;
    vec4 params;
    vec2 uv;
    vec4 color0;
    vec4 color1;
    vec4 color2;
    vec4 color3;
} v;

void main() {
    v.local = a_local;
    v.shape = a_shape;
    v.params = a_params;
    v.uv = a_uv;
    v.color0 = a_color0;
    v.color1 = a_color1;
    v.color2 = a_color2;
    v.color3 = a_color3;
    gl_Position = u_mvp * vec4(a_pos, 0.0, 1.0);
}
