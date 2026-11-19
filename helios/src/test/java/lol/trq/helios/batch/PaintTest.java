/* SPDX-License-Identifier: MIT */
package lol.trq.helios.batch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaintTest {
    private static int[] corners(Paint p, float hw, float hh) {
        int[] out = new int[4];
        p.corners(hw, hh, out);
        return out;
    }

    @Test
    void lerpPerChannel() {
        assertEquals(0x80808080, Paint.lerp(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFF0000FF, Paint.lerp(0xFF0000FF, 0x00FF0000, 0f));
        assertEquals(0x00FF0000, Paint.lerp(0xFF0000FF, 0x00FF0000, 2f));
    }

    @Test
    void horizontalAndVertical() {
        assertArrayEquals(new int[] {1, 2, 2, 1}, corners(Paint.horizontal(1, 2), 10, 5));
        assertArrayEquals(new int[] {1, 1, 2, 2}, corners(Paint.vertical(1, 2), 10, 5));
    }

    @Test
    void linearZeroDegreesMatchesHorizontal() {
        int a = 0xFF000000;
        int b = 0xFFFFFFFF;
        assertArrayEquals(corners(Paint.horizontal(a, b), 10, 5), corners(Paint.linear(0, a, b), 10, 5));
        assertArrayEquals(corners(Paint.vertical(a, b), 10, 5), corners(Paint.linear(90, a, b), 10, 5));
    }

    @Test
    void linearDiagonalHasMidpointsAtTheOtherCorners() {
        int[] c = corners(Paint.linear(45, 0xFF000000, 0xFFFEFEFE), 10, 10);
        assertEquals(0xFF000000, c[0]);
        assertEquals(0xFFFEFEFE, c[2]);
        assertEquals(0xFF7F7F7F, c[1]);
        assertEquals(0xFF7F7F7F, c[3]);
    }
}
