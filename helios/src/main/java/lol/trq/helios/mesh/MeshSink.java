/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

/**
 * What every vertex sink shares: a transform applied on the CPU to everything written after it. Positions are
 * multiplied by the matrix and normals by its upper 3x3, then normalized. Transforming on the CPU lets meshes with
 * different transforms, such as the attachments of every player on screen, go out in one draw call.
 */
public interface MeshSink {
    /**
     * Sets the transform for the vertices that follow. The matrix is copied.
     *
     * @param matrix a column-major 4x4 matrix, or {@code null} for none
     * @return this sink
     */
    MeshSink transform(float[] matrix);
}
