#version 410 core
#include "lib/sdf.glsl"

// Shape kinds, matching lol.trq.helios.batch.ShapeBatch.
#define KIND_RECT 0
#define KIND_ARC 1
#define KIND_SHADOW 2
#define KIND_GLOW 3
#define KIND_IMAGE 4
#define KIND_GLYPH 5

uniform sampler2D u_texture;
// Fragments with alpha at or below this are discarded; 0 keeps everything. Used to write stencil masks.
uniform float u_alphaCutoff;

in Shape {
    vec2 local;
    vec4 shape;   // half width, half height, corner radius (glyph: softness), kind
    vec4 params;  // stroke width or softness, gradient mode, arc start or radial radius, arc sweep;
                  // glyph: pixel range, weight, atlas width, atlas height
    vec2 uv;
    vec4 color0;  // top left, or the centre of a radial gradient
    vec4 color1;  // top right, or the edge of a radial gradient
    vec4 color2;  // bottom right
    vec4 color3;  // bottom left
} v;

layout(location = 0) out vec4 fragColor;

vec4 paint(vec2 p, vec2 halfSize) {
    if (v.params.y > 0.5) {
        float t = clamp(length(p) / max(v.params.z, 1e-5), 0.0, 1.0);
        return mix(v.color0, v.color1, t);
    }
    vec2 uv = clamp(p / max(halfSize, vec2(1e-5)) * 0.5 + 0.5, 0.0, 1.0);
    return mix(mix(v.color0, v.color1, uv.x), mix(v.color3, v.color2, uv.x), uv.y);
}

void main() {
    int kind = int(v.shape.w + 0.5);
    vec2 p = v.local;
    vec2 halfSize = v.shape.xy;
    float alpha;
    if (kind == KIND_GLYPH) {
        // Multi-channel distance: the median of the three channels, in field units (0.5 on the edge).
        vec3 s = texture(u_texture, v.uv).rgb;
        float sd = max(min(s.r, s.g), min(max(s.r, s.g), s.b)) - 0.5;
        vec2 unitRange = vec2(v.params.x) / v.params.zw;
        vec2 screenTexSize = vec2(1.0) / fwidth(v.uv);
        float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
        float d = screenPxRange * (sd + v.params.y);
        float softness = v.shape.z;
        alpha = softness > 0.0 ? smoothstep(-softness, softness, d) : clamp(d + 0.5, 0.0, 1.0);
    } else if (kind == KIND_ARC) {
        alpha = helios_coverage(helios_sdArc(p, halfSize.x, halfSize.y, v.params.z, v.params.w), p);
    } else {
        float d = helios_sdRoundedRect(p, halfSize, v.shape.z);
        if (kind == KIND_SHADOW) {
            alpha = helios_shadow(d, v.params.x);
        } else if (kind == KIND_GLOW) {
            alpha = helios_coverage(d, p) * helios_glow(d, v.params.x);
        } else {
            if (v.params.x > 0.0) {
                d = helios_stroke(d, v.params.x);
            }
            alpha = helios_coverage(d, p);
        }
    }
    vec4 color;
    if (kind == KIND_ARC) {
        color = paint(p, vec2(v.shape.x));
    } else if (kind == KIND_GLYPH) {
        color = v.color0;
    } else {
        color = paint(p, v.shape.xy);
    }
    if (kind == KIND_IMAGE) {
        color *= texture(u_texture, v.uv);
    }
    float a = color.a * alpha;
    if (a <= u_alphaCutoff && u_alphaCutoff > 0.0) {
        discard;
    }
    fragColor = vec4(color.rgb, a);
}
