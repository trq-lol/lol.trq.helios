/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import java.util.ArrayDeque;
import java.util.Deque;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.GlBackend;

/**
 * Nested clipping rectangles in drawing units, each clipped to the one outside it.
 *
 * <p>Drawing units are screen space with the origin at the top left, and {@code pixel} is one framebuffer pixel in
 * those units. The first {@link #push} saves the scissor state; the last {@link #pop} restores it. A clip covers every
 * pixel the rectangle touches.
 */
public final class ScissorStack {
    private final GlBackend gl;
    private final GlStateCache cache;
    private final SurfaceSize surface;
    private final Deque<int[]> stack = new ArrayDeque<>();
    private StateGuard guard;

    /**
     * Creates a stack.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param surface the window framebuffer size
     */
    public ScissorStack(GlBackend gl, GlStateCache cache, SurfaceSize surface) {
        this.gl = gl;
        this.cache = cache;
        this.surface = surface;
    }

    /**
     * Clips further drawing to a rectangle, inside the current clip.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param pixel one framebuffer pixel in drawing units
     */
    public void push(float x, float y, float width, float height, float pixel) {
        int[] box = toPixels(x, y, width, height, pixel, surface.height());
        int[] outer = stack.peek();
        if (outer != null) {
            box = intersect(box, outer);
        }
        if (guard == null) {
            guard = StateGuard.begin(gl, cache);
        }
        stack.push(box);
        guard.scissor(true, box[0], box[1], box[2], box[3]);
    }

    /** Returns to the previous clip, or to the state before the first push. */
    public void pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("scissor stack is empty");
        }
        stack.pop();
        int[] box = stack.peek();
        if (box == null) {
            guard.close();
            guard = null;
        } else {
            guard.scissor(true, box[0], box[1], box[2], box[3]);
        }
    }

    /**
     * Returns how many clips are pushed.
     *
     * @return the depth
     */
    public int depth() {
        return stack.size();
    }

    /**
     * Converts a rectangle in drawing units to a scissor box, rounding outward.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param pixel one framebuffer pixel in drawing units
     * @param framebufferHeight the framebuffer height in pixels
     * @return {@code {x, y, width, height}} in pixels, y from the bottom, never negative in size
     */
    public static int[] toPixels(float x, float y, float width, float height, float pixel, int framebufferHeight) {
        int left = (int) Math.floor(x / pixel + 1e-4f);
        int right = (int) Math.ceil((x + Math.max(width, 0)) / pixel - 1e-4f);
        int top = (int) Math.floor(y / pixel + 1e-4f);
        int bottom = (int) Math.ceil((y + Math.max(height, 0)) / pixel - 1e-4f);
        return new int[] {left, framebufferHeight - bottom, Math.max(0, right - left), Math.max(0, bottom - top)};
    }

    static int[] intersect(int[] a, int[] b) {
        int x0 = Math.max(a[0], b[0]);
        int y0 = Math.max(a[1], b[1]);
        int x1 = Math.min(a[0] + a[2], b[0] + b[2]);
        int y1 = Math.min(a[1] + a[3], b[1] + b[3]);
        return new int[] {x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)};
    }
}
