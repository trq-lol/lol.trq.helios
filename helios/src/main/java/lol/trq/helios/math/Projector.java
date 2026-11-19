/* SPDX-License-Identifier: MIT */
package lol.trq.helios.math;

import lol.trq.helios.gl.GlBackend;

/**
 * Projects 3D points to screen coordinates, as {@code gluProject} does, without allocating.
 *
 * <p>Capture the matrices where the 3D scene is drawn (in a world render hook, where Minecraft's matrices are
 * camera-relative), then project points during GUI drawing. Results are in drawing units with the origin at the top
 * left: framebuffer pixels times {@code pixel}, which is {@code 1 / guiScale} on a GUI screen.
 *
 * <pre>{@code
 * // world render hook:
 * projector.capture(helios.gl(), 1f / scaledResolution.getScaleFactor());
 * // GUI draw:
 * float[] box = new float[4];
 * if (projector.projectBox(minX, minY, minZ, maxX, maxY, maxZ, box)) {
 *     batch.outline(box[0], box[1], box[2] - box[0], box[3] - box[1], 0, 1, Paint.solid(0xFFFF4040));
 * }
 * }</pre>
 */
public final class Projector {

    private final float[] modelView = new float[16];
    private final float[] projection = new float[16];
    private final float[] mvp = new float[16];
    private final float[] clip = new float[4];
    private final int[] viewport = new int[4];
    private final float[] point = new float[3];
    private float pixel = 1;

    /** Creates a projector with identity matrices and an empty viewport. */
    public Projector() {
        Mat4.identity(modelView);
        Mat4.identity(projection);
        Mat4.identity(mvp);
    }

    /**
     * Captures the host's current model-view and projection matrices and the viewport.
     *
     * @param gl the backend
     * @param pixel one framebuffer pixel in output units, such as {@code 1 / guiScale}
     * @return this projector
     */
    public Projector capture(GlBackend gl, float pixel) {
        gl.matrices(modelView, projection);
        gl.getIntegers(GlBackend.VIEWPORT, viewport);
        return set(modelView, projection, viewport, pixel);
    }

    /**
     * Sets the matrices and viewport directly.
     *
     * @param modelView the model-view matrix, column-major; copied
     * @param projection the projection matrix, column-major; copied
     * @param viewport {@code x, y, width, height} in framebuffer pixels; copied
     * @param pixel one framebuffer pixel in output units
     * @return this projector
     */
    public Projector set(float[] modelView, float[] projection, int[] viewport, float pixel) {
        if (modelView != this.modelView) {
            System.arraycopy(modelView, 0, this.modelView, 0, 16);
        }
        if (projection != this.projection) {
            System.arraycopy(projection, 0, this.projection, 0, 16);
        }
        if (viewport != this.viewport) {
            System.arraycopy(viewport, 0, this.viewport, 0, 4);
        }
        this.pixel = pixel;
        Mat4.multiply(this.projection, this.modelView, mvp);
        return this;
    }

    /**
     * Projects a point.
     *
     * @param x x in the captured model space
     * @param y y
     * @param z z
     * @param out receives screen x, screen y (drawing units, top-left origin) and depth (0 near, 1 far)
     * @return false when the point is behind the camera, leaving {@code out} unspecified
     */
    public boolean project(double x, double y, double z, float[] out) {
        Mat4.transform(mvp, x, y, z, 1, clip);
        float w = clip[3];
        if (w <= 1e-6f) {
            return false;
        }
        float nx = clip[0] / w;
        float ny = clip[1] / w;
        float nz = clip[2] / w;
        float winX = viewport[0] + (nx + 1) * 0.5f * viewport[2];
        float fromTop = (1 - ny) * 0.5f * viewport[3];
        out[0] = winX * pixel;
        out[1] = fromTop * pixel;
        out[2] = (nz + 1) * 0.5f;
        return true;
    }

    /**
     * Projects an axis-aligned box and returns its screen bounds: the 2D box for ESP.
     *
     * @param minX box min x
     * @param minY box min y
     * @param minZ box min z
     * @param maxX box max x
     * @param maxY box max y
     * @param maxZ box max z
     * @param out receives left, top, right, bottom in drawing units
     * @return false when no corner is in front of the camera within the depth range
     */
    public boolean projectBox(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float[] out) {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? minX : maxX;
            double y = (i & 2) == 0 ? minY : maxY;
            double z = (i & 4) == 0 ? minZ : maxZ;
            if (project(x, y, z, point) && point[2] >= 0 && point[2] < 1) {
                left = Math.min(left, point[0]);
                top = Math.min(top, point[1]);
                right = Math.max(right, point[0]);
                bottom = Math.max(bottom, point[1]);
                any = true;
            }
        }
        if (any) {
            out[0] = left;
            out[1] = top;
            out[2] = right;
            out[3] = bottom;
        }
        return any;
    }
}
