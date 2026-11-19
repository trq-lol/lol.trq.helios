/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fbo;

/**
 * The size of the window's framebuffer in pixels.
 *
 * <p>On a high-DPI display this is larger than the window size in screen points (twice as large on a 2x Retina
 * screen). Everything Helios allocates per screen is sized from this, never from window points.
 */
public interface SurfaceSize {
    /**
     * Returns the framebuffer width.
     *
     * @return width in pixels
     */
    int width();

    /**
     * Returns the framebuffer height.
     *
     * @return height in pixels
     */
    int height();
}
