/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import static lol.trq.helios.gl.GlBackend.ALPHA_TEST;
import static lol.trq.helios.gl.GlBackend.BLEND;
import static lol.trq.helios.gl.GlBackend.CULL_FACE;
import static lol.trq.helios.gl.GlBackend.CURRENT_PROGRAM;
import static lol.trq.helios.gl.GlBackend.DEPTH_TEST;
import static lol.trq.helios.gl.GlBackend.DEPTH_WRITEMASK;
import static lol.trq.helios.gl.GlBackend.FRAMEBUFFER_BINDING;
import static lol.trq.helios.gl.GlBackend.ONE;
import static lol.trq.helios.gl.GlBackend.ONE_MINUS_SRC_ALPHA;
import static lol.trq.helios.gl.GlBackend.SRC_ALPHA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import lol.trq.helios.FakeGl;
import org.junit.jupiter.api.Test;

class StateGuardTest {
    private final FakeGl gl = new FakeGl();

    @Test
    void restoresEveryTouchedSlot() {
        gl.enabled.add(ALPHA_TEST);
        gl.enabled.add(CULL_FACE);
        gl.enabled.add(DEPTH_TEST);
        gl.ints.put(DEPTH_WRITEMASK, 1);
        gl.ints.put(CURRENT_PROGRAM, 7);
        gl.ints.put(FRAMEBUFFER_BINDING, 3);
        int[] blend = gl.blend.clone();
        float[] color = {0.5f, 0.25f, 1f, 0.75f};
        System.arraycopy(color, 0, gl.color, 0, 4);
        int[] viewport = gl.viewport.clone();

        try (StateGuard g = StateGuard.begin(gl, gl)) {
            g.blend(true)
                    .blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)
                    .alphaTest(false)
                    .cull(false)
                    .depthTest(false)
                    .depthMask(false)
                    .texture2D(true)
                    .color(1, 0, 0, 1)
                    .program(42)
                    .framebuffer(9)
                    .viewport(0, 0, 10, 10);
            assertTrue(gl.isEnabled(BLEND));
            assertEquals(42, gl.getInteger(CURRENT_PROGRAM));
        }

        assertFalse(gl.isEnabled(BLEND));
        assertTrue(gl.isEnabled(ALPHA_TEST));
        assertTrue(gl.isEnabled(CULL_FACE));
        assertTrue(gl.isEnabled(DEPTH_TEST));
        assertFalse(gl.isEnabled(lol.trq.helios.gl.GlBackend.TEXTURE_2D));
        assertEquals(1, gl.getInteger(DEPTH_WRITEMASK));
        assertArrayEquals(blend, gl.blend);
        assertArrayEquals(color, gl.color);
        assertArrayEquals(viewport, gl.viewport);
        assertEquals(7, gl.getInteger(CURRENT_PROGRAM));
        assertEquals(3, gl.getInteger(FRAMEBUFFER_BINDING));
    }

    @Test
    void savesOnlyOnFirstTouchAndRestoresInReverse() {
        StateGuard g = StateGuard.begin(gl, gl);
        g.blend(true).program(5).blend(false).program(6);
        assertEquals(List.of(StateGuard.Slot.BLEND, StateGuard.Slot.PROGRAM), g.touched());
        gl.calls.clear();
        g.close();
        assertEquals(List.of("useProgram 0", "disable " + BLEND), gl.calls);
    }

    @Test
    void untouchedStateIsNotWritten() {
        StateGuard g = StateGuard.begin(gl, gl);
        g.close();
        assertTrue(gl.calls.isEmpty());
    }

    @Test
    void closeTwiceIsHarmlessAndUseAfterCloseThrows() {
        StateGuard g = StateGuard.begin(gl, gl);
        g.blend(true);
        g.close();
        gl.calls.clear();
        g.close();
        assertTrue(gl.calls.isEmpty());
        assertThrows(IllegalStateException.class, () -> g.blend(true));
    }

    @Test
    void nestedGuardsUnwindToOuterState() {
        try (StateGuard outer = StateGuard.begin(gl, gl)) {
            outer.program(1);
            try (StateGuard inner = StateGuard.begin(gl, gl)) {
                inner.program(2);
            }
            assertEquals(1, gl.getInteger(CURRENT_PROGRAM));
        }
        assertEquals(0, gl.getInteger(CURRENT_PROGRAM));
    }
}
