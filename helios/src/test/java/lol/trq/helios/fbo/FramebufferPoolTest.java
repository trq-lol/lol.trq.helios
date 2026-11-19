/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.trq.helios.FakeGl;
import org.junit.jupiter.api.Test;

class FramebufferPoolTest {
    private final FakeGl gl = new FakeGl();
    private int width = 1708;
    private int height = 960;
    private final FramebufferPool pool = new FramebufferPool(gl, new SurfaceSize() {
        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }
    });

    @Test
    void screenSizeIsFramebufferPixels() {
        Framebuffer full = pool.acquireScreen(1, false);
        Framebuffer half = pool.acquireScreen(2, true);
        assertEquals(1708, full.width());
        assertEquals(960, full.height());
        assertEquals(854, half.width());
        assertEquals(480, half.height());
        assertTrue(half.depthStencil());
    }

    @Test
    void releasedBufferIsReused() {
        Framebuffer a = pool.acquireScreen(1, false);
        pool.release(a);
        Framebuffer b = pool.acquireScreen(1, false);
        assertSame(a, b);
        assertEquals(1, pool.createdCount());
    }

    @Test
    void differentFlagsAreDifferentBuffers() {
        Framebuffer a = pool.acquireScreen(1, false);
        pool.release(a);
        assertNotSame(a, pool.acquireScreen(1, true));
    }

    @Test
    void resizeDropsIdleAndRetiresLent() {
        Framebuffer idle = pool.acquireScreen(1, false);
        Framebuffer lent = pool.acquireScreen(1, false);
        pool.release(idle);
        width = 3416;
        height = 1920;
        Framebuffer fresh = pool.acquireScreen(1, false);
        assertEquals(3416, fresh.width());
        assertTrue(!gl.liveFramebuffers.contains(idle.id()));
        pool.release(lent);
        assertTrue(!gl.liveFramebuffers.contains(lent.id()));
        assertEquals(0, pool.idleCount());
        pool.release(fresh);
        assertEquals(1, pool.idleCount());
    }

    @Test
    void releasingForeignOrTwiceThrows() {
        Framebuffer a = pool.acquire(4, 4, false);
        pool.release(a);
        assertThrows(IllegalArgumentException.class, () -> pool.release(a));
    }

    @Test
    void invalidSizesThrow() {
        assertThrows(IllegalArgumentException.class, () -> pool.acquire(0, 4, false));
        assertThrows(IllegalArgumentException.class, () -> pool.acquireScreen(0, false));
    }

    @Test
    void closeDeletesIdle() {
        pool.release(pool.acquire(8, 8, false));
        pool.close();
        assertTrue(gl.liveFramebuffers.isEmpty());
    }
}
