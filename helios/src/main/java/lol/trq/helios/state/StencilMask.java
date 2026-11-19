/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.gl.GlBackend;

/**
 * Clips drawing to an arbitrary shape with the stencil buffer: draw a mask, then draw content only inside it (or only
 * outside it).
 *
 * <pre>{@code
 * helios.stencil().clip(
 *         () -> helios.shapes().begin(pixel).circle(50, 50, 30, Paint.solid(-1)).end(),
 *         true,
 *         () -> drawAvatar());
 * }</pre>
 *
 * <p>The bound framebuffer needs a stencil buffer: pooled framebuffers made with {@code depthStencil}, or on
 * Minecraft the main framebuffer after {@code MinecraftHelios.enableStencil()}. Mask fragments with alpha at or below
 * one half are dropped, so the mask edge is not anti-aliased; the content inside it is. Masks do not nest: a clip
 * clears the stencil buffer.
 */
public final class StencilMask {
    private static final float CUTOFF = 0.5f;

    private final GlBackend gl;
    private final GlStateCache cache;
    private final ShapeBatch batch;

    /**
     * Creates the helper.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param batch the batch the mask is drawn with; its alpha cutoff is raised while the mask draws
     */
    public StencilMask(GlBackend gl, GlStateCache cache, ShapeBatch batch) {
        this.gl = gl;
        this.cache = cache;
        this.batch = batch;
    }

    /**
     * Draws content clipped to a mask.
     *
     * @param mask draws the mask shape with the batch given to the constructor; nothing reaches the colour buffer
     * @param inside keep the content inside the mask, or outside it
     * @param content draws the clipped content
     * @throws IllegalStateException if the bound framebuffer has no stencil buffer
     */
    public void clip(Runnable mask, boolean inside, Runnable content) {
        if (gl.stencilBits() == 0) {
            throw new IllegalStateException(
                    "the bound framebuffer has no stencil buffer; on Minecraft call MinecraftHelios.enableStencil()");
        }
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.stencil(true, GlBackend.ALWAYS, 1, 0xFF, GlBackend.KEEP, GlBackend.REPLACE, GlBackend.REPLACE, 0xFF);
            gl.clearStencil(0);
            g.colorMask(false, false, false, false).depthMask(false);
            batch.alphaCutoff(CUTOFF);
            try {
                mask.run();
            } finally {
                batch.alphaCutoff(0);
            }
            g.colorMask(true, true, true, true);
            g.stencil(
                    true,
                    inside ? GlBackend.EQUAL : GlBackend.NOTEQUAL,
                    1,
                    0xFF,
                    GlBackend.KEEP,
                    GlBackend.KEEP,
                    GlBackend.KEEP,
                    0);
            content.run();
        }
    }
}
