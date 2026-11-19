// Signed distance functions and pixel coverage. Mirrors lol.trq.helios.sdf.Sdf: keep both in step.
// Distances are negative inside, zero on the edge, positive outside. Screen y points down.

const float HELIOS_PI = 3.14159265358979;
const float HELIOS_TAU = 6.28318530717959;

float helios_sdRoundedRect(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Outline of a shape: a band of the given width inside its edge.
float helios_stroke(float d, float width) {
    return abs(d + width * 0.5) - width * 0.5;
}

float helios_sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

// Ring segment centred on the origin with butt caps. Angles in radians, 0 along +x, growing clockwise on screen.
float helios_sdArc(vec2 p, float radius, float thickness, float start, float sweep) {
    float len = length(p);
    float inner = radius - thickness;
    float ring = abs(len - (radius - thickness * 0.5)) - thickness * 0.5;
    if (sweep >= HELIOS_TAU) {
        return ring;
    }
    float rel = mod(atan(p.y, p.x) - start, HELIOS_TAU);
    if (rel <= sweep) {
        float m = min(rel, sweep - rel);
        return max(ring, -len * sin(min(m, HELIOS_PI * 0.5)));
    }
    vec2 d0 = vec2(cos(start), sin(start));
    vec2 d1 = vec2(cos(start + sweep), sin(start + sweep));
    return min(helios_sdSegment(p, d0 * inner, d0 * radius), helios_sdSegment(p, d1 * inner, d1 * radius));
}

// Linear ramp across one pixel centred on the edge: feather of half a pixel either side. The pixel size comes from
// the derivatives of the local position p, which is affine and so exact everywhere; derivatives of the distance
// itself are wrong where the edge curves tightly or has a corner.
float helios_coverage(float d, vec2 p) {
    float pixel = 0.5 * (length(dFdx(p)) + length(dFdy(p)));
    return clamp(0.5 - d / max(pixel, 1e-5), 0.0, 1.0);
}

// Soft falloff for drop shadows: 1 well inside, 0 at softness outside the edge.
float helios_shadow(float d, float softness) {
    return 1.0 - smoothstep(-softness, softness, d);
}

// Inner glow: strongest at the edge, fading to 0 at width inside it.
float helios_glow(float d, float width) {
    return smoothstep(-width, 0.0, d);
}
