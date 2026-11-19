/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.trq.helios.FakeGl;
import lol.trq.helios.Helios;
import lol.trq.helios.batch.Paint;
import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderLoader;
import org.junit.jupiter.api.Test;

class StencilMaskTest {
    private final FakeGl gl = new FakeGl();
    private final ShapeBatch batch = new ShapeBatch(gl, gl, new ShaderLoader(gl, Helios.builtinShaders(), 120), 16);
    private final StencilMask stencil = new StencilMask(gl, gl, batch);

    @Test
    void maskWritesStencilOnlyThenContentTestsIt() {
        boolean[] seen = new boolean[2];
        stencil.clip(
                () -> {
                    assertFalse(gl.colorWrite[0]);
                    assertEquals(GlBackend.ALWAYS, gl.stencilState[0]);
                    assertEquals(GlBackend.REPLACE, gl.stencilState[5]);
                    batch.begin(1).circle(5, 5, 4, Paint.solid(-1)).end();
                    seen[0] = true;
                },
                true,
                () -> {
                    assertTrue(gl.colorWrite[0]);
                    assertEquals(GlBackend.EQUAL, gl.stencilState[0]);
                    assertEquals(0, gl.stencilState[6]);
                    seen[1] = true;
                });
        assertTrue(seen[0] && seen[1]);
        assertTrue(gl.calls.contains("clearStencil 0"));
        assertTrue(gl.calls.contains("alphaFunc " + GlBackend.GREATER + " 0.5"), gl.calls.toString());
    }

    @Test
    void outsideUsesNotEqual() {
        stencil.clip(() -> {}, false, () -> assertEquals(GlBackend.NOTEQUAL, gl.stencilState[0]));
    }

    @Test
    void everythingIsRestored() {
        int[] before = gl.stencilState.clone();
        stencil.clip(() -> batch.begin(1).rect(0, 0, 4, 4, 0, -1).end(), true, () -> {});
        assertArrayEquals(before, gl.stencilState);
        assertFalse(gl.isEnabled(GlBackend.STENCIL_TEST));
        assertTrue(gl.colorWrite[0] && gl.colorWrite[3]);
        assertFalse(gl.isEnabled(GlBackend.ALPHA_TEST));
        // The batch is back to no cutoff: a later flush does not touch the alpha function.
        gl.calls.clear();
        batch.begin(1).rect(0, 0, 4, 4, 0, -1).end();
        assertTrue(gl.calls.stream().noneMatch(c -> c.startsWith("alphaFunc")));
    }

    @Test
    void noStencilBufferThrows() {
        gl.stencilBits = 0;
        assertThrows(IllegalStateException.class, () -> stencil.clip(() -> {}, true, () -> {}));
    }
}
