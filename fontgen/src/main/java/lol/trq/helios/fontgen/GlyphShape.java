/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * A glyph outline as closed contours of edges, built from an AWT {@link Shape}, with every contour oriented so that
 * signed distances are positive inside the glyph. Cubic segments (CFF fonts) are approximated by quadratics.
 */
final class GlyphShape {
    final List<List<EdgeSegment>> contours = new ArrayList<>();

    private GlyphShape() {}

    static GlyphShape of(Shape outline) {
        GlyphShape shape = new GlyphShape();
        PathIterator it = outline.getPathIterator(null);
        double[] c = new double[6];
        List<EdgeSegment> contour = null;
        double[] start = null;
        double[] last = null;
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            switch (type) {
                case PathIterator.SEG_MOVETO -> {
                    shape.close(contour, last, start);
                    contour = new ArrayList<>();
                    start = new double[] {c[0], c[1]};
                    last = start;
                }
                case PathIterator.SEG_LINETO -> {
                    double[] p = {c[0], c[1]};
                    if (!same(last, p)) {
                        contour.add(new EdgeSegment.Linear(last, p, EdgeSegment.WHITE));
                    }
                    last = p;
                }
                case PathIterator.SEG_QUADTO -> {
                    double[] p1 = {c[0], c[1]};
                    double[] p2 = {c[2], c[3]};
                    if (!same(last, p2) || !same(last, p1)) {
                        contour.add(new EdgeSegment.Quadratic(last, p1, p2, EdgeSegment.WHITE));
                    }
                    last = p2;
                }
                case PathIterator.SEG_CUBICTO -> {
                    double[] p3 = {c[4], c[5]};
                    addCubic(contour, last, new double[] {c[0], c[1]}, new double[] {c[2], c[3]}, p3);
                    last = p3;
                }
                case PathIterator.SEG_CLOSE -> {
                    shape.close(contour, last, start);
                    contour = null;
                    last = start;
                }
                default -> throw new IllegalStateException("unknown path segment " + type);
            }
            it.next();
        }
        shape.close(contour, last, start);
        shape.orient(new Area(outline));
        return shape;
    }

    private void close(List<EdgeSegment> contour, double[] last, double[] start) {
        if (contour == null) {
            return;
        }
        if (last != null && start != null && !same(last, start)) {
            contour.add(new EdgeSegment.Linear(last, start, EdgeSegment.WHITE));
        }
        if (!contour.isEmpty() && !contours.contains(contour)) {
            contours.add(contour);
        }
    }

    /** Splits a cubic into four pieces and replaces each with its best single quadratic. */
    private static void addCubic(List<EdgeSegment> contour, double[] p0, double[] p1, double[] p2, double[] p3) {
        int pieces = 4;
        double[] prev = p0;
        for (int i = 1; i <= pieces; i++) {
            double t0 = (i - 1) / (double) pieces;
            double t1 = i / (double) pieces;
            double[] a = cubicPoint(p0, p1, p2, p3, t0);
            double[] d = cubicPoint(p0, p1, p2, p3, t1);
            double[] da = cubicDerivative(p0, p1, p2, p3, t0);
            double[] dd = cubicDerivative(p0, p1, p2, p3, t1);
            double h = (t1 - t0) / 3;
            double[] b = {a[0] + da[0] * h, a[1] + da[1] * h};
            double[] cc = {d[0] - dd[0] * h, d[1] - dd[1] * h};
            double[] control = {(3 * (b[0] + cc[0]) - a[0] - d[0]) / 4, (3 * (b[1] + cc[1]) - a[1] - d[1]) / 4};
            double[] end = i == pieces ? p3 : d;
            contour.add(new EdgeSegment.Quadratic(prev, control, end, EdgeSegment.WHITE));
            prev = end;
        }
    }

    private static double[] cubicPoint(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double u = 1 - t;
        double a = u * u * u;
        double b = 3 * u * u * t;
        double c = 3 * u * t * t;
        double d = t * t * t;
        return new double[] {
            a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]
        };
    }

    private static double[] cubicDerivative(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double u = 1 - t;
        double a = 3 * u * u;
        double b = 6 * u * t;
        double c = 3 * t * t;
        return new double[] {
            a * (p1[0] - p0[0]) + b * (p2[0] - p1[0]) + c * (p3[0] - p2[0]),
            a * (p1[1] - p0[1]) + b * (p2[1] - p1[1]) + c * (p3[1] - p2[1])
        };
    }

    /** Reverses every contour whose edges call the glyph's inside negative. */
    private void orient(Area area) {
        double[] dist = new double[3];
        for (int k = 0; k < contours.size(); k++) {
            List<EdgeSegment> contour = contours.get(k);
            EdgeSegment longest = contour.get(0);
            double best = -1;
            for (EdgeSegment e : contour) {
                double[] a = e.point(0);
                double[] b = e.point(1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                if (len > best) {
                    best = len;
                    longest = e;
                }
            }
            double[] m = longest.point(0.5);
            double[] dir = EdgeSegment.normalize(longest.direction(0.5));
            double eps = 1e-3;
            double qx = m[0] + dir[1] * eps;
            double qy = m[1] - dir[0] * eps;
            longest.signedDistance(qx, qy, dist);
            boolean positive = dist[0] > 0;
            if (positive != area.contains(qx, qy)) {
                List<EdgeSegment> reversed = new ArrayList<>(contour.size());
                for (int i = contour.size() - 1; i >= 0; i--) {
                    reversed.add(contour.get(i).reversed());
                }
                contours.set(k, reversed);
            }
        }
    }

    boolean isEmpty() {
        return contours.isEmpty();
    }

    private static boolean same(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9;
    }
}
