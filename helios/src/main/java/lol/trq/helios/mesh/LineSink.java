/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

/**
 * Takes line segments. Each segment becomes a quad that faces the screen and is its width in framebuffer pixels wide
 * at any distance, with an anti-aliased edge one pixel across. Ends are square and stop at the end points. A segment
 * that crosses the near plane is clipped to it; one entirely behind the camera draws nothing.
 */
public interface LineSink extends MeshSink {
    /**
     * Writes one segment.
     *
     * @param x0 start x
     * @param y0 start y
     * @param z0 start z
     * @param x1 end x
     * @param y1 end y
     * @param z1 end z
     * @param argb the colour, {@code 0xAARRGGBB}
     * @param width the width in framebuffer pixels; below 1 it draws one pixel wide and fades instead
     * @return this sink
     */
    LineSink line(float x0, float y0, float z0, float x1, float y1, float z1, int argb, float width);

    /**
     * Writes connected segments through a list of points, one segment per pair of neighbours. Joins are not filled, so
     * wide strips show small notches at sharp corners.
     *
     * @param points x, y, z triples; at least two points
     * @param closed whether a last segment joins the last point to the first
     * @param argb the colour, {@code 0xAARRGGBB}
     * @param width the width in framebuffer pixels
     * @return this sink
     * @throws IllegalArgumentException if {@code points} is not a whole number of triples, or has fewer than two
     */
    default LineSink strip(float[] points, boolean closed, int argb, float width) {
        if (points.length % 3 != 0 || points.length < 6) {
            throw new IllegalArgumentException("a strip needs at least two x, y, z points: " + points.length);
        }
        int n = points.length / 3;
        int segments = closed && n > 2 ? n : n - 1;
        for (int i = 0; i < segments; i++) {
            int a = i * 3;
            int b = (i + 1) % n * 3;
            line(points[a], points[a + 1], points[a + 2], points[b], points[b + 1], points[b + 2], argb, width);
        }
        return this;
    }

    @Override
    LineSink transform(float[] matrix);
}
