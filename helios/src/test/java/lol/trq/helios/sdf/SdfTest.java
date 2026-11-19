/* SPDX-License-Identifier: MIT */
package lol.trq.helios.sdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.trq.helios.FakeGl;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderLoader;
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
    void rendererGrowsTheQuadByOnePixelAndRestoresState() {
        FakeGl gl = new FakeGl();
        gl.enabled.add(GlBackend.ALPHA_TEST);
        gl.ints.put(GlBackend.CURRENT_PROGRAM, 11);
        ShaderLoader loader = new ShaderLoader(gl, lol.trq.helios.Helios.builtinShaders(), 120);
        RoundedRectRenderer r = new RoundedRectRenderer(gl, gl, loader);
        r.fill(10, 20, 100, 40, 8, 0xFF3B82F6, 0.5f);
        assertTrue(gl.calls.contains("quad 9.5,19.5,110.5,60.5 uv -50.5,-20.5,50.5,20.5"), gl.calls.toString());
        assertTrue(gl.isEnabled(GlBackend.ALPHA_TEST));
        assertTrue(!gl.isEnabled(GlBackend.BLEND));
        assertEquals(11, gl.getInteger(GlBackend.CURRENT_PROGRAM));
        assertEquals(
                "useProgram 11",
                gl.calls.stream()
                        .filter(c -> c.startsWith("useProgram"))
                        .reduce((a, b) -> b)
                        .orElseThrow());
    }

    @Test
    void emptyRectDrawsNothing() {
        FakeGl gl = new FakeGl();
        RoundedRectRenderer r =
                new RoundedRectRenderer(gl, gl, new ShaderLoader(gl, lol.trq.helios.Helios.builtinShaders(), 120));
        gl.calls.clear();
        r.fill(0, 0, 0, 10, 2, -1, 1);
        assertTrue(gl.calls.isEmpty());
    }
}
