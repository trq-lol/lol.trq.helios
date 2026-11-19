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
    void baselineIsGlsl120() {
        assertEquals(120, Helios.GLSL_VERSION);
    }
}
