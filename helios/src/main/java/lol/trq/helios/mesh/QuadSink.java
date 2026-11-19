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

    @Override
    QuadSink transform(float[] matrix);
}
