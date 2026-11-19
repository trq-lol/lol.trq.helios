/* SPDX-License-Identifier: MIT */
package lol.trq.helios.batch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;
import lol.trq.helios.FakeGl;
import lol.trq.helios.Helios;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderLoader;
import org.junit.jupiter.api.Test;

class ShapeBatchTest {
    private final FakeGl gl = new FakeGl();
    private final ShapeBatch batch = new ShapeBatch(gl, gl, new ShaderLoader(gl, Helios.builtinShaders()), 3);

    /** One decoded vertex. */
    record V(float x, float y, float lx, float ly, float[] shape, float[] params, float u, float v, int[] colors) {}

    static List<V> vertices(ByteBuffer b) {
        b = b.duplicate().order(java.nio.ByteOrder.nativeOrder());
        java.util.ArrayList<V> out = new java.util.ArrayList<>();
        while (b.remaining() >= ShapeBatch.STRIDE) {
            float x = b.getFloat(), y = b.getFloat(), lx = b.getFloat(), ly = b.getFloat();
            float[] shape = {b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat()};
            float[] params = {b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat()};
            float u = b.getFloat(), v = b.getFloat();
            int[] colors = new int[4];
            for (int i = 0; i < 4; i++) {
                int r = b.get() & 0xFF, g = b.get() & 0xFF, bl = b.get() & 0xFF, a = b.get() & 0xFF;
                colors[i] = a << 24 | r << 16 | g << 8 | bl;
            }
            out.add(new V(x, y, lx, ly, shape, params, u, v, colors));
        }
        return out;
    }

    @Test
    void shaderDeclaresEveryAttributeAtItsLocation() {
        String vs = gl.sources.values().stream()
                .filter(s -> s.contains("a_pos"))
                .findFirst()
                .orElseThrow();
        assertTrue(vs.startsWith("#version 410 core\n"));
        for (int i = 0; i < ShapeBatch.ATTRIBUTES.size(); i++) {
            assertTrue(vs.contains("layout(location = " + i + ") in "), ShapeBatch.ATTRIBUTES.get(i));
            assertTrue(vs.matches(
                    "(?s).*layout\\(location = " + i + "\\) in \\w+ " + ShapeBatch.ATTRIBUTES.get(i) + ";.*"));
        }
    }

    @Test
    void drawsIndexedTrianglesFromItsOwnVertexArray() {
        batch.begin(1).rect(0, 0, 1, 1, 0, -1).end();
        assertEquals(1, gl.liveVertexArrays.size());
        assertTrue(gl.calls.contains(
                "drawElements " + GlBackend.TRIANGLES + " 6 " + GlBackend.UNSIGNED_SHORT + " base 0"));
        assertTrue(gl.calls.stream().noneMatch(c -> c.startsWith("drawArrays")));
        assertTrue(gl.calls.contains("uniformMatrix4 " + "u_mvp".length()));
        assertEquals(0, gl.getInteger(GlBackend.VERTEX_ARRAY_BINDING));
        batch.begin(1).rect(0, 0, 1, 1, 0, -1).end();
        assertEquals(1, gl.liveVertexArrays.size());
    }

    @Test
    void framesAppendToOneBufferAndRotateAtFrameEnd() {
        batch.begin(1).rect(0, 0, 1, 1, 0, -1).end();
        batch.begin(1).rect(0, 0, 1, 1, 0, -1).end();
        assertTrue(gl.calls.contains("stream " + 4 * ShapeBatch.STRIDE + " @0"));
        assertTrue(gl.calls.contains("stream " + 4 * ShapeBatch.STRIDE + " @" + 4 * ShapeBatch.STRIDE));
        assertTrue(gl.calls.contains(
                "drawElements " + GlBackend.TRIANGLES + " 6 " + GlBackend.UNSIGNED_SHORT + " base 4"));
        batch.endFrame();
        assertEquals(1, gl.liveFences.size());
        batch.begin(1).rect(0, 0, 1, 1, 0, -1).end();
        assertTrue(gl.calls.get(gl.calls.size() - 1).contains("base 0")
                || gl.calls.stream()
                        .anyMatch(c -> c.equals(
                                "drawElements " + GlBackend.TRIANGLES + " 6 " + GlBackend.UNSIGNED_SHORT + " base 0")));
    }

