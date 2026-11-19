#version 410 core
in vec4 v_color;
noperspective in float v_edge;
flat in float v_half;

layout(location = 0) out vec4 fragColor;

void main() {
    // One pixel of coverage falloff, centred on the line's edge.
    float coverage = clamp(v_half + 0.5 - abs(v_edge), 0.0, 1.0);
    float alpha = v_color.a * coverage;
    if (alpha <= 0.0) discard;
    fragColor = vec4(v_color.rgb, alpha);
}
