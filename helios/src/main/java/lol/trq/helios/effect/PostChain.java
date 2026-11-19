/* SPDX-License-Identifier: MIT */
package lol.trq.helios.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lol.trq.helios.fbo.Framebuffer;
import lol.trq.helios.shader.ShaderProgram;

/**
 * A chain of full-screen passes applied to what is drawn so far: the screen is captured, each pass reads the previous
 * result and writes a new one, and the last result replaces the screen.
 *
 * <p>Each program must be built on {@link Passes#FULLSCREEN_VERTEX} and read {@code u_texture} (unit 0) at
 * {@code v_uv}; it also receives {@code u_texel}, one source pixel in texture coordinates.
 */
public final class PostChain {
    private final Passes passes;
    private final List<Step> steps = new ArrayList<>();

    /**
     * Creates an empty chain.
     *
     * @param passes the shared passes
     */
    public PostChain(Passes passes) {
        this.passes = passes;
    }

    /**
     * Appends a pass.
     *
     * @param program the program
     * @param uniforms sets its own uniforms each time it runs
     * @return this chain
     */
    public PostChain then(ShaderProgram program, Consumer<ShaderProgram> uniforms) {
        steps.add(new Step(program, uniforms));
        return this;
    }

    /**
     * Returns how many passes the chain has.
     *
     * @return the pass count
     */
    public int size() {
        return steps.size();
    }

    /** Runs the chain on the bound framebuffer. Does nothing when the chain is empty. */
    public void apply() {
        if (steps.isEmpty()) {
            return;
        }
        Framebuffer current = passes.captureScreen();
        try {
            for (Step step : steps) {
                Framebuffer next = passes.pool().acquire(current.width(), current.height(), false);
                try {
                    passes.run(step.program, current, next, step.uniforms);
                } catch (RuntimeException e) {
                    passes.pool().release(next);
                    throw e;
                }
                passes.pool().release(current);
                current = next;
            }
            passes.composite(current.texture(), false, 1, 1, 1, 1);
        } finally {
            passes.pool().release(current);
        }
    }

    private record Step(ShaderProgram program, Consumer<ShaderProgram> uniforms) {}
}