    @Test
    void rectIsOneQuadGrownByOnePixel() {
        batch.begin(0.5f).rect(10, 20, 100, 40, 8, 0xFF3B82F6).end();
        List<V> v = vertices(gl.streamed.get(0));
        assertEquals(4, v.size());
        assertEquals(9.5f, v.get(0).x());
        assertEquals(19.5f, v.get(0).y());
        assertEquals(110.5f, v.get(2).x());
        assertEquals(60.5f, v.get(2).y());
        assertEquals(-50.5f, v.get(0).lx());
        assertArrayEquals(
                new float[] {50, 20, 8, ShapeBatch.KIND_RECT}, v.get(0).shape());
        for (V vert : v) {
            assertArrayEquals(new int[] {0xFF3B82F6, 0xFF3B82F6, 0xFF3B82F6, 0xFF3B82F6}, vert.colors());
        }
        assertTrue(gl.calls.stream().anyMatch(c -> c.startsWith("drawElements " + GlBackend.TRIANGLES + " 6 ")));
    }

    @Test
    void manyShapesShareOneDrawCallUntilFull() {
        batch.begin(1);
        batch.rect(0, 0, 1, 1, 0, -1).circle(5, 5, 2, Paint.solid(-1)).outline(0, 0, 9, 9, 2, 1, Paint.solid(-1));
        batch.rect(0, 0, 1, 1, 0, -1);
        batch.end();
        assertEquals(2, batch.drawCalls());
        assertTrue(gl.calls.contains("drawElements 4 18 " + GlBackend.UNSIGNED_SHORT + " base 0"));
        assertTrue(gl.calls.contains("drawElements 4 6 " + GlBackend.UNSIGNED_SHORT + " base 12"));
    }

    @Test
    void flushRestoresEveryState() {
        gl.ints.put(GlBackend.VERTEX_ARRAY_BINDING, 2);
        gl.ints.put(GlBackend.CURRENT_PROGRAM, 5);
        gl.ints.put(GlBackend.ARRAY_BUFFER_BINDING, 9);
        batch.begin(1).rect(0, 0, 4, 4, 1, -1).end();
        assertEquals(2, gl.getInteger(GlBackend.VERTEX_ARRAY_BINDING));
        assertEquals(5, gl.getInteger(GlBackend.CURRENT_PROGRAM));
        assertEquals(9, gl.getInteger(GlBackend.ARRAY_BUFFER_BINDING));
        assertFalse(gl.isEnabled(GlBackend.BLEND));
    }

    @Test
    void lineIsARotatedCapsule() {
        batch.begin(1).line(0, 0, 0, 10, 2, true, Paint.solid(-1)).end();
        List<V> v = vertices(gl.streamed.get(0));
        // Half length 5 + cap 1 = 6 along +y; half width 1; radius 1.
        assertArrayEquals(new float[] {6, 1, 1, ShapeBatch.KIND_RECT}, v.get(0).shape());
        // First corner is local (-7, -2): along the axis -7 maps to y = 5 - 7, across it -2 maps to x = +2.
        assertEquals(2f, v.get(0).x(), 1e-5);
        assertEquals(-2f, v.get(0).y(), 1e-5);
    }

    @Test
    void arcCarriesAnglesAndRejectsRadialPaint() {
        batch.begin(1).arc(50, 50, 10, 3, 1f, 2f, Paint.solid(-1)).end();
        V v = vertices(gl.streamed.get(0)).get(0);
        assertArrayEquals(new float[] {10, 3, 0, ShapeBatch.KIND_ARC}, v.shape());
        assertEquals(1f, v.params()[2]);
        assertEquals(2f, v.params()[3]);
        batch.begin(1);
        assertThrows(IllegalArgumentException.class, () -> batch.arc(0, 0, 5, 1, 0, 1, Paint.radial(-1, 0, 0)));
        batch.end();
    }

