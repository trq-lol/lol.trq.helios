/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lol.trq.helios.FakeGl;
import lol.trq.helios.Helios;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.math.Mat4;
import lol.trq.helios.shader.ShaderLoader;
import org.junit.jupiter.api.Test;

class MeshRendererTest {
    private final FakeGl gl = new FakeGl();
    private final MeshRenderer meshes = new MeshRenderer(gl, gl, new ShaderLoader(gl, Helios.builtinShaders()), 2);

    private static void quad(QuadSink q, int argb) {
        q.vertex(0, 0, 0, argb).vertex(1, 0, 0, argb).vertex(1, 1, 0, argb).vertex(0, 1, 0, argb);
    }

    private long draws() {
        return gl.calls.stream().filter(c -> c.startsWith("drawElements")).count();
    }

    @Test
    void everyShaderIsCoreProfile() {
        long mesh = gl.sources.values().stream()
                .filter(s -> s.contains("a_color"))
                .peek(s -> assertTrue(s.startsWith("#version 410 core\n")))
                .count();
        assertEquals(3, mesh);
    }

    @Test
    void quadsDrawAsIndexedTrianglesOnFlush() {
        quad(meshes.quads(), 0xFFFF0000);
        assertEquals(0, draws());
        meshes.flush();
        assertTrue(gl.calls.contains(
                "drawElements " + GlBackend.TRIANGLES + " 6 " + GlBackend.UNSIGNED_SHORT + " base 0"));
        assertEquals(4 * MeshFormat.COLOR.stride(), gl.streamed.get(0).remaining());
        ByteBuffer b = gl.streamed.get(0).order(ByteOrder.nativeOrder());
        b.position(12);
        assertEquals((byte) 0xFF, b.get());
        assertEquals(0, b.get());
        assertEquals(0, b.get());
        assertEquals((byte) 0xFF, b.get());
    }

    @Test
    void aFullBatchFlushesBeforeTheNextQuad() {
        for (int i = 0; i < 3; i++) {
            quad(meshes.quads(), -1);
        }
        assertEquals(1, draws());
        meshes.flush();
        assertEquals(2, draws());
    }

    @Test
    void switchingSinksKeepsTheOrderWritten() {
        quad(meshes.quads(), -1);
        meshes.lines().line(0, 0, 0, 1, 1, 1, -1, 2);
        quad(meshes.quads(), -1);
        meshes.flush();
        assertEquals(3, draws());
        assertEquals(4 * MeshFormat.LINE.stride(), gl.streamed.get(1).remaining());
    }

    @Test
    void textureAndLightingChangesFlushTexturedQuads() {
        TexturedSink t = meshes.texture(5).textured();
        for (int i = 0; i < 4; i++) {
            t.vertex(i, 0, 0, 0, 0, 0, 1, 0, -1);
        }
        meshes.texture(5);
        assertEquals(0, draws());
        meshes.texture(6);
        assertEquals(1, draws());
        for (int i = 0; i < 4; i++) {
            t.vertex(i, 0, 0, 0, 0, 0, 1, 0, -1);
        }
        meshes.lighting(false);
        assertEquals(2, draws());
    }

    @Test
    void transformsApplyOnTheCpu() {
        float[] m = Mat4.translation(10, 20, 30, new float[16]);
        QuadSink q = meshes.quads().transform(m);
        quad(q, -1);
        q.transform(null);
        meshes.flush();
        ByteBuffer b = gl.streamed.get(0).order(ByteOrder.nativeOrder());
        assertEquals(10, b.getFloat(0));
        assertEquals(20, b.getFloat(4));
        assertEquals(30, b.getFloat(8));
    }

    @Test
    void normalsTurnWithTheTransformAndPackToBytes() {
        float[] m = Mat4.rotation(90, 0, 0, 1, new float[16]);
        TexturedSink t = meshes.texture(1).textured().transform(m);
        for (int i = 0; i < 4; i++) {
            t.vertex(0, 0, 0, 0, 0, 1, 0, 0, -1);
        }
        meshes.flush();
        ByteBuffer b = gl.streamed.get(0);
        assertEquals(0, b.get(20));
        assertEquals(127, b.get(21));
        assertEquals(0, b.get(22));
    }

