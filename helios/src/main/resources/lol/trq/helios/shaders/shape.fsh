#include "lib/sdf.glsl"

// Shape kinds, matching lol.trq.helios.batch.ShapeBatch.
#define KIND_RECT 0
#define KIND_ARC 1
#define KIND_SHADOW 2
#define KIND_GLOW 3
#define KIND_IMAGE 4
#define KIND_GLYPH 5

uniform sampler2D u_texture;

varying vec2 v_local;
varying vec4 v_shape;   // half width, half height, corner radius (glyph: softness), kind
varying vec4 v_params;  // stroke width or softness, gradient mode, arc start or radial radius, arc sweep;
                        // glyph: pixel range, weight, atlas width, atlas height
varying vec2 v_uv;
varying vec4 v_color0;  // top left, or the centre of a radial gradient
varying vec4 v_color1;  // top right, or the edge of a radial gradient
varying vec4 v_color2;  // bottom right
varying vec4 v_color3;  // bottom left

vec4 paint(vec2 p, vec2 halfSize) {
    if (v_params.y > 0.5) {
        float t = clamp(length(p) / max(v_params.z, 1e-5), 0.0, 1.0);
        return mix(v_color0, v_color1, t);
    }
    vec2 uv = clamp(p / max(halfSize, vec2(1e-5)) * 0.5 + 0.5, 0.0, 1.0);
    return mix(mix(v_color0, v_color1, uv.x), mix(v_color3, v_color2, uv.x), uv.y);
}

void main() {
    int kind = int(v_shape.w + 0.5);
    vec2 p = v_local;
    vec2 halfSize = v_shape.xy;
    float alpha;
    if (kind == KIND_GLYPH) {
        // Multi-channel distance: the median of the three channels, in field units (0.5 on the edge).
        vec3 s = texture2D(u_texture, v_uv).rgb;
        float sd = max(min(s.r, s.g), min(max(s.r, s.g), s.b)) - 0.5;
        vec2 unitRange = vec2(v_params.x) / v_params.zw;
        vec2 screenTexSize = vec2(1.0) / fwidth(v_uv);
        float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
        float d = screenPxRange * (sd + v_params.y);
        float softness = v_shape.z;
        alpha = softness > 0.0 ? smoothstep(-softness, softness, d) : clamp(d + 0.5, 0.0, 1.0);
    } else if (kind == KIND_ARC) {
        alpha = helios_coverage(helios_sdArc(p, halfSize.x, halfSize.y, v_params.z, v_params.w), p);
    } else {
        float d = helios_sdRoundedRect(p, halfSize, v_shape.z);
        if (kind == KIND_SHADOW) {
            alpha = helios_shadow(d, v_params.x);
        } else if (kind == KIND_GLOW) {
            alpha = helios_coverage(d, p) * helios_glow(d, v_params.x);
        } else {
            if (v_params.x > 0.0) {
                d = helios_stroke(d, v_params.x);
            }
            alpha = helios_coverage(d, p);
        }
    }
    vec4 color;
    if (kind == KIND_ARC) {
        color = paint(p, vec2(v_shape.x));
    } else if (kind == KIND_GLYPH) {
        color = v_color0;
    } else {
        color = paint(p, v_shape.xy);
    }
    if (kind == KIND_IMAGE) {
        color *= texture2D(u_texture, v_uv);
    }
    gl_FragColor = vec4(color.rgb, color.a * alpha);
}