    @Test
    void radialPaintDefaultsToTheLargerHalfExtent() {
        batch.begin(1)
                .rect(0, 0, 40, 20, 0, Paint.radial(0xFFFFFFFF, 0xFF000000, 0))
                .end();
        V v = vertices(gl.streamed.get(0)).get(0);
        assertEquals(1f, v.params()[1]);
        assertEquals(20f, v.params()[2]);
    }

    @Test
    void shadowQuadIsGrownBySoftness() {
        batch.begin(1).shadow(10, 10, 20, 20, 4, 6, 0x80000000).end();
        V v = vertices(gl.streamed.get(0)).get(0);
        assertEquals(10 - 6 - 1f, v.x());
        assertEquals(6f, v.params()[0]);
        assertEquals((float) ShapeBatch.KIND_SHADOW, v.shape()[3]);
    }

    @Test
    void imagesFlushOnTextureChangeAndBindTheirTexture() {
        batch.begin(1);
        batch.image(11, 0, 0, 10, 10, 0, 0, 0, 1, 1, -1);
        batch.image(11, 20, 0, 10, 10, 0, 0, 0, 1, 1, -1);
        batch.image(12, 40, 0, 10, 10, 0, 0, 0, 1, 1, -1);
        batch.end();
        assertEquals(2, batch.drawCalls());
        assertTrue(gl.calls.contains("bindTexture 11"));
        assertTrue(gl.calls.contains("bindTexture 12"));
        V first = vertices(gl.streamed.get(0)).get(0);
        // Grown by one pixel on a 10 wide image: u extrapolates to -0.1.
        assertEquals(-0.1f, first.u(), 1e-5);
    }

    @Test
    void addingOutsideBeginThrows() {
        assertThrows(IllegalStateException.class, () -> batch.rect(0, 0, 1, 1, 0, -1));
        batch.begin(1);
        assertThrows(IllegalStateException.class, () -> batch.begin(1));
        batch.end();
        assertThrows(IllegalStateException.class, batch::end);
    }

    @Test
    void emptyShapesAreSkipped() {
        batch.begin(1).rect(0, 0, 0, 5, 0, -1).end();
        assertEquals(0, batch.drawCalls());
        assertTrue(gl.streamed.isEmpty());
    }

    @Test
    void closeDeletesBuffer() {
        batch.begin(1).rect(0, 0, 1, 1, 0, -1).end();
        batch.close();
        assertTrue(gl.liveBuffers.isEmpty());
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void shapesWithNonFiniteCoordinatesAreDropped() {
        float nan = Float.NaN;
        float inf = Float.POSITIVE_INFINITY;
        batch.begin(1)
                .rect(nan, 0, 10, 10, 0, -1)
                .rect(0, 0, inf, 10, 0, -1)
                .outline(0, nan, 10, 10, 2, 1, Paint.solid(-1))
                .circle(nan, nan, 4, Paint.solid(-1))
                .arc(nan, 0, 4, 1, 0, 1, Paint.solid(-1))
                .line(0, 0, nan, 10, 1, false, Paint.solid(-1))
                .shadow(0, 0, nan, 10, 0, 2, -1)
                .image(5, 0, 0, nan, 10, 0, 0, 0, 1, 1, -1)
                .glyph(5, nan, 0, 4, 4, 0, 0, 0, 0, 1, 1, -1, 4, 0, 0, 64, 64)
                .glyph(5, 0, 0, 4, 4, nan, 0, 0, 0, 1, 1, -1, 4, 0, 0, 64, 64)
                .end();
        assertTrue(gl.streamed.isEmpty(), "nothing is uploaded for shapes that cannot be placed");
        assertEquals(0, batch.drawCalls());

        batch.begin(1).rect(nan, 0, 10, 10, 0, -1).rect(0, 0, 10, 10, 0, -1).end();
        assertEquals(1, batch.drawCalls());
        assertEquals(4 * ShapeBatch.STRIDE, gl.streamed.get(0).remaining(), "only the finite rect is drawn");
    }
}
