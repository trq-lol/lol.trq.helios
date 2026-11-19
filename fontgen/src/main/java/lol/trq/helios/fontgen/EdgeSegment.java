/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

/**
 * One edge of a glyph outline: a line or a quadratic Bézier. Ported from msdfgen (MIT, Viktor Chlumský), keeping its
 * distance and pseudo-distance definitions so the generated fields behave like msdfgen's.
 *
 * <p>Points are {@code double[2]}. Colours are channel bit masks: red 1, green 2, blue 4.
 */
abstract sealed class EdgeSegment permits EdgeSegment.Linear, EdgeSegment.Quadratic {
    static final int BLACK = 0;
    static final int RED = 1;
    static final int GREEN = 2;
    static final int YELLOW = 3;
    static final int BLUE = 4;
    static final int MAGENTA = 5;
    static final int CYAN = 6;
    static final int WHITE = 7;

    int color = WHITE;

    abstract double[] point(double t);

    abstract double[] direction(double t);

    /**
     * Signed distance from a point to this edge.
     *
     * @param px point x
     * @param py point y
     * @param out receives {@code {distance, dot, param}}: the signed distance, the tie-breaking dot product and the
     *     curve parameter of the nearest point (outside {@code [0, 1]} when an endpoint is nearest)
     */
    abstract void signedDistance(double px, double py, double[] out);

    abstract EdgeSegment[] splitInThirds();

    abstract EdgeSegment reversed();

    /** Converts a distance whose nearest point is an endpoint into a pseudo-distance to the extended edge. */
    void toPseudoDistance(double[] dist, double px, double py) {
        double param = dist[2];
        if (param < 0) {
            double[] dir = normalize(direction(0));
            double[] a = point(0);
            double aqx = px - a[0];
            double aqy = py - a[1];
            if (aqx * dir[0] + aqy * dir[1] < 0) {
                double pseudo = cross(aqx, aqy, dir[0], dir[1]);
                if (Math.abs(pseudo) <= Math.abs(dist[0])) {
                    dist[0] = pseudo;
                    dist[1] = 0;
                }
            }
        } else if (param > 1) {
            double[] dir = normalize(direction(1));
            double[] b = point(1);
            double bqx = px - b[0];
            double bqy = py - b[1];
            if (bqx * dir[0] + bqy * dir[1] > 0) {
                double pseudo = cross(bqx, bqy, dir[0], dir[1]);
                if (Math.abs(pseudo) <= Math.abs(dist[0])) {
                    dist[0] = pseudo;
                    dist[1] = 0;
                }
            }
        }
    }

    static double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    static double[] normalize(double[] v) {
        double len = Math.hypot(v[0], v[1]);
        return len == 0 ? new double[] {0, 1} : new double[] {v[0] / len, v[1] / len};
    }

    static double nonZeroSign(double v) {
        return v > 0 ? 1 : -1;
    }

    /** A straight edge. */
    static final class Linear extends EdgeSegment {
        final double[] p0;
        final double[] p1;

        Linear(double[] p0, double[] p1, int color) {
            this.p0 = p0;
            this.p1 = p1;
            this.color = color;
        }

        @Override
        double[] point(double t) {
            return new double[] {p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t};
        }

        @Override
        double[] direction(double t) {
            return new double[] {p1[0] - p0[0], p1[1] - p0[1]};
        }

        @Override
        void signedDistance(double px, double py, double[] out) {
            double aqx = px - p0[0];
            double aqy = py - p0[1];
            double abx = p1[0] - p0[0];
            double aby = p1[1] - p0[1];
            double ab2 = abx * abx + aby * aby;
            double param = ab2 == 0 ? 0 : (aqx * abx + aqy * aby) / ab2;
            double[] e = param > 0.5 ? p1 : p0;
            double eqx = e[0] - px;
            double eqy = e[1] - py;
            double endpoint = Math.hypot(eqx, eqy);
            out[2] = param;
            if (param > 0 && param < 1) {
                double len = Math.sqrt(ab2);
                // orthonormal(ab, polarity false) = (aby, -abx) / len
                double ortho = (aby * aqx - abx * aqy) / len;
                if (Math.abs(ortho) < endpoint) {
                    out[0] = ortho;
                    out[1] = 0;
                    return;
                }
            }
            double[] nab = normalize(new double[] {abx, aby});
            double[] neq = normalize(new double[] {eqx, eqy});
            out[0] = nonZeroSign(cross(aqx, aqy, abx, aby)) * endpoint;
            out[1] = Math.abs(nab[0] * neq[0] + nab[1] * neq[1]);
        }

