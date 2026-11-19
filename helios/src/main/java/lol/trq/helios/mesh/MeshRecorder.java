/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Records vertices of one format through the matching sink, to upload once as a {@link StaticMesh}. Recording
 * touches no GL state and can run on any thread; {@link #build(MeshRenderer)} must run on the render thread.
 *
 * <pre>{@code
 * MeshRecorder rec = new MeshRecorder(MeshFormat.TEXTURED);
 * rec.textured().vertex(...)...;
 * StaticMesh mesh = rec.build(helios.meshes());
 * helios.meshes().texture(tex).draw(mesh, model);
 * }</pre>
 */
public final class MeshRecorder {
    private final Store store;

    /**
     * Creates an empty recorder.
     *
     * @param format the vertex format; only the matching sink accepts vertices
     */
    public MeshRecorder(MeshFormat format) {
        this.store = new Store(format);
    }

    /**
     * Returns the sink for a {@link MeshFormat#COLOR} recorder.
     *
     * @return the sink
     * @throws IllegalStateException if the recorder has another format
     */
    public QuadSink quads() {
        require(MeshFormat.COLOR);
        return store.quads;
    }

    /**
     * Returns the sink for a {@link MeshFormat#LINE} recorder.
     *
     * @return the sink
     * @throws IllegalStateException if the recorder has another format
     */
    public LineSink lines() {
        require(MeshFormat.LINE);
        return store.lines;
    }

    /**
     * Returns the sink for a {@link MeshFormat#TEXTURED} recorder.
     *
     * @return the sink
     * @throws IllegalStateException if the recorder has another format
     */
    public TexturedSink textured() {
        require(MeshFormat.TEXTURED);
        return store.textured;
    }

    /**
     * Returns the number of complete quads recorded; a line counts as one.
     *
     * @return the quad count
     */
    public int quadCount() {
        return store.quadCount();
    }

    /**
     * Uploads the complete quads recorded so far. The recorder can keep recording or be discarded.
     *
     * @param renderer the renderer the mesh will be drawn with
     * @return the mesh
     */
    public StaticMesh build(MeshRenderer renderer) {
        int quads = store.quadCount();
        ByteBuffer copy = store.data.duplicate().order(ByteOrder.nativeOrder());
        copy.flip().limit(quads * 4 * store.format.stride());
        return renderer.upload(store.format, copy, quads);
    }

    private void require(MeshFormat format) {
        if (store.format != format) {
            throw new IllegalStateException("this recorder records " + store.format + ", not " + format);
        }
    }

    private static final class Store extends VertexStore {
        Store(MeshFormat format) {
            super(format, ByteBuffer.allocateDirect(64 * 4 * format.stride()).order(ByteOrder.nativeOrder()));
        }

        @Override
        void reserveQuad() {
            int need = 4 * format.stride();
            if (data.remaining() < need) {
                ByteBuffer grown = ByteBuffer.allocateDirect(Math.max(data.capacity() * 2, data.position() + need))
                        .order(ByteOrder.nativeOrder());
                data.flip();
                grown.put(data);
                data = grown;
            }
        }
    }
}
