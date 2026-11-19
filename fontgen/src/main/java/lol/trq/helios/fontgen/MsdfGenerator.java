/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

/**
 * Generates a multi-channel signed distance field for one glyph, then removes channel clashes. Port of msdfgen's
 * legacy {@code generateMSDF} and {@code msdfErrorCorrection}.
 *
 * <p>Output values are distances mapped to {@code [0, 1]}: 0.5 on the edge, above inside, with {@code range} shape
 * units spanning the full interval.
 */
final class MsdfGenerator {
    private MsdfGenerator() {}

    /**
     * Fills an RGB float field.
     *
     * @param shape the coloured glyph
     * @param width field width in texels
     * @param height field height in texels
     * @param originX shape x of the field's left edge
     * @param originY shape y of the field's top edge
     * @param range the distance in shape units that spans the output range
     * @return {@code width * height * 3} values, rows top to bottom
     */
    static float[] generate(GlyphShape shape, int width, int height, double originX, double originY, double range) {
        float[] out = new float[width * height * 3];
        double[] d = new double[3];
        double[][] best = new double[3][3];
        EdgeSegment[] near = new EdgeSegment[3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double px = originX + x + 0.5;
                double py = originY + y + 0.5;
                for (int ch = 0; ch < 3; ch++) {
                    best[ch][0] = -Double.MAX_VALUE;
                    best[ch][1] = 1;
                    near[ch] = null;
                }
                for (var contour : shape.contours) {
                    for (EdgeSegment e : contour) {
                        e.signedDistance(px, py, d);
                        for (int ch = 0; ch < 3; ch++) {
                            if ((e.color & (1 << ch)) != 0 && less(d, best[ch])) {
                                best[ch][0] = d[0];
                                best[ch][1] = d[1];
                                best[ch][2] = d[2];
                                near[ch] = e;
                            }
                        }
                    }
                }
                int i = (y * width + x) * 3;
                for (int ch = 0; ch < 3; ch++) {
                    if (near[ch] != null) {
                        near[ch].toPseudoDistance(best[ch], px, py);
                    }
                    out[i + ch] = (float) (best[ch][0] / range + 0.5);
                }
            }
        }
        return out;
    }

    private static boolean less(double[] a, double[] b) {
        double fa = Math.abs(a[0]);
        double fb = Math.abs(b[0]);
        return fa < fb || (fa == fb && a[1] < b[1]);
    }

    /**
     * Equalizes texels whose channels would interpolate into a false edge against a neighbour.
     *
     * @param field the field from {@link #generate}
     * @param width field width
     * @param height field height
     * @param threshold the clash threshold in output units, msdfgen uses {@code 1.001 / pxRange}
     */
    static void correctErrors(float[] field, int width, int height, double threshold) {
        boolean[] clash = new boolean[width * height];
        double diag = threshold * Math.sqrt(2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if ((x > 0 && detect(field, i, i - 1, threshold))
                        || (x < width - 1 && detect(field, i, i + 1, threshold))
                        || (y > 0 && detect(field, i, i - width, threshold))
                        || (y < height - 1 && detect(field, i, i + width, threshold))) {
                    clash[i] = true;
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (clash[i]) {
                    continue;
                }
                if ((x > 0 && y > 0 && detect(field, i, i - width - 1, diag))
                        || (x < width - 1 && y > 0 && detect(field, i, i - width + 1, diag))
                        || (x > 0 && y < height - 1 && detect(field, i, i + width - 1, diag))
                        || (x < width - 1 && y < height - 1 && detect(field, i, i + width + 1, diag))) {
                    clash[i] = true;
                }
            }
        }
        for (int i = 0; i < clash.length; i++) {
            if (clash[i]) {
                float m = median(field[i * 3], field[i * 3 + 1], field[i * 3 + 2]);
                field[i * 3] = field[i * 3 + 1] = field[i * 3 + 2] = m;
            }
        }
    }

    private static boolean detect(float[] f, int ia, int ib, double threshold) {
        float a0 = f[ia * 3];
        float a1 = f[ia * 3 + 1];
        float a2 = f[ia * 3 + 2];
        float b0 = f[ib * 3];
        float b1 = f[ib * 3 + 1];
        float b2 = f[ib * 3 + 2];
        float t;
        if (Math.abs(b0 - a0) < Math.abs(b1 - a1)) {
            t = a0;
            a0 = a1;
            a1 = t;
            t = b0;
            b0 = b1;
            b1 = t;
        }
        if (Math.abs(b1 - a1) < Math.abs(b2 - a2)) {
            t = a1;
            a1 = a2;
            a2 = t;
            t = b1;
            b1 = b2;
            b2 = t;
            if (Math.abs(b0 - a0) < Math.abs(b1 - a1)) {
                t = a0;
                a0 = a1;
                a1 = t;
                t = b0;
                b0 = b1;
                b1 = t;
            }
        }
        return Math.abs(b1 - a1) >= threshold && !(b0 == b1 && b0 == b2) && Math.abs(a2 - 0.5f) >= Math.abs(b2 - 0.5f);
    }

    static float median(float a, float b, float c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }
}
