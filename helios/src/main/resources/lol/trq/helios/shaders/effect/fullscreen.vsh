// Full-screen pass: the quad is given in normalized device coordinates, texture coordinates on unit 0.
varying vec2 v_uv;

void main() {
    v_uv = gl_MultiTexCoord0.xy;
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
}
