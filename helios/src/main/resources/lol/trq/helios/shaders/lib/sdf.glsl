// Signed distance functions and pixel coverage. Mirrors lol.trq.helios.sdf.Sdf.

float helios_sdRoundedRect(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Linear ramp across one pixel centred on the edge: feather of half a pixel either side.
float helios_coverage(float d) {
    float pixel = length(vec2(dFdx(d), dFdy(d)));
    return clamp(0.5 - d / max(pixel, 1e-5), 0.0, 1.0);
}
