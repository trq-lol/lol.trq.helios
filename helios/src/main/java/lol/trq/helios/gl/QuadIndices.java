/* SPDX-License-Identifier: MIT */
package lol.trq.helios.gl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Index data that draws quads of four vertices as two triangles each, 0-1-2 and 2-3-0. */
public final class QuadIndices {
    private QuadIndices() {}

    /**
     * Returns the smallest index type that can address a number of quads.
     *
     * @param quads the quad count
     * @return {@link GlBackend#UNSIGNED_SHORT} or {@link GlBackend#UNSIGNED_INT}
     */
    public static int type(int quads) {
        return (long) quads * 4 <= 0x10000 ? GlBackend.UNSIGNED_SHORT : GlBackend.UNSIGNED_INT;
    }

    /**
     * Builds the indices for a number of quads.
     *
     * @param quads the quad count
     * @param type {@link GlBackend#UNSIGNED_SHORT} or {@link GlBackend#UNSIGNED_INT}
     * @return a flipped direct buffer of {@code quads * 6} indices
     */
    public static ByteBuffer create(int quads, int type) {
        boolean shorts = type == GlBackend.UNSIGNED_SHORT;
        ByteBuffer b = ByteBuffer.allocateDirect(quads * 6 * (shorts ? 2 : 4)).order(ByteOrder.nativeOrder());
        for (int q = 0; q < quads; q++) {
            int v = q * 4;
            put(b, shorts, v);
            put(b, shorts, v + 1);
            put(b, shorts, v + 2);
            put(b, shorts, v + 2);
            put(b, shorts, v + 3);
            put(b, shorts, v);
        }
        return b.flip();
    }

    private static void put(ByteBuffer b, boolean shorts, int i) {
        if (shorts) {
            b.putShort((short) i);
        } else {
            b.putInt(i);
        }
    }
}
