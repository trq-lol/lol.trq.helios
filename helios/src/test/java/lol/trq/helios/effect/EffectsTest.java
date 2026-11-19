/* SPDX-License-Identifier: MIT */
package lol.trq.helios.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import lol.trq.helios.FakeGl;
import lol.trq.helios.Helios;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderProgram;
import org.junit.jupiter.api.Test;

class EffectsTest {
    private final FakeGl gl = new FakeGl();
    private final Helios helios = Helios.create(gl, gl, new SurfaceSize() {
        @Override
        public int width() {
            return 800;
        }

        @Override
        public int height() {
            return 600;
        }
    });

    @Test
    void blurSizesHalveAndNeverReachZero() {
        int[][] s = Passes.blurSizes(800, 3, 3);
        assertArrayEquals(new int[] {800, 3}, s[0]);
        assertArrayEquals(new int[] {400, 1}, s[1]);
        assertArrayEquals(new int[] {100, 1}, s[3]);
        assertThrows(IllegalArgumentException.class, () -> Passes.blurSizes(8, 8, 0));
    }

    @Test
    void blurRunsDownThenUpAndReturnsEverythingButTheResult() {
        int before = gl.calls.size();
        var out = helios.passes().blur(77, 800, 600, 3, 2);
        assertEquals(800, out.width());
        long quads = gl.calls.subList(before, gl.calls.size()).stream()
                .filter(c -> c.equals("drawArrays 4 0 3"))
                .count();
        assertEquals(6, quads);
        assertEquals(1, helios.framebuffers().lentCount());
        helios.framebuffers().release(out);
        assertEquals(0, helios.framebuffers().lentCount());
    }

    @Test
    void bloomCompositesAdditivelyAndRestoresState() {
        gl.ints.put(GlBackend.FRAMEBUFFER_BINDING, 4);
        boolean[] ran = {false};
        helios.effects()
                .bloom(
                        () -> {
                            ran[0] = true;
                            assertTrue(gl.getInteger(GlBackend.FRAMEBUFFER_BINDING) != 4);
                        },
                        2,
                        1,
                        1);
        assertTrue(ran[0]);
        assertEquals(4, gl.getInteger(GlBackend.FRAMEBUFFER_BINDING));
        assertFalse(gl.isEnabled(GlBackend.BLEND));
        assertEquals(0, helios.framebuffers().lentCount());
        assertTrue(gl.calls.contains("clear"));
    }

    @Test
    void blurBehindCapturesTheScreenAndDrawsOneImage() {
        helios.effects().blurBehind(10, 10, 100, 50, 8, 0.5f, 2, 1, -1);
        assertTrue(gl.calls.stream().anyMatch(c -> c.startsWith("copy 800x600")));
        assertTrue(gl.calls.stream().anyMatch(c -> c.startsWith("drawElements 4 6 ")));
        assertEquals(0, helios.framebuffers().lentCount());
    }

    @Test
    void postChainPingPongsAndReplacesTheScreen() {
        ShaderProgram invert = helios.shaders().load(Passes.FULLSCREEN_VERTEX, "effect/composite.fsh", Map.of());
        int[] runs = {0};
        helios.postChain()
                .then(invert, p -> runs[0]++)
                .then(invert, p -> runs[0]++)
                .apply();
        assertEquals(2, runs[0]);
        assertEquals(0, helios.framebuffers().lentCount());
        assertEquals(1, helios.framebuffers().createdCount() - 1);
    }

    @Test
    void emptyPostChainDoesNothing() {
        int before = gl.calls.size();
        helios.postChain().apply();
        assertEquals(before, gl.calls.size());
    }
}
