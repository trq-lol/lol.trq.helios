/* SPDX-License-Identifier: MIT */
package lol.trq.helios.sdf;

/**
 * Signed distance functions and anti-aliasing coverage, the same formulas the shaders in
 * {@code lol/trq/helios/shaders/lib/sdf.glsl} use. Kept in Java so they can be tested off the GL thread, and so
 * the harness can predict every rendered pixel. Screen y points down, so angles grow clockwise on screen.
 *
 * <p>Distances are negative inside a shape, zero on its edge and positive outside.
 */
public final class Sdf {
    private Sdf() {}

    /**
     * Distance from a point to a rounded rectangle centred on the origin.
     *
     * @param px point x, relative to the centre
     * @param py point y, relative to the centre
     * @param halfWidth half the rectangle width
     * @param halfHeight half the rectangle height
     * @param radius the corner radius, clamped to {@code [0, min(halfWidth, halfHeight)]}
     * @return the signed distance
     */
    public static float roundedRect(float px, float py, float halfWidth, float halfHeight, float radius) {
        float r = clampRadius(radius, halfWidth, halfHeight);
        float qx = Math.abs(px) - halfWidth + r;
        float qy = Math.abs(py) - halfHeight + r;
        float outside = (float) Math.hypot(Math.max(qx, 0f), Math.max(qy, 0f));
        float inside = Math.min(Math.max(qx, qy), 0f);
        return outside + inside - r;
    }

    /**
     * Coverage of a pixel by a shape: 1 well inside, 0 well outside, and a linear ramp across one pixel centred on
     * the edge, so the feather is half a pixel either side.
     *
     * @param distance the signed distance at the pixel centre
     * @param pixel the length of one pixel in distance units (the gradient length of the distance across a pixel)
     * @return the coverage in {@code [0, 1]}
     */
    public static float coverage(float distance, float pixel) {
        float c = 0.5f - distance / Math.max(pixel, 1e-5f);
        return Math.min(Math.max(c, 0f), 1f);
    }

    /**
     * Clamps a corner radius so it fits the rectangle.
     *
     * @param radius the requested radius
     * @param halfWidth half the rectangle width
     * @param halfHeight half the rectangle height
     * @return the radius used
     */
    public static float clampRadius(float radius, float halfWidth, float halfHeight) {
        return Math.max(0f, Math.min(radius, Math.min(halfWidth, halfHeight)));
    }

    /**
     * Turns a filled shape into an outline: a band of the given width just inside its edge.
     *
     * @param distance the distance to the filled shape
     * @param width the outline width
     * @return the distance to the outline
     */
    public static float stroke(float distance, float width) {
        return Math.abs(distance + width * 0.5f) - width * 0.5f;
    }

    /**
     * Distance from a point to a line segment.
     *
     * @param px point x
     * @param py point y
     * @param ax segment start x
     * @param ay segment start y
     * @param bx segment end x
     * @param by segment end y
     * @return the distance, never negative
     */
    public static float segment(float px, float py, float ax, float ay, float bx, float by) {
        float pax = px - ax;
        float pay = py - ay;
        float bax = bx - ax;
        float bay = by - ay;
        float h = clamp((pax * bax + pay * bay) / (bax * bax + bay * bay), 0f, 1f);
        return (float) Math.hypot(pax - bax * h, pay - bay * h);
    }

    /**
     * Distance from a point to a ring segment with butt caps, centred on the origin.
     *
     * @param px point x
     * @param py point y
     * @param radius the outer radius
     * @param thickness the ring thickness, inward from the outer radius
     * @param start the start angle in radians, 0 along +x
     * @param sweep the swept angle in radians, clockwise on screen; 2&pi; or more is a full ring
     * @return the signed distance
     */
    public static float arc(float px, float py, float radius, float thickness, float start, float sweep) {
        float len = (float) Math.hypot(px, py);
        float inner = radius - thickness;
        float ring = Math.abs(len - (radius - thickness * 0.5f)) - thickness * 0.5f;
        if (sweep >= TAU) {
            return ring;
        }
        float rel = mod((float) Math.atan2(py, px) - start, TAU);
        if (rel <= sweep) {
            float m = Math.min(rel, sweep - rel);
            return Math.max(ring, -len * (float) Math.sin(Math.min(m, Math.PI / 2)));
        }
        float c0 = (float) Math.cos(start);
        float s0 = (float) Math.sin(start);
        float c1 = (float) Math.cos(start + sweep);
        float s1 = (float) Math.sin(start + sweep);
        return Math.min(
                segment(px, py, c0 * inner, s0 * inner, c0 * radius, s0 * radius),
                segment(px, py, c1 * inner, s1 * inner, c1 * radius, s1 * radius));
    }

    /**
     * Drop shadow falloff: 1 well inside, 0 at {@code softness} outside the edge.
     *
     * @param distance the signed distance
     * @param softness half the width of the falloff band
     * @return the shadow opacity in {@code [0, 1]}
     */
    public static float shadow(float distance, float softness) {
        return 1f - smoothstep(-softness, softness, distance);
    }

    /**
     * Inner glow: 1 at the edge, fading to 0 at {@code width} inside it.
     *
     * @param distance the signed distance
     * @param width how far the glow reaches inward
     * @return the glow strength in {@code [0, 1]}
     */
    public static float glow(float distance, float width) {
        return smoothstep(-width, 0f, distance);
    }

    /**
     * GLSL {@code smoothstep}.
     *
     * @param edge0 lower edge
     * @param edge1 upper edge
     * @param x the value
     * @return the Hermite interpolation in {@code [0, 1]}
     */
    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** A full turn in radians. */
    public static final float TAU = (float) (Math.PI * 2);

    private static float clamp(float v, float lo, float hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    private static float mod(float x, float y) {
        return x - y * (float) Math.floor(x / y);
    }
}
