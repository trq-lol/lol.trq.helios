// Rounded rectangle. The quad carries its position relative to the shape centre in texture coordinate 0.
varying vec2 v_local;

void main() {
    v_local = gl_MultiTexCoord0.xy;
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