        @Override
        EdgeSegment[] splitInThirds() {
            return new EdgeSegment[] {
                new Linear(p0, point(1 / 3.0), color),
                new Linear(point(1 / 3.0), point(2 / 3.0), color),
                new Linear(point(2 / 3.0), p1, color)
            };
        }

        @Override
        EdgeSegment reversed() {
            return new Linear(p1, p0, color);
        }
    }

    /** A quadratic Bézier edge. */
    static final class Quadratic extends EdgeSegment {
        final double[] p0;
        final double[] p1;
        final double[] p2;

        Quadratic(double[] p0, double[] p1, double[] p2, int color) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.color = color;
        }

        @Override
        double[] point(double t) {
            double u = 1 - t;
            return new double[] {
                u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]
            };
        }

        @Override
        double[] direction(double t) {
            double dx = (p1[0] - p0[0]) + t * ((p2[0] - p1[0]) - (p1[0] - p0[0]));
            double dy = (p1[1] - p0[1]) + t * ((p2[1] - p1[1]) - (p1[1] - p0[1]));
            if (dx == 0 && dy == 0) {
                return new double[] {p2[0] - p0[0], p2[1] - p0[1]};
            }
            return new double[] {dx, dy};
        }

        @Override
        void signedDistance(double px, double py, double[] out) {
            double qax = p0[0] - px;
            double qay = p0[1] - py;
            double abx = p1[0] - p0[0];
            double aby = p1[1] - p0[1];
            double brx = p2[0] - p1[0] - abx;
            double bry = p2[1] - p1[1] - aby;
            double a = brx * brx + bry * bry;
            double b = 3 * (abx * brx + aby * bry);
            double c = 2 * (abx * abx + aby * aby) + (qax * brx + qay * bry);
            double d = qax * abx + qay * aby;
            double[] roots = new double[3];
            int n = Roots.cubic(roots, a, b, c, d);

            double[] ep = direction(0);
            double minDistance = nonZeroSign(cross(ep[0], ep[1], qax, qay)) * Math.hypot(qax, qay);
            double param = -(qax * ep[0] + qay * ep[1]) / (ep[0] * ep[0] + ep[1] * ep[1]);
            {
                ep = direction(1);
                double ex = p2[0] - px;
                double ey = p2[1] - py;
                double distance = Math.hypot(ex, ey);
                if (distance < Math.abs(minDistance)) {
                    minDistance = nonZeroSign(cross(ep[0], ep[1], ex, ey)) * distance;
                    param = ((px - p1[0]) * ep[0] + (py - p1[1]) * ep[1]) / (ep[0] * ep[0] + ep[1] * ep[1]);
                }
            }
            for (int i = 0; i < n; i++) {
                double t = roots[i];
                if (t > 0 && t < 1) {
                    double qex = qax + 2 * t * abx + t * t * brx;
                    double qey = qay + 2 * t * aby + t * t * bry;
                    double distance = Math.hypot(qex, qey);
                    if (distance <= Math.abs(minDistance)) {
                        minDistance = nonZeroSign(cross(abx + t * brx, aby + t * bry, qex, qey)) * distance;
                        param = t;
                    }
                }
            }
            out[0] = minDistance;
            out[2] = param;
            if (param >= 0 && param <= 1) {
                out[1] = 0;
            } else if (param < 0.5) {
                double[] dir = normalize(direction(0));
                double[] q = normalize(new double[] {qax, qay});
                out[1] = Math.abs(dir[0] * q[0] + dir[1] * q[1]);
            } else {
                double[] dir = normalize(direction(1));
                double[] q = normalize(new double[] {p2[0] - px, p2[1] - py});
                out[1] = Math.abs(dir[0] * q[0] + dir[1] * q[1]);
            }
        }

        @Override
        EdgeSegment[] splitInThirds() {
            double[] a = point(1 / 3.0);
            double[] b = point(2 / 3.0);
            return new EdgeSegment[] {
                new Quadratic(p0, mix(p0, p1, 1 / 3.0), a, color),
                new Quadratic(a, mix(mix(p0, p1, 5 / 9.0), mix(p1, p2, 4 / 9.0), 0.5), b, color),
                new Quadratic(b, mix(p1, p2, 2 / 3.0), p2, color)
            };
        }

        @Override
        EdgeSegment reversed() {
            return new Quadratic(p2, p1, p0, color);
        }

        private static double[] mix(double[] a, double[] b, double t) {
            return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
        }
    }
}
