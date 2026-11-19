/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import lol.trq.helios.gl.GlBackend;

/**
 * Records the GL state a draw changes and puts it back.
 *
 * <p>The first change to a slot reads its current value from GL and keeps it; later changes to the same slot are not
 * saved again. {@link #close()} restores the touched slots in reverse order of first touch. States the host caches
 * are changed and restored through its {@link GlStateCache}, so its cache stays true; the rest (program, framebuffer,
 * viewport) go to the backend. Use one guard per draw, in a try-with-resources block, on the GL thread.
 *
 * <pre>{@code
 * try (StateGuard g = StateGuard.begin(gl, cache)) {
 *     g.blend(true);
 *     g.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
 *     g.program(program.id());
 *     // draw
 * }
 * }</pre>
 *
 * <p>Saved values come from GL, which agrees with the host cache as long as everyone changes cached state through
 * the cache.
 */
public final class StateGuard implements AutoCloseable {
    /** A piece of GL state the guard can save and restore. */
    public enum Slot {
        /** {@code GL_BLEND}. */
        BLEND,
        /** The four separate blend factors. */
        BLEND_FUNC,
        /** {@code GL_DEPTH_TEST}. */
        DEPTH_TEST,
        /** The depth write mask. */
        DEPTH_MASK,
        /** {@code GL_CULL_FACE}. */
        CULL,
        /** {@code GL_ALPHA_TEST}. */
        ALPHA_TEST,
        /** {@code GL_TEXTURE_2D} on the active unit. */
        TEXTURE_2D,
        /** The current colour. */
        COLOR,
        /** The program in use. */
        PROGRAM,
        /** The framebuffer bound to {@code GL_FRAMEBUFFER}. */
        FRAMEBUFFER,
        /** The viewport. */
        VIEWPORT
    }

    private final GlBackend gl;
    private final GlStateCache cache;
    private final List<Slot> order = new ArrayList<>(Slot.values().length);
    private final EnumSet<Slot> saved = EnumSet.noneOf(Slot.class);
    private final boolean[] flags = new boolean[Slot.values().length];
    private final int[] blendFunc = new int[4];
    private final float[] color = new float[4];
    private final int[] viewport = new int[4];
    private int program;
    private int framebuffer;
    private boolean closed;

    private StateGuard(GlBackend gl, GlStateCache cache) {
        this.gl = gl;
        this.cache = cache;
    }

    /**
     * Opens a guard.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @return the guard
     */
    public static StateGuard begin(GlBackend gl, GlStateCache cache) {
        return new StateGuard(gl, cache);
    }

    /**
     * Sets blending on or off.
     *
     * @param on whether blending is on
     * @return this guard
     */
    public StateGuard blend(boolean on) {
        saveFlag(Slot.BLEND, GlBackend.BLEND);
        cache.blend(on);
        return this;
    }

