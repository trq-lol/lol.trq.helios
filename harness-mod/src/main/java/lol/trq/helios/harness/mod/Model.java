/* SPDX-License-Identifier: MIT */
package lol.trq.helios.harness.mod;

import lol.trq.helios.sdf.Sdf;

/**
 * A software model of the shape shader: for a point in GUI units, the colour and coverage the GPU should produce.
 * Each test shape carries its geometry, its colours and a drawing call for the batch.
 */
final class Model {
    private Model() {}

    enum Kind {
        RECT,
        ARC,
        SHADOW,
        GLOW
    }

    /**
     * One test shape in its local frame: centre, axis, half extents, corner radius, kind parameters and paint.
     *
     * @param name label for reports
     * @param cx centre x
     * @param cy centre y
     * @param ax axis x (unit vector of the local x axis)
     * @param ay axis y
     * @param hw half width
     * @param hh half height
     * @param radius corner radius (rect kinds)
     * @param kind the kind
     * @param param stroke width, shadow softness or glow width; arc thickness for arcs
     * @param start arc start angle
     * @param sweep arc sweep
     * @param corners corner colours top-left, top-right, bottom-right, bottom-left
     * @param radial radial gradient radius, or 0 for a box gradient
     */
    record Shape(
            String name,
            float cx,
            float cy,
            float ax,
            float ay,
            float hw,
            float hh,
            float radius,
            Kind kind,
            float param,
            float start,
            float sweep,
            int[] corners,
            float radial) {
        /** Bounds in GUI units, grown by a margin: {x0, y0, x1, y1}. */
        float[] bounds(float margin) {
            float ex = Math.abs(hw * ax) + Math.abs(hh * ay) + margin;
            float ey = Math.abs(hw * ay) + Math.abs(hh * ax) + margin;
            if (kind == Kind.SHADOW) {
                ex += param;
                ey += param;
            }
            return new float[] {cx - ex, cy - ey, cx + ex, cy + ey};
        }

        /** Colour and alpha at a point: {r, g, b, a} in [0, 1], alpha including coverage. */
        float[] shade(float gx, float gy, float pixel) {
            float dx = gx - cx;
            float dy = gy - cy;
            float lx = dx * ax + dy * ay;
            float ly = -dx * ay + dy * ax;
            float alpha;
            if (kind == Kind.ARC) {
                alpha = Sdf.coverage(Sdf.arc(lx, ly, hw, param, start, sweep), pixel);
            } else {
                float d = Sdf.roundedRect(lx, ly, hw, hh, radius);
                alpha = switch (kind) {
                    case SHADOW -> Sdf.shadow(d, param);
                    case GLOW -> Sdf.coverage(d, pixel) * Sdf.glow(d, param);
                    default -> Sdf.coverage(param > 0 ? Sdf.stroke(d, param) : d, pixel);
                };
            }
            float[] c = paint(lx, ly);
            c[3] *= alpha;
            return c;
        }

        private float[] paint(float lx, float ly) {
            if (radial > 0) {
                float t = clamp((float) Math.hypot(lx, ly) / radial);
                return mix(rgba(corners[0]), rgba(corners[1]), t);
            }
            float u = clamp(lx / hw * 0.5f + 0.5f);
            float v = clamp(ly / hh * 0.5f + 0.5f);
            float[] top = mix(rgba(corners[0]), rgba(corners[1]), u);
            float[] bottom = mix(rgba(corners[3]), rgba(corners[2]), u);
            return mix(top, bottom, v);
        }
    }

    static float[] rgba(int argb) {
        return new float[] {
            ((argb >>> 16) & 0xFF) / 255f, ((argb >>> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f
        };
    }

    static float[] mix(float[] a, float[] b, float t) {
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
        return out;
    }

    static float clamp(float v) {
        return Math.min(Math.max(v, 0f), 1f);
    }
}
