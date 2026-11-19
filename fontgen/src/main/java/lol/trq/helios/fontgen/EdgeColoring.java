/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigns channel colours to edges so that every sharp corner sits between two edges that share only one channel,
 * which is what lets a multi-channel field keep the corner sharp. Port of msdfgen's {@code edgeColoringSimple}.
 */
final class EdgeColoring {
    private EdgeColoring() {}

    /** Seeded colour switcher; deterministic for a given seed. */
    private static final class Switcher {
        long seed;

        Switcher(long seed) {
            this.seed = seed;
        }

        int next(int color, int banned) {
            int combined = color & banned;
            if (combined == EdgeSegment.RED || combined == EdgeSegment.GREEN || combined == EdgeSegment.BLUE) {
                return combined ^ EdgeSegment.WHITE;
            }
            if (color == EdgeSegment.BLACK || color == EdgeSegment.WHITE) {
                int[] start = {EdgeSegment.CYAN, EdgeSegment.MAGENTA, EdgeSegment.YELLOW};
                int c = start[(int) (seed % 3)];
                seed /= 3;
                return c;
            }
            int shifted = color << (1 + (int) (seed & 1));
            seed >>= 1;
            return (shifted | shifted >> 3) & EdgeSegment.WHITE;
        }
    }

    static void simple(GlyphShape shape, double angleThreshold, long seed) {
        double crossThreshold = Math.sin(angleThreshold);
        Switcher sw = new Switcher(seed);
        for (int k = 0; k < shape.contours.size(); k++) {
            List<EdgeSegment> edges = shape.contours.get(k);
            List<Integer> corners = new ArrayList<>();
            double[] prev = EdgeSegment.normalize(edges.get(edges.size() - 1).direction(1));
            for (int i = 0; i < edges.size(); i++) {
                double[] cur = EdgeSegment.normalize(edges.get(i).direction(0));
                if (isCorner(prev, cur, crossThreshold)) {
                    corners.add(i);
                }
                prev = EdgeSegment.normalize(edges.get(i).direction(1));
            }
            if (corners.isEmpty()) {
                for (EdgeSegment e : edges) {
                    e.color = EdgeSegment.WHITE;
                }
            } else if (corners.size() == 1) {
                int[] colors = {EdgeSegment.WHITE, EdgeSegment.WHITE, 0};
                colors[0] = sw.next(colors[0], EdgeSegment.BLACK);
                colors[2] = sw.next(colors[0], EdgeSegment.BLACK);
                int corner = corners.get(0);
                int m = edges.size();
                if (m >= 3) {
                    for (int i = 0; i < m; i++) {
                        int idx = (int) (3 + 2.875 * i / (m - 1) - 1.4375 + 0.5) - 3;
                        edges.get((corner + i) % m).color = colors[1 + idx];
                    }
                } else {
                    EdgeSegment[] parts = new EdgeSegment[7];
                    EdgeSegment[] first = edges.get(0).splitInThirds();
                    System.arraycopy(first, 0, parts, 3 * corner, 3);
                    if (m >= 2) {
                        EdgeSegment[] second = edges.get(1).splitInThirds();
                        System.arraycopy(second, 0, parts, 3 - 3 * corner, 3);
                        parts[0].color = parts[1].color = colors[0];
                        parts[2].color = parts[3].color = colors[1];
                        parts[4].color = parts[5].color = colors[2];
                    } else {
                        parts[0].color = colors[0];
                        parts[1].color = colors[1];
                        parts[2].color = colors[2];
                    }
                    List<EdgeSegment> split = new ArrayList<>();
                    for (EdgeSegment p : parts) {
                        if (p != null) {
                            split.add(p);
                        }
                    }
                    shape.contours.set(k, split);
                }
            } else {
                int cornerCount = corners.size();
                int spline = 0;
                int start = corners.get(0);
                int m = edges.size();
                int color = sw.next(EdgeSegment.WHITE, EdgeSegment.BLACK);
                int initial = color;
                for (int i = 0; i < m; i++) {
                    int index = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1) == index) {
                        spline++;
                        color = sw.next(color, spline == cornerCount - 1 ? initial : EdgeSegment.BLACK);
                    }
                    edges.get(index).color = color;
                }
            }
        }
    }

    static boolean isCorner(double[] a, double[] b, double crossThreshold) {
        return a[0] * b[0] + a[1] * b[1] <= 0 || Math.abs(EdgeSegment.cross(a[0], a[1], b[0], b[1])) > crossThreshold;
    }
}
