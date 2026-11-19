/* SPDX-License-Identifier: MIT */
package lol.trq.helios.effect;

import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.fbo.Framebuffer;
import lol.trq.helios.fbo.FramebufferPool;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;

/**
 * Screen effects: background blur behind a shape, and bloom.
 *
 * <p>Both work on the framebuffer bound when they are called, and assume drawing units are screen space with the
 * origin at the top left, as on a Minecraft GUI screen: a point {@code (x, y)} is at framebuffer pixel
 * {@code (x / pixel, y / pixel)} from the top left.
 */
public final class Effects implements AutoCloseable {
    private final GlBackend gl;
    private final GlStateCache cache;
    private final Passes passes;
    private final ShapeBatch batch;

    /**
     * Creates the effects.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param passes the shared passes
     * @param batch the batch used to draw blurred results through a shape
     */
    public Effects(GlBackend gl, GlStateCache cache, Passes passes, ShapeBatch batch) {
        this.gl = gl;
        this.cache = cache;
        this.passes = passes;
        this.batch = batch;
    }

    /**
     * Blurs what is already drawn behind a rounded rectangle and draws the result inside it, the frosted glass
     * effect.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param radius corner radius
     * @param pixel one framebuffer pixel in drawing units
     * @param iterations blur strength, 1 to 8; each step roughly doubles the radius
     * @param offset tap spread, usually 1 to 4
     * @param tint colour the blurred image is multiplied by; {@code 0xFFFFFFFF} for none
     */
    public void blurBehind(
            float x,
            float y,
            float width,
            float height,
            float radius,
            float pixel,
            int iterations,
            float offset,
            int tint) {
        FramebufferPool pool = passes.pool();
        Framebuffer screen = passes.captureScreen();
        Framebuffer blurred = null;
        try {
            blurred = passes.blur(screen.texture(), screen.width(), screen.height(), iterations, offset);
            float sw = screen.width() * pixel;
            float sh = screen.height() * pixel;
            batch.begin(pixel)
                    .image(
                            blurred.texture(),
                            x,
                            y,
                            width,
                            height,
                            radius,
                            x / sw,
                            1 - y / sh,
                            (x + width) / sw,
                            1 - (y + height) / sh,
                            tint)
                    .end();
        } finally {
            pool.release(screen);
            if (blurred != null) {
                pool.release(blurred);
            }
        }
    }

    /**
     * Bloom: draws the glow sources into an offscreen buffer, blurs it, and adds the result onto the bound
     * framebuffer. The sources themselves are not drawn on screen; draw them again normally if they should show.
     *
     * @param sources draws the glowing parts, in the current projection; the offscreen buffer is bound and cleared
     * @param iterations blur strength, 1 to 8
     * @param offset tap spread, usually 1 to 4
     * @param intensity how strongly the glow is added; 1 adds it once
     */
    public void bloom(Runnable sources, int iterations, float offset, float intensity) {
        FramebufferPool pool = passes.pool();
        Framebuffer glow = pool.acquireScreen(1, false);
        Framebuffer blurred = null;
        try {
            try (StateGuard g = StateGuard.begin(gl, cache)) {
                g.framebuffer(glow.id()).viewport(0, 0, glow.width(), glow.height());
                Passes.offscreen(g);
                gl.clearColorTransparent();
                sources.run();
            }
            blurred = passes.blur(glow.texture(), glow.width(), glow.height(), iterations, offset);
            passes.composite(blurred.texture(), true, intensity, intensity, intensity, intensity);
        } finally {
            pool.release(glow);
            if (blurred != null) {
                pool.release(blurred);
            }
        }
    }

    /** Deletes the batch the effects draw through. */
    @Override
    public void close() {
        batch.close();
    }
}
