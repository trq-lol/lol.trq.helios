/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fbo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import lol.trq.helios.gl.GlBackend;

/**
 * Reuses offscreen framebuffers across frames.
 *
 * <p>Screen-relative sizes come from {@link SurfaceSize}, in framebuffer pixels, so effects stay sharp on high-DPI
 * screens. When the surface size changes, every idle buffer is deleted, and buffers lent out under the old size are
 * deleted when they come back. Use it on the GL thread only.
 */
public final class FramebufferPool implements AutoCloseable {
    private final GlBackend gl;
    private final SurfaceSize surface;
    private final Map<Key, Deque<Framebuffer>> idle = new HashMap<>();
    private final Map<Framebuffer, Integer> lent = new IdentityHashMap<>();
    private int generation;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int created;

    /**
     * Creates a pool.
     *
     * @param gl the backend
     * @param surface the window framebuffer size
     */
    public FramebufferPool(GlBackend gl, SurfaceSize surface) {
        this.gl = gl;
        this.surface = surface;
    }

    /**
     * Borrows a framebuffer the size of the window, or a fraction of it.
     *
     * @param divisor 1 for full size, 2 for half, and so on
     * @param depthStencil whether a depth-stencil buffer is needed
     * @return the framebuffer; give it back with {@link #release}
     */
    public Framebuffer acquireScreen(int divisor, boolean depthStencil) {
        if (divisor < 1) {
            throw new IllegalArgumentException("divisor must be at least 1: " + divisor);
        }
        checkSurface();
        return acquire(Math.max(1, lastWidth / divisor), Math.max(1, lastHeight / divisor), depthStencil);
    }

    /**
     * Borrows a framebuffer of a fixed size.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param depthStencil whether a depth-stencil buffer is needed
     * @return the framebuffer; give it back with {@link #release}
     */
    public Framebuffer acquire(int width, int height, boolean depthStencil) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("size must be positive: " + width + "x" + height);
        }
        checkSurface();
        Deque<Framebuffer> free = idle.get(new Key(width, height, depthStencil));
        Framebuffer fb = free == null ? null : free.pollFirst();
        if (fb == null) {
            int[] ids = gl.createFramebuffer(width, height, depthStencil);
            fb = new Framebuffer(ids[0], ids[1], width, height, depthStencil, ids);
            created++;
        }
        lent.put(fb, generation);
        return fb;
    }

    /**
     * Gives a framebuffer back.
     *
     * @param fb a framebuffer from this pool
     * @throws IllegalArgumentException if it is not currently lent out by this pool
     */
    public void release(Framebuffer fb) {
        Integer gen = lent.remove(fb);
        if (gen == null) {
            throw new IllegalArgumentException("framebuffer not lent out by this pool");
        }
        if (gen != generation) {
            gl.deleteFramebuffer(fb.ids());
            return;
        }
        idle.computeIfAbsent(new Key(fb.width(), fb.height(), fb.depthStencil()), k -> new ArrayDeque<>())
                .addFirst(fb);
    }

    /**
     * Returns how many framebuffers sit idle.
     *
     * @return the idle count
     */
    public int idleCount() {
        return idle.values().stream().mapToInt(Deque::size).sum();
    }

    /**
     * Returns how many framebuffers are lent out.
     *
     * @return the lent count
     */
    public int lentCount() {
        return lent.size();
    }

    /**
     * Returns how many framebuffers this pool has created in total.
     *
     * @return the creation count
     */
    public int createdCount() {
        return created;
    }

    /** Deletes every idle framebuffer. Lent ones are deleted when released. */
    public void trim() {
        for (Deque<Framebuffer> q : idle.values()) {
            for (Framebuffer fb : q) {
                gl.deleteFramebuffer(fb.ids());
            }
        }
        idle.clear();
    }

    /** Deletes every idle framebuffer and retires the lent ones, as after a resize. */
    @Override
    public void close() {
        trim();
        generation++;
    }

    private void checkSurface() {
        int w = surface.width();
        int h = surface.height();
        if (w != lastWidth || h != lastHeight) {
            if (lastWidth >= 0) {
                close();
            }
            lastWidth = w;
            lastHeight = h;
        }
    }

    private record Key(int width, int height, boolean depthStencil) {}
}
