// Batched shapes. See lol.trq.helios.batch.ShapeBatch for the vertex layout.
attribute vec2 a_pos;
attribute vec2 a_local;
attribute vec4 a_shape;
attribute vec4 a_params;
attribute vec2 a_uv;
attribute vec4 a_color0;
attribute vec4 a_color1;
attribute vec4 a_color2;
attribute vec4 a_color3;

varying vec2 v_local;
varying vec4 v_shape;
varying vec4 v_params;
varying vec2 v_uv;
varying vec4 v_color0;
varying vec4 v_color1;
varying vec4 v_color2;
varying vec4 v_color3;

void main() {
    v_local = a_local;
    v_shape = a_shape;
    v_params = a_params;
    v_uv = a_uv;
    v_color0 = a_color0;
    v_color1 = a_color1;
    v_color2 = a_color2;
    v_color3 = a_color3;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(a_pos, 0.0, 1.0);
}
