/* SPDX-License-Identifier: MIT */
package lol.trq.helios.effect;

import java.util.Map;
import lol.trq.helios.fbo.Framebuffer;
import lol.trq.helios.fbo.FramebufferPool;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.ShaderProgram;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;

/**
 * Full-screen passes shared by the effects: screen capture, the dual Kawase blur, and compositing a texture onto the
 * bound framebuffer. Every pass saves and restores the state it touches.
 */
public final class Passes implements AutoCloseable {
    /** The vertex shader for custom full-screen passes; it provides {@code varying vec2 v_uv}. */
    public static final String FULLSCREEN_VERTEX = "effect/fullscreen.vsh";

    private final GlBackend gl;
    private final GlStateCache cache;
    private final FramebufferPool pool;
    private final ShaderProgram down;
    private final ShaderProgram up;
    private final ShaderProgram composite;
    private int vao;

    /**
     * Creates the passes and compiles their programs.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param pool where intermediate framebuffers come from
     * @param shaders the loader that resolves the built-in shaders
     */
    public Passes(GlBackend gl, GlStateCache cache, FramebufferPool pool, ShaderLoader shaders) {
        this.gl = gl;
        this.cache = cache;
        this.pool = pool;
        this.down = shaders.load(FULLSCREEN_VERTEX, "effect/kawase_down.fsh", Map.of());
        this.up = shaders.load(FULLSCREEN_VERTEX, "effect/kawase_up.fsh", Map.of());
        this.composite = shaders.load(FULLSCREEN_VERTEX, "effect/composite.fsh", Map.of());
    }

    /**
     * Returns the framebuffer pool.
     *
     * @return the pool
     */
    public FramebufferPool pool() {
        return pool;
    }

    /**
     * Copies what is drawn so far into a pooled texture the size of the window.
     *
     * @return the copy; release it to the pool
     */
    public Framebuffer captureScreen() {
        Framebuffer fb = pool.acquireScreen(1, false);
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.texture(0, fb.texture());
            gl.copyToBoundTexture(fb.width(), fb.height());
        }
        return fb;
    }

    /**
     * Blurs a texture with the dual Kawase filter: {@code iterations} halvings, then the same number of doublings.
     *
     * @param texture the source texture
     * @param width source width in pixels
     * @param height source height in pixels
     * @param iterations how many times to halve, at least 1; each one roughly doubles the blur radius
     * @param offset the tap spread in source pixels, usually 1 to 4
     * @return the blurred image at the source size; release it to the pool
     */
    public Framebuffer blur(int texture, int width, int height, int iterations, float offset) {
        int[][] sizes = blurSizes(width, height, iterations);
        Framebuffer[] levels = new Framebuffer[sizes.length];
        int src = texture;
        try {
            for (int i = 1; i < sizes.length; i++) {
                levels[i] = pool.acquire(sizes[i][0], sizes[i][1], false);
                pass(down, src, levels[i], offset, false);
                src = levels[i].texture();
            }
            for (int i = sizes.length - 2; i >= 1; i--) {
                pass(up, src, levels[i], offset, false);
                src = levels[i].texture();
            }
            Framebuffer out = pool.acquire(width, height, false);
            pass(up, src, out, offset, false);
            return out;
        } finally {
            for (int i = 1; i < levels.length; i++) {
                if (levels[i] != null) {
                    pool.release(levels[i]);
                }
            }
        }
    }

    /**
     * The size of each level of a dual Kawase blur, level 0 being the source.
     *
     * @param width source width
     * @param height source height
     * @param iterations how many halvings
     * @return {@code iterations + 1} sizes, never below 1 pixel
     */
    public static int[][] blurSizes(int width, int height, int iterations) {
        if (iterations < 1 || iterations > 8) {
            throw new IllegalArgumentException("iterations must be between 1 and 8: " + iterations);
        }
        int[][] sizes = new int[iterations + 1][];
        for (int i = 0; i <= iterations; i++) {
            sizes[i] = new int[] {Math.max(1, width >> i), Math.max(1, height >> i)};
        }
        return sizes;
    }

    /**
     * Draws a texture over the whole bound framebuffer, multiplied by a tint.
     *
     * @param texture the texture
     * @param additive add onto what is there (for glows), or replace it
     * @param r red multiplier
     * @param g green multiplier
     * @param b blue multiplier
     * @param a alpha multiplier
     */
    public void composite(int texture, boolean additive, float r, float g, float b, float a) {
        try (StateGuard guard = StateGuard.begin(gl, cache)) {
            prepare(guard, composite, texture, additive);
            composite.set("u_tint", r, g, b, a);
            gl.drawArrays(GlBackend.TRIANGLES, 0, 3);
        }
    }

    /**
     * Runs a custom full-screen program from one texture into a framebuffer. The program gets {@code u_texture} on
     * unit 0 and {@code u_texel}, the size of one source pixel in texture coordinates.
     *
     * @param program a program built on {@link #FULLSCREEN_VERTEX}
     * @param source the source framebuffer
     * @param target the target framebuffer
     * @param uniforms sets the program's own uniforms; the program is bound when it runs
     */
    public void run(
            ShaderProgram program,
            Framebuffer source,
            Framebuffer target,
            java.util.function.Consumer<ShaderProgram> uniforms) {
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.framebuffer(target.id()).viewport(0, 0, target.width(), target.height());
            offscreen(g);
            prepare(g, program, source.texture(), false);
            program.set("u_texel", 1f / source.width(), 1f / source.height());
            uniforms.accept(program);
            gl.drawArrays(GlBackend.TRIANGLES, 0, 3);
        }
    }

    private void pass(ShaderProgram program, int source, Framebuffer target, float offset, boolean additive) {
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.framebuffer(target.id()).viewport(0, 0, target.width(), target.height());
            offscreen(g);
            prepare(g, program, source, additive);
            program.set("u_halfPixel", 0.5f / target.width(), 0.5f / target.height());
            program.set("u_offset", offset);
            gl.drawArrays(GlBackend.TRIANGLES, 0, 3);
        }
    }

    /**
     * Clears the caller's clipping and write masks for a pass into one of Helios's own framebuffers: a scissor box in
     * screen pixels, a stencil test or a colour mask left off by the caller would corrupt the intermediate image. The
     * final composite onto the caller's framebuffer keeps them, so masked effects still work.
     */
    static void offscreen(StateGuard g) {
        g.scissor(false, 0, 0, 0, 0)
                .stencil(false, GlBackend.ALWAYS, 0, 0xFF, GlBackend.KEEP, GlBackend.KEEP, GlBackend.KEEP, 0xFF)
                .colorMask(true, true, true, true);
    }

    private void prepare(StateGuard g, ShaderProgram program, int texture, boolean additive) {
        g.depthTest(false).depthMask(false).cull(false).blend(additive).vertexArray(emptyVertexArray());
        if (additive) {
            g.blendFunc(GlBackend.ONE, GlBackend.ONE, 0, GlBackend.ONE);
        }
        g.program(program.id()).texture(0, texture);
        program.set("u_texture", 0);
    }

    /** Deletes the programs. */
    /** Full-screen passes build their triangle from {@code gl_VertexID}; core still needs a vertex array bound. */
    private int emptyVertexArray() {
        if (vao == 0) {
            vao = gl.createVertexArray();
        }
        return vao;
    }

    @Override
    public void close() {
        if (vao != 0) {
            gl.deleteVertexArray(vao);
            vao = 0;
        }
        down.close();
        up.close();
        composite.close();
    }
}
