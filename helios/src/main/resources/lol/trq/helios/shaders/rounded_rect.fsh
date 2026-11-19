#include "lib/sdf.glsl"

uniform vec2 u_halfSize;
uniform float u_radius;
uniform vec4 u_color;

varying vec2 v_local;

void main() {
    float d = helios_sdRoundedRect(v_local, u_halfSize, u_radius);
    float a = helios_coverage(d);
    gl_FragColor = vec4(u_color.rgb, u_color.a * a);
}
