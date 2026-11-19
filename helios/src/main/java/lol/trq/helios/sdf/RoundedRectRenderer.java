/* SPDX-License-Identifier: MIT */
package lol.trq.helios.sdf;

import java.util.Map;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.ShaderProgram;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;

/**
 * Draws filled rounded rectangles with a signed distance field and analytic one-pixel anti-aliasing.
 *
 * <p>Coordinates are in the caller's current model-view-projection space (GUI units on a Minecraft screen). The
 * quad is grown by one pixel on every side so the outer half of the anti-aliased edge is not clipped.
 */
public final class RoundedRectRenderer implements AutoCloseable {
    private final GlBackend gl;
    private final GlStateCache cache;
    private final ShaderProgram program;

    /**
     * Creates the renderer and compiles its program.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param shaders the loader that resolves Helios's built-in shaders
     */
    public RoundedRectRenderer(GlBackend gl, GlStateCache cache, ShaderLoader shaders) {
        this.gl = gl;
        this.cache = cache;
        this.program = shaders.load("rounded_rect.vsh", "rounded_rect.fsh", Map.of());
    }

    /**
     * Draws a filled rounded rectangle.
     *
     * @param x left edge
     * @param y top edge
     * @param width width
     * @param height height
     * @param radius corner radius, clamped to half the shorter side
     * @param argb colour as packed {@code 0xAARRGGBB}, not premultiplied
     * @param pixel the size of one framebuffer pixel in these units, such as {@code 1 / guiScale}
     */
    public void fill(float x, float y, float width, float height, float radius, int argb, float pixel) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float hw = width / 2f;
        float hh = height / 2f;
        float pad = Math.max(pixel, 0f);
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.blend(true)
                    .blendFunc(
                            GlBackend.SRC_ALPHA,
                            GlBackend.ONE_MINUS_SRC_ALPHA,
                            GlBackend.ONE,
                            GlBackend.ONE_MINUS_SRC_ALPHA)
                    .alphaTest(false)
                    .texture2D(false)
                    .cull(false)
                    .program(program.id());
            program.set("u_halfSize", hw, hh);
            program.set("u_radius", Sdf.clampRadius(radius, hw, hh));
            program.set(
                    "u_color",
                    ((argb >>> 16) & 0xFF) / 255f,
                    ((argb >>> 8) & 0xFF) / 255f,
                    (argb & 0xFF) / 255f,
                    (argb >>> 24) / 255f);
            gl.quad(x - pad, y - pad, x + width + pad, y + height + pad, -hw - pad, -hh - pad, hw + pad, hh + pad);
        }
    }

    /** Deletes the program. */
    @Override
    public void close() {
        program.close();
    }
}