    @Test
    void flushRestoresTheHostBindings() {
        gl.ints.put(GlBackend.VERTEX_ARRAY_BINDING, 77);
        gl.ints.put(GlBackend.ARRAY_BUFFER_BINDING, 78);
        gl.ints.put(GlBackend.CURRENT_PROGRAM, 79);
        quad(meshes.quads(), -1);
        meshes.flush();
        assertEquals(77, gl.getInteger(GlBackend.VERTEX_ARRAY_BINDING));
        assertEquals(78, gl.getInteger(GlBackend.ARRAY_BUFFER_BINDING));
        assertEquals(79, gl.getInteger(GlBackend.CURRENT_PROGRAM));
    }

    @Test
    void recordedMeshesUploadOnceAndDrawWithoutStreaming() {
        MeshRecorder rec = new MeshRecorder(MeshFormat.COLOR);
        for (int i = 0; i < 100; i++) {
            quad(rec.quads(), -1);
        }
        rec.quads().vertex(0, 0, 0, -1);
        assertEquals(100, rec.quadCount());
        StaticMesh mesh = rec.build(meshes);
        assertEquals(100, mesh.quads());
        assertTrue(gl.calls.contains("bufferData " + 100 * 4 * MeshFormat.COLOR.stride()));
        int streamedBefore = gl.streamed.size();
        meshes.draw(mesh, null);
        meshes.draw(mesh, Mat4.translation(1, 0, 0, new float[16]));
        assertEquals(streamedBefore, gl.streamed.size());
        assertEquals(2, draws());
        mesh.close();
    }

    @Test
    void recordersAcceptOnlyTheirFormat() {
        MeshRecorder rec = new MeshRecorder(MeshFormat.LINE);
        assertThrows(IllegalStateException.class, rec::quads);
        assertThrows(IllegalStateException.class, rec::textured);
        rec.lines().line(0, 0, 0, 1, 0, 0, -1, 1).line(0, 0, 0, 1, 0, 0, -1, 0);
        assertEquals(1, rec.quadCount());
    }

    @Test
    void closeDeletesEverything() {
        quad(meshes.quads(), -1);
        meshes.lines().line(0, 0, 0, 1, 0, 0, -1, 1);
        meshes.flush();
        meshes.close();
        assertTrue(gl.liveVertexArrays.isEmpty());
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void aTriangleIsAQuadRepeatingItsLastVertex() {
        meshes.quads().triangle(0, 0, 0, 0xFFFF0000, 1, 0, 0, 0xFF00FF00, 0, 1, 0, 0xFF0000FF);
        meshes.flush();
        ByteBuffer b = gl.streamed.get(0).order(ByteOrder.nativeOrder());
        assertEquals(4 * MeshFormat.COLOR.stride(), b.remaining());
        int stride = MeshFormat.COLOR.stride();
        for (int i = 0; i < stride; i++) {
            assertEquals(b.get(2 * stride + i), b.get(3 * stride + i), "byte " + i);
        }
        assertEquals(1, b.getFloat(stride));
        assertTrue(gl.calls.contains(
                "drawElements " + GlBackend.TRIANGLES + " 6 " + GlBackend.UNSIGNED_SHORT + " base 0"));
    }

    @Test
    void stripsWriteOneSegmentPerNeighbourPair() {
        float[] square = {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0};
        MeshRecorder open = new MeshRecorder(MeshFormat.LINE);
        open.lines().strip(square, false, -1, 2);
        assertEquals(3, open.quadCount());
        MeshRecorder closed = new MeshRecorder(MeshFormat.LINE);
        closed.lines().strip(square, true, -1, 2);
        assertEquals(4, closed.quadCount());
        MeshRecorder pair = new MeshRecorder(MeshFormat.LINE);
        pair.lines().strip(new float[] {0, 0, 0, 1, 0, 0}, true, -1, 2);
        assertEquals(1, pair.quadCount());
        assertThrows(IllegalArgumentException.class, () -> open.lines().strip(new float[] {0, 0, 0}, false, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> open.lines().strip(new float[] {0, 0, 0, 1}, false, -1, 1));
    }

    @Test
    void closingAStripJoinsTheLastPointToTheFirst() {
        meshes.lines().strip(new float[] {0, 0, 0, 1, 0, 0, 1, 1, 0}, true, -1, 1);
        meshes.flush();
        // The renderer holds two quads, so the third segment is the first of the second upload.
        ByteBuffer b = gl.streamed.get(1).order(ByteOrder.nativeOrder());
        int last = 0;
        assertEquals(1, b.getFloat(last));
        assertEquals(1, b.getFloat(last + 4));
        assertEquals(0, b.getFloat(last + 12));
        assertEquals(0, b.getFloat(last + 16));
    }
}
