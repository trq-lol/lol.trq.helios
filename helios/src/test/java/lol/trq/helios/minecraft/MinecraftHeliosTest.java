/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftHeliosTest {
    /** Stands in for Forge's {@code Framebuffer}: method names match, calls are counted. */
    public static final class FakeFramebuffer {
        boolean stencil;
        int recreated;
        int bound;

        public boolean isStencilEnabled() {
            return stencil;
        }

        public boolean enableStencil() {
            if (!stencil) {
                stencil = true;
                recreated++;
            }
            return true;
        }

        public void bindFramebuffer(boolean viewport) {
            bound++;
        }
    }

    @Test
    void theFirstCallRecreatesAndRebindsTheFramebuffer() throws ReflectiveOperationException {
        FakeFramebuffer fb = new FakeFramebuffer();
        assertTrue(MinecraftHelios.enableStencil(fb));
        assertEquals(1, fb.recreated);
        assertEquals(1, fb.bound);
    }

    @Test
    void laterCallsChangeNothing() throws ReflectiveOperationException {
        FakeFramebuffer fb = new FakeFramebuffer();
        MinecraftHelios.enableStencil(fb);
        for (int i = 0; i < 5; i++) {
            assertTrue(MinecraftHelios.enableStencil(fb));
        }
        assertEquals(1, fb.recreated);
        assertEquals(1, fb.bound, "a repeated call must not rebind the main framebuffer or reset the viewport");
    }
}
