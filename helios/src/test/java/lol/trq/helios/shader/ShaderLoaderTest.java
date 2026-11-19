/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lol.trq.helios.FakeGl;
import org.junit.jupiter.api.Test;

class ShaderLoaderTest {
    private final FakeGl gl = new FakeGl();
    private final Map<String, String> files = new HashMap<>(Map.of(
            "a.vsh", "void main() {}\n",
            "a.fsh", "#include \"lib.glsl\"\nvoid main() {}\n",
            "lib.glsl", "float f;\n"));
    private final ShaderLoader loader = new ShaderLoader(gl, files::get);

    @Test
    void uniformLocationsAreCachedIncludingMisses() {
        ShaderProgram p = loader.load("a.vsh", "a.fsh", Map.of());
        p.set("u_color", 1f, 0f, 0f, 1f);
        p.set("u_color", 0f, 1f, 0f, 1f);
        p.set("missing_uniform", 2f);
        p.set("missing_uniform", 3f);
        assertEquals(2, gl.uniformLookups);
        assertEquals(2, gl.calls.stream().filter(c -> c.startsWith("uniform4f")).count());
        assertTrue(gl.calls.stream().noneMatch(c -> c.startsWith("uniform1f")));
    }

    @Test
    void compileErrorNamesFileAndLine() {
        gl.compiles = src -> !src.contains("float f;");
        gl.compileLog = "ERROR: 1:1: 'f' : bad\n";
        var e = assertThrows(ShaderException.class, () -> loader.load("a.vsh", "a.fsh", Map.of()));
        assertTrue(e.getMessage().contains("ERROR: lib.glsl:1: 'f' : bad"), e.getMessage());
        assertTrue(gl.liveShaders.isEmpty(), "shaders leaked: " + gl.liveShaders);
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void linkErrorDeletesProgram() {
        gl.links = false;
        var e = assertThrows(ShaderException.class, () -> loader.load("a.vsh", "a.fsh", Map.of()));
        assertTrue(e.getMessage().contains("link error"));
        assertTrue(gl.livePrograms.isEmpty());
        assertTrue(gl.liveShaders.isEmpty());
    }

    @Test
    void definesReachBothStages() {
        loader.load("a.vsh", "a.fsh", Map.of("FAST", "1"));
        assertEquals(
                2,
                gl.sources.values().stream()
                        .filter(s -> s.contains("#define FAST 1"))
                        .count());
    }

    @Test
    void reloadSwapsProgramAndClearsCache() {
        ShaderProgram p = loader.load("a.vsh", "a.fsh", Map.of());
        int first = p.id();
        p.location("u_x");
        assertEquals(List.of(), loader.reloadAll());
        assertNotEquals(first, p.id());
        assertTrue(!gl.livePrograms.contains(first));
        p.location("u_x");
        assertEquals(2, gl.uniformLookups);
    }

    @Test
    void failedReloadKeepsLastGoodProgram() {
        ShaderProgram p = loader.load("a.vsh", "a.fsh", Map.of());
        int first = p.id();
        files.put("lib.glsl", "broken\n");
        gl.compiles = src -> !src.contains("broken");
        List<String> failures = loader.reloadAll();
        assertEquals(1, failures.size());
        assertEquals(first, p.id());
        assertTrue(gl.livePrograms.contains(first));
    }

    @Test
    void closeDeletesAndSkipsReload() {
        ShaderProgram p = loader.load("a.vsh", "a.fsh", Map.of());
        p.close();
        assertEquals(0, p.id());
        assertTrue(gl.livePrograms.isEmpty());
        loader.reloadAll();
        assertTrue(gl.livePrograms.isEmpty());
    }
}
