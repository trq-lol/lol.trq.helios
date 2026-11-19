/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

/**
 * Takes textured quads: four vertices per quad, in order around it. Each vertex is a position, texture coordinates, a
 * normal and a colour that multiplies the texture. With lighting on, the normal shades the quad as Minecraft 1.8.9
 * shades entities: two fixed directional lights and an ambient term (see {@link MeshRenderer#lighting(boolean)}).
 */
public interface TexturedSink extends MeshSink {
    /**
     * Writes one vertex.
     *
     * @param x x
     * @param y y
     * @param z z
     * @param u texture u
     * @param v texture v
     * @param nx normal x
     * @param ny normal y
     * @param nz normal z
     * @param argb the tint, {@code 0xAARRGGBB}; {@code 0xFFFFFFFF} for none
     * @return this sink
     */
    TexturedSink vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int argb);

    @Override
    TexturedSink transform(float[] matrix);
}