    /**
     * Sets the separate blend factors.
     *
     * @param srcRgb source RGB factor
     * @param dstRgb destination RGB factor
     * @param srcAlpha source alpha factor
     * @param dstAlpha destination alpha factor
     * @return this guard
     */
    public StateGuard blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (save(Slot.BLEND_FUNC)) {
            blendFunc[0] = gl.getInteger(GlBackend.BLEND_SRC_RGB);
            blendFunc[1] = gl.getInteger(GlBackend.BLEND_DST_RGB);
            blendFunc[2] = gl.getInteger(GlBackend.BLEND_SRC_ALPHA);
            blendFunc[3] = gl.getInteger(GlBackend.BLEND_DST_ALPHA);
        }
        cache.blendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        return this;
    }

    /**
     * Sets the depth test on or off.
     *
     * @param on whether the depth test is on
     * @return this guard
     */
    public StateGuard depthTest(boolean on) {
        saveFlag(Slot.DEPTH_TEST, GlBackend.DEPTH_TEST);
        cache.depthTest(on);
        return this;
    }

    /**
     * Sets the depth write mask.
     *
     * @param write whether depth writes are on
     * @return this guard
     */
    public StateGuard depthMask(boolean write) {
        if (save(Slot.DEPTH_MASK)) {
            flags[Slot.DEPTH_MASK.ordinal()] = gl.getInteger(GlBackend.DEPTH_WRITEMASK) != 0;
        }
        cache.depthMask(write);
        return this;
    }

    /**
     * Sets face culling on or off.
     *
     * @param on whether culling is on
     * @return this guard
     */
    public StateGuard cull(boolean on) {
        saveFlag(Slot.CULL, GlBackend.CULL_FACE);
        cache.cull(on);
        return this;
    }

    /**
     * Sets the fixed-function alpha test on or off.
     *
     * @param on whether the alpha test is on
     * @return this guard
     */
    public StateGuard alphaTest(boolean on) {
        saveFlag(Slot.ALPHA_TEST, GlBackend.ALPHA_TEST);
        cache.alphaTest(on);
        return this;
    }

    /**
     * Sets fixed-function 2D texturing on the active unit on or off.
     *
     * @param on whether texturing is on
     * @return this guard
     */
    public StateGuard texture2D(boolean on) {
        saveFlag(Slot.TEXTURE_2D, GlBackend.TEXTURE_2D);
        cache.texture2D(on);
        return this;
    }

    /**
     * Sets the current colour.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @param a alpha
     * @return this guard
     */
    public StateGuard color(float r, float g, float b, float a) {
        if (save(Slot.COLOR)) {
            gl.getFloats(GlBackend.CURRENT_COLOR, color);
        }
        cache.color(r, g, b, a);
        return this;
    }

    /**
     * Uses a program.
     *
     * @param id the program, or 0
     * @return this guard
     */
    public StateGuard program(int id) {
        if (save(Slot.PROGRAM)) {
            program = gl.getInteger(GlBackend.CURRENT_PROGRAM);
        }
        gl.useProgram(id);
        return this;
    }

    /**
     * Binds a framebuffer.
     *
     * @param id the framebuffer, or 0 for the window
     * @return this guard
     */
    public StateGuard framebuffer(int id) {
        if (save(Slot.FRAMEBUFFER)) {
            framebuffer = gl.getInteger(GlBackend.FRAMEBUFFER_BINDING);
        }
        gl.bindFramebuffer(id);
        return this;
    }

    /**
     * Sets the viewport.
     *
     * @param x left
     * @param y bottom
     * @param width width in pixels
     * @param height height in pixels
     * @return this guard
     */
    public StateGuard viewport(int x, int y, int width, int height) {
        if (save(Slot.VIEWPORT)) {
            gl.getIntegers(GlBackend.VIEWPORT, viewport);
        }
        gl.viewport(x, y, width, height);
        return this;
    }

    /**
     * Returns the slots this guard will restore, in first-touch order.
     *
     * @return an unmodifiable view
     */
    public List<Slot> touched() {
        return Collections.unmodifiableList(order);
    }

    /** Restores every touched slot, newest first. Calling it again does nothing. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int i = order.size() - 1; i >= 0; i--) {
            Slot slot = order.get(i);
            boolean flag = flags[slot.ordinal()];
            switch (slot) {
                case BLEND -> cache.blend(flag);
                case BLEND_FUNC -> cache.blendFunc(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
                case DEPTH_TEST -> cache.depthTest(flag);
                case DEPTH_MASK -> cache.depthMask(flag);
                case CULL -> cache.cull(flag);
                case ALPHA_TEST -> cache.alphaTest(flag);
                case TEXTURE_2D -> cache.texture2D(flag);
                case COLOR -> cache.color(color[0], color[1], color[2], color[3]);
                case PROGRAM -> gl.useProgram(program);
                case FRAMEBUFFER -> gl.bindFramebuffer(framebuffer);
                case VIEWPORT -> gl.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
        }
    }

    private void saveFlag(Slot slot, int cap) {
        if (save(slot)) {
            flags[slot.ordinal()] = gl.isEnabled(cap);
        }
    }

    private boolean save(Slot slot) {
        if (closed) {
            throw new IllegalStateException("guard already closed");
        }
        if (saved.add(slot)) {
            order.add(slot);
            return true;
        }
        return false;
    }
}
