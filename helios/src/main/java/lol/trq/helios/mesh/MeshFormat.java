/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

import lol.trq.helios.gl.GlBackend;

/** The vertex layouts behind the sinks, one per sink type, each with its own shader. */
public enum MeshFormat {
    /** {@link QuadSink}: position (3 floats) and colour (4 bytes); 16 bytes. */
    COLOR(16, "mesh_color"),
    /** {@link LineSink}: both end points (6 floats), the corner (along, side), the width and the colour; 40 bytes. */
    LINE(40, "mesh_line"),
    /** {@link TexturedSink}: position, texture coordinates, normal (3 signed bytes and a pad) and colour; 28 bytes. */
    TEXTURED(28, "mesh_textured");

    private final int stride;
    private final String shader;

    MeshFormat(int stride, String shader) {
        this.stride = stride;
        this.shader = shader;
    }

    /**
     * Returns the bytes per vertex.
     *
     * @return the stride
     */
    public int stride() {
        return stride;
    }

    String vertexShader() {
        return shader + ".vsh";
    }

    String fragmentShader() {
        return shader + ".fsh";
    }

    int attributes() {
        return switch (this) {
            case COLOR -> 2;
            case LINE -> 5;
            case TEXTURED -> 4;
        };
    }

    /** Points the attributes at the bound array buffer, starting at byte 0. */
    void pointAttributes(GlBackend gl) {
        switch (this) {
            case COLOR -> {
                gl.vertexAttribPointer(0, 3, GlBackend.FLOAT, false, stride, 0);
                gl.vertexAttribPointer(1, 4, GlBackend.UNSIGNED_BYTE, true, stride, 12);
            }
            case LINE -> {
                gl.vertexAttribPointer(0, 3, GlBackend.FLOAT, false, stride, 0);
                gl.vertexAttribPointer(1, 3, GlBackend.FLOAT, false, stride, 12);
                gl.vertexAttribPointer(2, 2, GlBackend.FLOAT, false, stride, 24);
                gl.vertexAttribPointer(3, 1, GlBackend.FLOAT, false, stride, 32);
                gl.vertexAttribPointer(4, 4, GlBackend.UNSIGNED_BYTE, true, stride, 36);
            }
            case TEXTURED -> {
                gl.vertexAttribPointer(0, 3, GlBackend.FLOAT, false, stride, 0);
                gl.vertexAttribPointer(1, 2, GlBackend.FLOAT, false, stride, 12);
                gl.vertexAttribPointer(2, 3, GlBackend.BYTE, true, stride, 20);
                gl.vertexAttribPointer(3, 4, GlBackend.UNSIGNED_BYTE, true, stride, 24);
            }
        }
    }
}
