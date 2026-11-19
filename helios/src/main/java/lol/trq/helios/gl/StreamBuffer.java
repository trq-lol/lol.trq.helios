/* SPDX-License-Identifier: MIT */
package lol.trq.helios.gl;

import java.nio.ByteBuffer;

/**
 * Streamed vertex data, the one place that decides how per-frame uploads reach the GPU.
 *
 * <p>The strategy keeps a ring of array buffers. Each frame appends its data to one buffer, so no upload touches a
 * region the GPU may still be reading. The write itself is {@code glBufferSubData} by default, or an unsynchronized
 * {@code glMapBufferRange} with {@code -Dhelios.stream=map}; the fences make the mapping safe. {@link #endFrame()} fences the
 * buffer and moves to the next, waiting on that one's fence from {@code ring} frames ago (normally long signalled). A
 * frame that outgrows its buffer orphans it and starts again at offset 0.
 */
public final class StreamBuffer implements AutoCloseable {
    /** Buffers in the ring. */
    public static final int RING = 3;

    private static final long FENCE_TIMEOUT_NANOS = 1_000_000_000L;

    /** {@code -Dhelios.stream}: {@code subdata} (default) or {@code map}. */
    public static final String STRATEGY_PROPERTY = "helios.stream";

    private final GlBackend gl;
    private final long capacity;
    private final boolean mapped = "map".equals(System.getProperty(STRATEGY_PROPERTY));
    private final int[] buffers = new int[RING];
    private final long[] fences = new long[RING];
    private int current;
    private long offset;
    private int orphans;

    /**
     * Creates the ring lazily on first use.
     *
     * @param gl the backend
     * @param capacity bytes per buffer
     */
    public StreamBuffer(GlBackend gl, long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.gl = gl;
        this.capacity = capacity;
    }

    /**
     * Uploads data into the current buffer and leaves it bound to {@link GlBackend#ARRAY_BUFFER}.
     *
     * @param data the bytes, from position to limit; at most the capacity
     * @param alignment the start offset is a multiple of this, such as the vertex stride
     * @return the byte offset the data starts at
     */
    public long upload(ByteBuffer data, int alignment) {
        int size = data.remaining();
        if (size > capacity) {
            throw new IllegalArgumentException("upload of " + size + " bytes exceeds capacity " + capacity);
        }
        if (buffers[current] == 0) {
            buffers[current] = gl.createBuffer();
            gl.bindBuffer(GlBackend.ARRAY_BUFFER, buffers[current]);
            gl.bufferStorage(GlBackend.ARRAY_BUFFER, capacity, GlBackend.STREAM_DRAW);
        } else {
            gl.bindBuffer(GlBackend.ARRAY_BUFFER, buffers[current]);
        }
        long at = (offset + alignment - 1) / alignment * alignment;
        if (at + size > capacity) {
            gl.bufferStorage(GlBackend.ARRAY_BUFFER, capacity, GlBackend.STREAM_DRAW);
            at = 0;
            orphans++;
        }
        if (mapped) {
            gl.bufferWriteUnsynchronized(GlBackend.ARRAY_BUFFER, at, data);
        } else {
            gl.bufferSubData(GlBackend.ARRAY_BUFFER, at, data);
        }
        offset = at + size;
        return at;
    }

    /**
     * Returns the buffer the last upload went to.
     *
     * @return the buffer name, 0 before the first upload
     */
    public int buffer() {
        return buffers[current];
    }

    /** Fences this frame's buffer and moves to the next, waiting until the GPU has finished with it. */
    public void endFrame() {
        if (buffers[current] != 0 && offset > 0) {
            if (fences[current] != 0) {
                gl.deleteFence(fences[current]);
            }
            fences[current] = gl.fence();
        }
        current = (current + 1) % RING;
        offset = 0;
        if (fences[current] != 0) {
            gl.waitFence(fences[current], FENCE_TIMEOUT_NANOS);
            gl.deleteFence(fences[current]);
            fences[current] = 0;
        }
    }

    /**
     * Returns how often a frame outgrew its buffer and orphaned it.
     *
     * @return the count since creation
     */
    public int orphans() {
        return orphans;
    }

    /** Deletes the buffers and fences. */
    @Override
    public void close() {
        for (int i = 0; i < RING; i++) {
            if (fences[i] != 0) {
                gl.deleteFence(fences[i]);
                fences[i] = 0;
            }
            if (buffers[i] != 0) {
                gl.deleteBuffer(buffers[i]);
                buffers[i] = 0;
            }
        }
    }
}
