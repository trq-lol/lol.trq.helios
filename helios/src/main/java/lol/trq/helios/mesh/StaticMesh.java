/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

import lol.trq.helios.gl.GlBackend;

/**
 * A mesh uploaded once into its own {@code GL_STATIC_DRAW} buffers and vertex array. Built by
 * {@link MeshRecorder#build(MeshRenderer)}, drawn by {@link MeshRenderer#draw(StaticMesh, float[])}. Close it when
 * it is no longer needed.
 */
public final class StaticMesh implements AutoCloseable {
    private final GlBackend gl;
    final MeshFormat format;
    final int vao;
    final int vbo;
    final int ibo;
    final int quads;
    final int indexType;
    private boolean closed;

    StaticMesh(GlBackend gl, MeshFormat format, int vao, int vbo, int ibo, int quads, int indexType) {
        this.gl = gl;
        this.format = format;
        this.vao = vao;
        this.vbo = vbo;
        this.ibo = ibo;
        this.quads = quads;
        this.indexType = indexType;
    }

    /**
     * Returns the vertex format.
     *
     * @return the format
     */
    public MeshFormat format() {
        return format;
    }

    /**
     * Returns the number of quads; a line counts as one.
     *
     * @return the quad count
     */
    public int quads() {
        return quads;
    }

    /** Deletes the buffers and the vertex array. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            gl.deleteVertexArray(vao);
            gl.deleteBuffer(vbo);
            gl.deleteBuffer(ibo);
        }
    }
}
