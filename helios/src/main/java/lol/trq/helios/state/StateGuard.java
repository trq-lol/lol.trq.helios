/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import java.util.ArrayList;
import java.util.Collections;
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
        /** The program in use. */
        PROGRAM,
        /** The framebuffer bound to {@code GL_FRAMEBUFFER}. */
        FRAMEBUFFER,
        /** The viewport. */
        VIEWPORT,
        /** The buffer bound to {@code GL_ARRAY_BUFFER}. */
        ARRAY_BUFFER,
        /** {@code GL_VERTEX_ARRAY_BINDING}. */
        VERTEX_ARRAY,
        /** The active texture unit. */
        ACTIVE_TEXTURE,
        /** The 2D texture bound on each touched unit. */
        TEXTURES,
        /** {@code GL_SCISSOR_TEST} and the scissor box. */
        SCISSOR,
        /** {@code GL_STENCIL_TEST}, the stencil function, operations and write mask. */
        STENCIL,
        /** The colour write mask. */
        COLOR_MASK,
    }

    private static final int MAX_UNITS = 8;

    private final GlBackend gl;
    private final GlStateCache cache;
    private static final Slot[] SLOTS = Slot.values();

    // Saved state lives in plain fields and bit masks; arrays are allocated only for the rarely used slots, so a
    // guard costs one small allocation.
    private final byte[] order = new byte[SLOTS.length];
    private int orderSize;
    private int savedMask;
    private int flagBits;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int[] viewport;
    private int program;
    private int framebuffer;
    private int arrayBuffer;
    private int vertexArray;
    private int activeTexture;
    private int unitsTouched;
    private int[] textures;
    private boolean scissorEnabled;
    private int[] scissorBox;
    private int[] stencil;
    private boolean stencilEnabled;
    private int colorMaskBits;
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
            blendSrcRgb = gl.getInteger(GlBackend.BLEND_SRC_RGB);
            blendDstRgb = gl.getInteger(GlBackend.BLEND_DST_RGB);
            blendSrcAlpha = gl.getInteger(GlBackend.BLEND_SRC_ALPHA);
            blendDstAlpha = gl.getInteger(GlBackend.BLEND_DST_ALPHA);
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
            setFlag(Slot.DEPTH_MASK, gl.getInteger(GlBackend.DEPTH_WRITEMASK) != 0);
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
            viewport = new int[4];
            gl.getIntegers(GlBackend.VIEWPORT, viewport);
        }
        gl.viewport(x, y, width, height);
        return this;
    }

    /**
     * Binds an array buffer.
     *
     * @param id the buffer, or 0
     * @return this guard
     */
    public StateGuard arrayBuffer(int id) {
        if (save(Slot.ARRAY_BUFFER)) {
            arrayBuffer = gl.getInteger(GlBackend.ARRAY_BUFFER_BINDING);
        }
        gl.bindBuffer(GlBackend.ARRAY_BUFFER, id);
        return this;
    }

    /**
     * Binds a vertex array object. Core profile draws need one bound; the element buffer and attribute layout are
     * part of it, so they need no slots of their own.
     *
     * @param id the vertex array, or 0
     * @return this guard
     */
    public StateGuard vertexArray(int id) {
        if (save(Slot.VERTEX_ARRAY)) {
            vertexArray = gl.getInteger(GlBackend.VERTEX_ARRAY_BINDING);
        }
        gl.bindVertexArray(id);
        return this;
    }

    /**
     * Binds a 2D texture on a texture unit, leaving that unit active.
     *
     * @param unit the unit number, below 8
     * @param texture the texture
     * @return this guard
     */
    public StateGuard texture(int unit, int texture) {
        if (unit < 0 || unit >= MAX_UNITS) {
            throw new IllegalArgumentException("texture unit out of range: " + unit);
        }
        if (save(Slot.ACTIVE_TEXTURE)) {
            activeTexture = gl.getInteger(GlBackend.ACTIVE_TEXTURE);
        }
        cache.activeTexture(GlBackend.TEXTURE0 + unit);
        if (save(Slot.TEXTURES)) {
            textures = new int[MAX_UNITS];
        }
        int bit = 1 << unit;
        if ((unitsTouched & bit) == 0) {
            unitsTouched |= bit;
            textures[unit] = gl.getInteger(GlBackend.TEXTURE_BINDING_2D);
        }
        cache.bindTexture(texture);
        return this;
    }

    /**
     * Sets the scissor test and box.
     *
     * @param on whether the scissor test is on
     * @param x left in pixels
     * @param y bottom in pixels
     * @param width width in pixels
     * @param height height in pixels
     * @return this guard
     */
    public StateGuard scissor(boolean on, int x, int y, int width, int height) {
        if (save(Slot.SCISSOR)) {
            scissorEnabled = gl.isEnabled(GlBackend.SCISSOR_TEST);
            scissorBox = new int[4];
            gl.getIntegers(GlBackend.SCISSOR_BOX, scissorBox);
        }
        gl.scissor(x, y, width, height);
        gl.setEnabled(GlBackend.SCISSOR_TEST, on);
        return this;
    }

    /**
     * Sets the stencil test, function and operations.
     *
     * @param on whether the stencil test is on
     * @param func the comparison
     * @param ref the reference value
     * @param valueMask the comparison mask
     * @param sfail action when the stencil test fails
     * @param dpfail action when the depth test fails
     * @param dppass action when both pass
     * @param writeMask which stencil bits are written
     * @return this guard
     */
    public StateGuard stencil(
            boolean on, int func, int ref, int valueMask, int sfail, int dpfail, int dppass, int writeMask) {
        if (save(Slot.STENCIL)) {
            stencilEnabled = gl.isEnabled(GlBackend.STENCIL_TEST);
            stencil = new int[7];
            stencil[0] = gl.getInteger(GlBackend.STENCIL_FUNC);
            stencil[1] = gl.getInteger(GlBackend.STENCIL_REF);
            stencil[2] = gl.getInteger(GlBackend.STENCIL_VALUE_MASK);
            stencil[3] = gl.getInteger(GlBackend.STENCIL_FAIL);
            stencil[4] = gl.getInteger(GlBackend.STENCIL_PASS_DEPTH_FAIL);
            stencil[5] = gl.getInteger(GlBackend.STENCIL_PASS_DEPTH_PASS);
            stencil[6] = gl.getInteger(GlBackend.STENCIL_WRITEMASK);
        }
        gl.setEnabled(GlBackend.STENCIL_TEST, on);
        gl.stencilFunc(func, ref, valueMask);
        gl.stencilOp(sfail, dpfail, dppass);
        gl.stencilMask(writeMask);
        return this;
    }

    /**
     * Sets which colour channels are written.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @param a alpha
     * @return this guard
     */
    public StateGuard colorMask(boolean r, boolean g, boolean b, boolean a) {
        if (save(Slot.COLOR_MASK)) {
            int[] m = new int[4];
            gl.getIntegers(GlBackend.COLOR_WRITEMASK, m);
            for (int i = 0; i < 4; i++) {
                if (m[i] != 0) {
                    colorMaskBits |= 1 << i;
                }
            }
        }
        cache.colorMask(r, g, b, a);
        return this;
    }

    /**
     * Returns the slots this guard will restore, in first-touch order.
     *
     * @return an unmodifiable view
     */
    public List<Slot> touched() {
        List<Slot> slots = new ArrayList<>(orderSize);
        for (int i = 0; i < orderSize; i++) {
            slots.add(SLOTS[order[i]]);
        }
        return Collections.unmodifiableList(slots);
    }

    /** Restores every touched slot, newest first. Calling it again does nothing. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int i = orderSize - 1; i >= 0; i--) {
            Slot slot = SLOTS[order[i]];
            boolean flag = (flagBits & (1 << slot.ordinal())) != 0;
            switch (slot) {
                case BLEND -> cache.blend(flag);
                case BLEND_FUNC -> cache.blendFunc(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
                case DEPTH_TEST -> cache.depthTest(flag);
                case DEPTH_MASK -> cache.depthMask(flag);
                case CULL -> cache.cull(flag);
                case PROGRAM -> gl.useProgram(program);
                case FRAMEBUFFER -> gl.bindFramebuffer(framebuffer);
                case VIEWPORT -> gl.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                case ARRAY_BUFFER -> gl.bindBuffer(GlBackend.ARRAY_BUFFER, arrayBuffer);
                case VERTEX_ARRAY -> gl.bindVertexArray(vertexArray);
                case ACTIVE_TEXTURE -> cache.activeTexture(activeTexture);
                case TEXTURES -> {
                    for (int u = 0; u < MAX_UNITS; u++) {
                        if ((unitsTouched & (1 << u)) != 0) {
                            cache.activeTexture(GlBackend.TEXTURE0 + u);
                            cache.bindTexture(textures[u]);
                        }
                    }
                }
                case STENCIL -> {
                    gl.stencilFunc(stencil[0], stencil[1], stencil[2]);
                    gl.stencilOp(stencil[3], stencil[4], stencil[5]);
                    gl.stencilMask(stencil[6]);
                    gl.setEnabled(GlBackend.STENCIL_TEST, stencilEnabled);
                }
                case COLOR_MASK ->
                    cache.colorMask(
                            (colorMaskBits & 1) != 0,
                            (colorMaskBits & 2) != 0,
                            (colorMaskBits & 4) != 0,
                            (colorMaskBits & 8) != 0);
                case SCISSOR -> {
                    gl.scissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
                    gl.setEnabled(GlBackend.SCISSOR_TEST, scissorEnabled);
                }
            }
        }
    }

    private void saveFlag(Slot slot, int cap) {
        if (save(slot)) {
            setFlag(slot, gl.isEnabled(cap));
        }
    }

    private boolean save(Slot slot) {
        if (closed) {
            throw new IllegalStateException("guard already closed");
        }
        int bit = 1 << slot.ordinal();
        if ((savedMask & bit) == 0) {
            savedMask |= bit;
            order[orderSize++] = (byte) slot.ordinal();
            return true;
        }
        return false;
    }

    private void setFlag(Slot slot, boolean on) {
        int bit = 1 << slot.ordinal();
        flagBits = on ? flagBits | bit : flagBits & ~bit;
    }
}
