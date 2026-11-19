#version 410 core
// Screen-facing line quads. See lol.trq.helios.mesh.MeshFormat.LINE.
layout(location = 0) in vec3 a_start;
layout(location = 1) in vec3 a_end;
layout(location = 2) in vec2 a_corner; // x: 0 at the start, 1 at the end; y: side, -1 or 1
layout(location = 3) in float a_width;
layout(location = 4) in vec4 a_color;

uniform mat4 u_mvp;
uniform vec2 u_viewport;

out vec4 v_color;
noperspective out float v_edge; // signed distance from the centre line, in pixels
flat out float v_half;

const float NEAR_W = 1e-4;

void main() {
    vec4 a = u_mvp * vec4(a_start, 1.0);
    vec4 b = u_mvp * vec4(a_end, 1.0);
    if (a.w < NEAR_W && b.w < NEAR_W) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        v_color = vec4(0.0);
        v_edge = 0.0;
        v_half = 0.0;
        return;
    }
    if (a.w < NEAR_W) {
        a = mix(a, b, (NEAR_W - a.w) / (b.w - a.w));
    } else if (b.w < NEAR_W) {
        b = mix(b, a, (NEAR_W - b.w) / (a.w - b.w));
    }
    vec2 halfViewport = 0.5 * u_viewport;
    vec2 sa = a.xy / a.w * halfViewport;
    vec2 sb = b.xy / b.w * halfViewport;
    vec2 d = sb - sa;
    float len = length(d);
    vec2 dir = len > 1e-6 ? d / len : vec2(1.0, 0.0);
    vec2 normal = vec2(-dir.y, dir.x);

    float width = max(a_width, 1.0);
    float reach = width * 0.5 + 0.5;
    vec4 p = a_corner.x < 0.5 ? a : b;
    p.xy += normal * (a_corner.y * reach) / halfViewport * p.w;
    gl_Position = p;

    v_color = vec4(a_color.rgb, a_color.a * min(a_width, 1.0));
    v_edge = a_corner.y * reach;
    v_half = width * 0.5;
}
