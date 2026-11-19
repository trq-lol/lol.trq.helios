/* SPDX-License-Identifier: MIT */
package lol.trq.helios.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MathTest {
    private static final float EPS = 1e-4f;

    @Test
    void scalarHelpers() {
        assertEquals(5, Maths.clamp(9, 0, 5));
        assertEquals(2.5, Maths.lerp(0, 10, 0.25));
        assertEquals(0.25, Maths.inverseLerp(0, 10, 2.5));
        assertEquals(0, Maths.inverseLerp(3, 3, 7));
        assertEquals(150, Maths.remap(0.5, 0, 1, 100, 200));
        assertEquals(3, Maths.approach(0, 10, 3));
        assertEquals(10, Maths.approach(9, 10, 3));
        assertEquals(-170, Maths.wrapDegrees(190));
        assertEquals(-180, Maths.wrapDegrees(180));
        assertEquals(355, Maths.lerpDegrees(350, 10, 0.25), 1e-9);
        assertEquals(1.24, Maths.round(1.235, 2), 1e-9);
        assertEquals(-1.24, Maths.round(-1.235, 2), 1e-9);
        assertEquals(1.5, Maths.snap(1.4, 0.5));
        assertThrows(IllegalArgumentException.class, () -> Maths.round(1, -1));
    }

    @Test
    void gaussianKernelIsNormalized() {
        float[] k = new float[6];
        Maths.gaussianKernel(2, k);
        double sum = k[0];
        for (int i = 1; i < k.length; i++) {
            sum += 2 * k[i];
            assertTrue(k[i] < k[i - 1]);
        }
        assertEquals(1, sum, 1e-6);
    }

    @Test
    void randomStaysInRange() {
        for (int i = 0; i < 1000; i++) {
            double d = Maths.random(2.0, 3.0);
            assertTrue(d >= 2 && d < 3);
            int n = Maths.random(1, 3);
            assertTrue(n >= 1 && n <= 3);
        }
    }

    @Test
    void multiplyAppliesRightFirst() {
        float[] t = Mat4.translation(1, 0, 0, new float[16]);
        float[] r = Mat4.rotation(90, 0, 0, 1, new float[16]);
        float[] tr = Mat4.multiply(t, r, new float[16]);
        float[] p = Mat4.transform(tr, 1, 0, 0, 1, new float[4]);
        // Rotate (1,0) to (0,1), then translate to (1,1).
        assertArrayEquals(new float[] {1, 1, 0, 1}, p, EPS);
        float[] aliased = Mat4.multiply(t, r, t);
        assertArrayEquals(tr, aliased, EPS);
    }

    @Test
    void inverseUndoes() {
        float[] m = Mat4.multiply(
                Mat4.perspective(70, 16 / 9f, 0.05f, 256, new float[16]),
                Mat4.multiply(
                        Mat4.rotation(30, 1, 2, 3, new float[16]),
                        Mat4.translation(3, -2, 5, new float[16]),
                        new float[16]),
                new float[16]);
        float[] inv = new float[16];
        assertTrue(Mat4.invert(m, inv));
        assertArrayEquals(Mat4.identity(new float[16]), Mat4.multiply(m, inv, new float[16]), 1e-3f);
        assertFalse(Mat4.invert(new float[16], inv));
        assertThrows(IllegalArgumentException.class, () -> Mat4.invert(m, m));
    }

    @Test
    void perspectiveProjectsTheCentreToTheMiddle() {
        Projector p = new Projector()
                .set(
                        Mat4.identity(new float[16]),
                        Mat4.perspective(90, 2, 0.1f, 100, new float[16]),
                        new int[] {0, 0, 800, 400},
                        0.5f);
        float[] out = new float[3];
        assertTrue(p.project(0, 0, -10, out));
        assertEquals(200, out[0], EPS);
        assertEquals(100, out[1], EPS);
        assertTrue(out[2] > 0 && out[2] < 1);
        // 90 degrees vertical at distance 10: y = 10 is the top edge.
        assertTrue(p.project(0, 10, -10, out));
        assertEquals(0, out[1], EPS);
        // Behind the camera.
        assertFalse(p.project(0, 0, 10, out));
    }

    @Test
    void orthoProjectionIsTheIdentityOnScreen() {
        // The GUI projection: ortho(0, w, h, 0), so a GUI point maps to itself in GUI units.
        float guiW = 427;
        float guiH = 240;
        Projector p = new Projector()
                .set(
                        Mat4.translation(0, 0, -2000, new float[16]),
                        Mat4.ortho(0, guiW, guiH, 0, 1000, 3000, new float[16]),
                        new int[] {0, 0, 1281, 720},
                        guiW / 1281f);
        float[] out = new float[3];
        assertTrue(p.project(100, 50, 0, out));
        assertEquals(100, out[0], 1e-2f);
        assertEquals(50, out[1], 1e-2f);
    }

    @Test
    void boxBoundsCoverAllCorners() {
        Projector p = new Projector()
                .set(
                        Mat4.identity(new float[16]),
                        Mat4.perspective(90, 1, 0.1f, 100, new float[16]),
                        new int[] {0, 0, 400, 400},
                        1);
        float[] box = new float[4];
        assertTrue(p.projectBox(-1, -1, -11, 1, 1, -9, box));
        float[] near = new float[3];
        p.project(-1, 1, -9, near);
        assertEquals(near[0], box[0], EPS);
        assertEquals(near[1], box[1], EPS);
        assertEquals(400 - near[0], box[2], EPS);
        assertFalse(p.projectBox(-1, -1, 5, 1, 1, 6, box));
    }
}
