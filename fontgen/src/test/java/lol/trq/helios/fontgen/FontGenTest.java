/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Path2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FontGenTest {
    private static Font inter;

    @BeforeAll
    static void load() throws Exception {
        Path ttf = Path.of(System.getProperty("helios.fontDir"), "inter", "Inter-Regular.ttf");
        try (var in = Files.newInputStream(ttf)) {
            inter = Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    @Test
    void cubicRoots() {
        double[] r = new double[3];
        // (x - 1)(x - 2)(x - 3)
        assertEquals(3, Roots.cubic(r, 1, -6, 11, -6));
        java.util.Arrays.sort(r);
        assertEquals(1, r[0], 1e-9);
        assertEquals(2, r[1], 1e-9);
        assertEquals(3, r[2], 1e-9);
        assertEquals(1, Roots.cubic(r, 0, 0, 2, -4));
        assertEquals(2, r[0], 1e-12);
    }

    @Test
    void lineDistanceIsSignedPerpendicular() {
        var e = new EdgeSegment.Linear(new double[] {0, 0}, new double[] {10, 0}, EdgeSegment.WHITE);
        double[] d = new double[3];
        e.signedDistance(5, 3, d);
        assertEquals(3, Math.abs(d[0]), 1e-12);
        double side = d[0];
        e.signedDistance(5, -3, d);
        assertEquals(-side, d[0], 1e-12);
        e.signedDistance(13, 4, d);
        assertEquals(5, Math.abs(d[0]), 1e-12);
        assertTrue(d[2] > 1);
    }

    @Test
    void quadraticDistanceMatchesSampling() {
        var q = new EdgeSegment.Quadratic(
                new double[] {0, 0}, new double[] {5, 10}, new double[] {10, 0}, EdgeSegment.WHITE);
        double[] d = new double[3];
        for (double[] p : new double[][] {{5, 2}, {5, 8}, {1, 3}, {9, -1}, {2, 7}}) {
            q.signedDistance(p[0], p[1], d);
            double best = Double.MAX_VALUE;
            for (int i = 0; i <= 100000; i++) {
                double[] c = q.point(i / 100000.0);
                best = Math.min(best, Math.hypot(c[0] - p[0], c[1] - p[1]));
            }
            assertEquals(best, Math.abs(d[0]), 1e-4);
        }
    }

    @Test
    void squareIsOrientedPositiveInsideWithFourCornersColoured() {
        Path2D.Double sq = new Path2D.Double();
        sq.moveTo(0, 0);
        sq.lineTo(10, 0);
        sq.lineTo(10, 10);
        sq.lineTo(0, 10);
        sq.closePath();
        GlyphShape shape = GlyphShape.of(sq);
        EdgeColoring.simple(shape, 3.0, 0);
        float[] f = MsdfGenerator.generate(shape, 1, 1, 4.5, 4.5, 4);
        assertTrue(f[0] > 0.5f && f[1] > 0.5f && f[2] > 0.5f);
        float[] out = MsdfGenerator.generate(shape, 1, 1, 11.5, 4.5, 4);
        float m = MsdfGenerator.median(out[0], out[1], out[2]);
        assertTrue(m < 0.5f, "outside median " + m);
        // Adjacent edges at each corner share exactly one channel.
        var edges = shape.contours.get(0);
        for (int i = 0; i < edges.size(); i++) {
            int a = edges.get(i).color;
            int b = edges.get((i + 1) % edges.size()).color;
            assertEquals(1, Integer.bitCount(a & b), "edges " + i + " and next share " + (a & b));
        }
    }

    @Test
    void reconstructedGlyphsMatchTheRasterizer() {
        float em = 40;
        float range = 4;
        AtlasBuilder.Atlas atlas = AtlasBuilder.build(
                inter, "inter", em, range, "AgM@&%Rk0".codePoints().toArray());
        Font sized = inter.deriveFont(em * 4);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        for (AtlasBuilder.Glyph g : atlas.glyphs()) {
            // Render the glyph 4x larger than the field: reconstruct from the MSDF with bilinear sampling and a
            // one-pixel ramp, and compare with AWT's outline fill sampled at pixel centres.
            Shape outline = sized.createGlyphVector(frc, Character.toChars(g.codepoint()))
                    .getGlyphOutline(0);
            int scale = 4;
            int w = g.atlasWidth() * scale;
            int h = g.atlasHeight() * scale;
            int mismatch = 0;
            int inside = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double u = (x + 0.5) / scale;
                    double v = (y + 0.5) / scale;
                    float sd = sample(atlas, g, u, v) - 0.5f;
                    float spr = range * scale;
                    boolean msdfInside = spr * sd > 0;
                    double sx = (g.planeLeft() * em + u) * scale;
                    double sy = (g.planeTop() * em + v) * scale;
                    boolean truth = outline.contains(sx, sy);
                    if (truth) {
                        inside++;
                    }
                    // Ignore pixels within half a pixel of the edge, where either answer is right.
                    if (msdfInside != truth && Math.abs(spr * sd) > 0.5f) {
                        mismatch++;
                    }
                }
            }
            double rate = mismatch / (double) Math.max(inside, 1);
            assertTrue(rate < 0.002, "glyph " + new String(Character.toChars(g.codepoint())) + " mismatch " + rate);
        }
    }

    @Test
    void atlasRoundTripsThroughTheFileFormat() throws Exception {
        AtlasBuilder.Atlas atlas =
                AtlasBuilder.build(inter, "inter", 32, 4, "AVa b".codePoints().toArray());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtlasBuilder.write(atlas, bytes);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(AtlasBuilder.MAGIC, in.readInt());
        assertEquals(AtlasBuilder.VERSION, in.readInt());
        assertEquals("inter", in.readUTF());
        assertTrue(atlas.kerning().stream().anyMatch(k -> k.left() == 'A' && k.right() == 'V' && k.amount() < 0));
        assertTrue(
                atlas.glyphs().stream().anyMatch(g -> g.codepoint() == ' ' && g.atlasWidth() == 0 && g.advance() > 0));
    }

    /** Bilinear median of the atlas around a glyph-local texel position. */
    private static float sample(AtlasBuilder.Atlas atlas, AtlasBuilder.Glyph g, double u, double v) {
        double fx = g.atlasX() + u - 0.5;
        double fy = g.atlasY() + v - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double tx = fx - x0;
        double ty = fy - y0;
        float[] c = new float[3];
        for (int ch = 0; ch < 3; ch++) {
            double a = texel(atlas, x0, y0, ch) * (1 - tx) + texel(atlas, x0 + 1, y0, ch) * tx;
            double b = texel(atlas, x0, y0 + 1, ch) * (1 - tx) + texel(atlas, x0 + 1, y0 + 1, ch) * tx;
            c[ch] = (float) (a * (1 - ty) + b * ty);
        }
        return MsdfGenerator.median(c[0], c[1], c[2]);
    }

    private static float texel(AtlasBuilder.Atlas atlas, int x, int y, int ch) {
        x = Math.max(0, Math.min(atlas.width() - 1, x));
        y = Math.max(0, Math.min(atlas.height() - 1, y));
        return (atlas.rgb()[(y * atlas.width() + x) * 3 + ch] & 0xFF) / 255f;
    }
}
