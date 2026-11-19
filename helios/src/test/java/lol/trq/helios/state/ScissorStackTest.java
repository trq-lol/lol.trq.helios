/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.trq.helios.FakeGl;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.GlBackend;
import org.junit.jupiter.api.Test;

class ScissorStackTest {
    private final FakeGl gl = new FakeGl();
    private final ScissorStack stack = new ScissorStack(gl, gl, new SurfaceSize() {
        @Override
        public int width() {
            return 1280;
        }

        @Override
        public int height() {
            return 720;
        }
    });

    @Test
    void convertsTopLeftUnitsToBottomLeftPixelsRoundingOutward() {
        assertArrayEquals(new int[] {30, 720 - 90, 60, 60}, ScissorStack.toPixels(10, 10, 20, 20, 1 / 3f, 720));
        assertArrayEquals(new int[] {1, 720 - 4, 3, 3}, ScissorStack.toPixels(0.5f, 0.5f, 1.2f, 1.2f, 0.5f, 720));
    }

    @Test
    void nestedClipsIntersectAndUnwind() {
        gl.scissorBox[2] = 99;
        stack.push(0, 0, 100, 100, 1);
        stack.push(50, 50, 100, 100, 1);
        assertArrayEquals(new int[] {50, 620, 50, 50}, gl.scissorBox);
        stack.pop();
        assertArrayEquals(new int[] {0, 620, 100, 100}, gl.scissorBox);
        assertTrue(gl.isEnabled(GlBackend.SCISSOR_TEST));
        stack.pop();
        assertFalse(gl.isEnabled(GlBackend.SCISSOR_TEST));
        assertEquals(99, gl.scissorBox[2]);
        assertEquals(0, stack.depth());
    }

    @Test
    void disjointClipIsEmpty() {
        stack.push(0, 0, 10, 10, 1);
        stack.push(20, 20, 10, 10, 1);
        assertEquals(0, gl.scissorBox[2]);
        assertEquals(0, gl.scissorBox[3]);
    }

    @Test
    void popOnEmptyThrows() {
        assertThrows(IllegalStateException.class, stack::pop);
    }
}
