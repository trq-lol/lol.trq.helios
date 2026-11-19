/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderLogTest {
    private static final List<String> FILES = List.of("blur.fsh", "lib/sdf.glsl");

    @Test
    void nvidiaStyle() {
        assertEquals(
                "lib/sdf.glsl:7 : error C1008: undefined variable \"q\"",
                ShaderLog.describe("1(7) : error C1008: undefined variable \"q\"", FILES));
    }

    @Test
    void amdIntelAppleStyle() {
        assertEquals(
                "ERROR: blur.fsh:12: 'x' : undeclared identifier\nERROR: lib/sdf.glsl:3: syntax error",
                ShaderLog.describe("ERROR: 0:12: 'x' : undeclared identifier\nERROR: 1:3: syntax error", FILES));
    }

    @Test
    void mesaStyle() {
        assertEquals("blur.fsh:4(9): error: syntax error", ShaderLog.describe("0:4(9): error: syntax error", FILES));
    }

    @Test
    void unknownSourceNumberIsKept() {
        assertEquals("ERROR: 5:1: x", ShaderLog.describe("ERROR: 5:1: x", FILES));
    }

    @Test
    void emptyLog() {
        assertEquals("", ShaderLog.describe(null, FILES));
    }
}
