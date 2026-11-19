/* SPDX-License-Identifier: MIT */
package lol.trq.helios.sdf;

/**
 * Signed distance functions and anti-aliasing coverage, the same formulas the shaders in
 * {@code lol/trq/helios/shaders/lib/sdf.glsl} use. Kept in Java so they can be tested off the GL thread.
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
}
