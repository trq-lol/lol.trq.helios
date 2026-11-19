/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MinecraftOptiFineTest {
    @Test
    void withoutOptiFineEverythingFallsBack() {
        MinecraftOptiFine optiFine = new MinecraftOptiFine(MinecraftOptiFineTest.class.getClassLoader());
        assertFalse(optiFine.present());
        assertNull(optiFine.version());
        assertFalse(optiFine.fastRender());
        assertFalse(optiFine.fastRender(true));
    }
}
