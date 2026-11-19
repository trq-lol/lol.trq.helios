/* SPDX-License-Identifier: MIT */
package lol.trq.helios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HeliosTest {
    @Test
    void versionIsNeverNull() {
        assertNotNull(Helios.version());
    }

    @Test
    void glslIs410Core() {
        assertEquals("410 core", Helios.GLSL_VERSION);
    }
}
