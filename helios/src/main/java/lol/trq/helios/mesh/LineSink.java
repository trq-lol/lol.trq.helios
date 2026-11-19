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

    @Override
    LineSink transform(float[] matrix);
}
