/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

/**
 * Takes flat-coloured quads: four vertices per quad, in order around it. Each vertex is a position and a colour; there
 * is no lighting or texture. Drawn as two triangles, 0-1-2 and 2-3-0.
 */
public interface QuadSink extends MeshSink {
    /**
     * Writes one vertex.
     *
     * @param x x
     * @param y y
     * @param z z
     * @param argb the colour, {@code 0xAARRGGBB}
     * @return this sink
     */
    QuadSink vertex(float x, float y, float z, int argb);

    /**
     * Writes one triangle, as a quad whose last vertex repeats the third. Fans and strips are drawn as a triangle per
     * step. Call it only between quads, never after a partial quad.
     *
     * @param x0 first x
     * @param y0 first y
     * @param z0 first z
     * @param argb0 first colour
     * @param x1 second x
     * @param y1 second y
     * @param z1 second z
     * @param argb1 second colour
     * @param x2 third x
     * @param y2 third y
     * @param z2 third z
     * @param argb2 third colour
     * @return this sink
     */
    default QuadSink triangle(
            float x0,
            float y0,
            float z0,
            int argb0,
            float x1,
            float y1,
            float z1,
            int argb1,
            float x2,
            float y2,
            float z2,
            int argb2) {
        return vertex(x0, y0, z0, argb0)
                .vertex(x1, y1, z1, argb1)
                .vertex(x2, y2, z2, argb2)
                .vertex(x2, y2, z2, argb2);
    }

    @Override
    QuadSink transform(float[] matrix);
}
