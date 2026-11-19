/* SPDX-License-Identifier: MIT */
package lol.trq.helios.sdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SdfTest {
    private static final float EPS = 1e-5f;

    @Test
    void edgesAreZeroInsideNegativeOutsidePositive() {
        assertEquals(0f, Sdf.roundedRect(50, 0, 50, 20, 8), EPS);
        assertEquals(0f, Sdf.roundedRect(0, -20, 50, 20, 8), EPS);
        assertEquals(-20f, Sdf.roundedRect(0, 0, 50, 20, 8), EPS);
        assertEquals(3f, Sdf.roundedRect(53, 0, 50, 20, 8), EPS);
    }

    @Test
    void cornerFollowsTheArc() {
        // Corner circle centre at (42, 12), radius 8.
        float d = 8f / (float) Math.sqrt(2);
        assertEquals(0f, Sdf.roundedRect(42 + d, 12 + d, 50, 20, 8), 1e-4f);
        // The square corner is outside a rounded rect: distance to the arc is sqrt(2)*8 - 8.
        assertEquals((float) (Math.sqrt(2) * 8 - 8), Sdf.roundedRect(50, 20, 50, 20, 8), 1e-4f);
        assertEquals(0f, Sdf.roundedRect(50, 20, 50, 20, 0), EPS);
    }

    @Test
    void radiusIsClampedToHalfTheShortSide() {
        assertEquals(10f, Sdf.clampRadius(99, 50, 10), EPS);
        assertEquals(0f, Sdf.clampRadius(-3, 50, 10), EPS);
        // A fully rounded pill: its end is a semicircle of radius 10.
        assertEquals(0f, Sdf.roundedRect(50, 0, 50, 10, 99), EPS);
        assertEquals(-10f, Sdf.roundedRect(0, 0, 50, 10, 99), EPS);
    }

    @Test
    void coverageRampsOverOnePixelCentredOnTheEdge() {
        assertEquals(0.5f, Sdf.coverage(0f, 1f), EPS);
        assertEquals(1f, Sdf.coverage(-0.5f, 1f), EPS);
        assertEquals(0f, Sdf.coverage(0.5f, 1f), EPS);
        assertEquals(0.75f, Sdf.coverage(-0.25f, 1f), EPS);
        assertEquals(1f, Sdf.coverage(-3f, 1f), EPS);
        assertEquals(0f, Sdf.coverage(3f, 1f), EPS);
        // At GUI scale 2 one pixel is half a unit: the ramp is half a unit wide.
        assertEquals(0f, Sdf.coverage(0.25f, 0.5f), EPS);
        assertEquals(1f, Sdf.coverage(-0.25f, 0.5f), EPS);
    }

    @Test
    void coverageOfAFilledPixelRowSumsToTheCoveredLength() {
        // Rasterize a 1D edge at x = 10.3 on a pixel grid: total coverage equals the covered length.
        float sum = 0;
        for (int px = 0; px < 20; px++) {
            float centre = px + 0.5f;
            sum += Sdf.coverage(centre - 10.3f, 1f);
        }
        assertEquals(10.3f, sum, 1e-4f);
    }

    @Test
    void strokeIsABandInsideTheEdge() {
        float d = Sdf.roundedRect(48, 0, 50, 20, 0);
        assertEquals(-2f, d, EPS);
        assertEquals(-1f, Sdf.stroke(Sdf.roundedRect(49, 0, 50, 20, 0), 2), EPS);
        assertEquals(0f, Sdf.stroke(d, 2), EPS);
        assertEquals(3f, Sdf.stroke(Sdf.roundedRect(45, 0, 50, 20, 0), 2), EPS);
    }

    @Test
    void segmentDistance() {
        assertEquals(3f, Sdf.segment(5, 3, 0, 0, 10, 0), EPS);
        assertEquals(5f, Sdf.segment(-3, 4, 0, 0, 10, 0), EPS);
    }

    @Test
    void arcInsideSweepIsTheRing() {
        float half = (float) Math.PI;
        // Ring from radius 8 to 10, upper half on screen (angles pi..2pi, y negative).
        assertEquals(-1f, Sdf.arc(0, -9, 10, 2, half, half), 1e-4f);
        assertEquals(0f, Sdf.arc(0, -10, 10, 2, half, half), 1e-4f);
        assertEquals(2f, Sdf.arc(0, -12, 10, 2, half, half), 1e-4f);
    }

    @Test
    void arcOutsideSweepMeasuresToTheCaps() {
        float half = (float) Math.PI;
        // (0, 9) is in the lower half: the nearest cap points are the inner ends (+-8, 0).
        assertEquals((float) Math.hypot(8, 9), Sdf.arc(0, 9, 10, 2, half, half), 1e-3f);
        // Just below the cap at angle 0 (the end of the sweep), inside the ring band.
        assertEquals(0.5f, Sdf.arc(9, 0.5f, 10, 2, half, half), 1e-3f);
    }

    @Test
    void fullSweepIsARing() {
        assertEquals(Sdf.arc(3, 9, 10, 2, 0, Sdf.TAU), Sdf.arc(3, 9, 10, 2, 1, 7), EPS);
        assertEquals(-1f, Sdf.arc(0, 9, 10, 2, 0, Sdf.TAU), 1e-4f);
    }

    @Test
    void shadowAndGlowFalloff() {
        assertEquals(1f, Sdf.shadow(-4, 4), EPS);
        assertEquals(0.5f, Sdf.shadow(0, 4), EPS);
        assertEquals(0f, Sdf.shadow(4, 4), EPS);
        assertEquals(1f, Sdf.glow(0, 3), EPS);
        assertEquals(0f, Sdf.glow(-3, 3), EPS);
        assertEquals(0.5f, Sdf.glow(-1.5f, 3), EPS);
    }
}
