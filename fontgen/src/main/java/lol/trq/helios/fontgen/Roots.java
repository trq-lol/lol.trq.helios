/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

/** Real roots of quadratic and cubic equations, as msdfgen solves them. */
final class Roots {
    private static final double TOO_LARGE_RATIO = 1e12;

    private Roots() {}

    /**
     * Solves {@code a x^2 + b x + c = 0}.
     *
     * @param out receives up to two roots
     * @return the root count, or -1 when every x is a solution
     */
    static int quadratic(double[] out, double a, double b, double c) {
        if (a == 0 || Math.abs(b) > TOO_LARGE_RATIO * Math.abs(a)) {
            if (b == 0) {
                return c == 0 ? -1 : 0;
            }
            out[0] = -c / b;
            return 1;
        }
        double dscr = b * b - 4 * a * c;
        if (dscr > 0) {
            dscr = Math.sqrt(dscr);
            out[0] = (-b + dscr) / (2 * a);
            out[1] = (-b - dscr) / (2 * a);
            return 2;
        } else if (dscr == 0) {
            out[0] = -b / (2 * a);
            return 1;
        }
        return 0;
    }

    private static int cubicNormed(double[] out, double a, double b, double c) {
        double a2 = a * a;
        double q = (a2 - 3 * b) / 9;
        double r = (a * (2 * a2 - 9 * b) + 27 * c) / 54;
        double r2 = r * r;
        double q3 = q * q * q;
        a /= 3;
        if (r2 < q3) {
            double t = r / Math.sqrt(q3);
            t = Math.max(-1, Math.min(1, t));
            t = Math.acos(t);
            q = -2 * Math.sqrt(q);
            out[0] = q * Math.cos(t / 3) - a;
            out[1] = q * Math.cos((t + 2 * Math.PI) / 3) - a;
            out[2] = q * Math.cos((t - 2 * Math.PI) / 3) - a;
            return 3;
        }
        double u = (r < 0 ? 1 : -1) * Math.pow(Math.abs(r) + Math.sqrt(r2 - q3), 1 / 3.0);
        double v = u == 0 ? 0 : q / u;
        out[0] = (u + v) - a;
        if (u == v || Math.abs(u - v) < 1e-12 * Math.abs(u + v)) {
            out[1] = -0.5 * (u + v) - a;
            return 2;
        }
        return 1;
    }

    /**
     * Solves {@code a x^3 + b x^2 + c x + d = 0}.
     *
     * @param out receives up to three roots
     * @return the root count, or -1 when every x is a solution
     */
    static int cubic(double[] out, double a, double b, double c, double d) {
        if (a != 0) {
            double bn = b / a;
            if (Math.abs(bn) < 1e6) {
                return cubicNormed(out, bn, c / a, d / a);
            }
        }
        return quadratic(out, b, c, d);
    }
}
