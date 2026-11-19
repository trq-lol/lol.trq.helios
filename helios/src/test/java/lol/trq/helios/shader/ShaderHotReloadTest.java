/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import lol.trq.helios.FakeGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderHotReloadTest {
    @TempDir
    Path dir;

    private final FakeGl gl = new FakeGl();
    private long now;

    @Test
    void reloadsWhenAFileChangesAndThrottlesScans() throws Exception {
        Files.writeString(dir.resolve("a.vsh"), "void main() {}\n");
        Files.writeString(dir.resolve("a.fsh"), "void main() {}\n");
        ShaderLoader loader = new ShaderLoader(gl, SourceResolver.directory(dir), 120);
        ShaderProgram p = loader.load("a.vsh", "a.fsh", Map.of());
        ShaderHotReload hot = new ShaderHotReload(loader, dir, 250, () -> now);

        assertFalse(hot.poll().reloaded());
        int first = p.id();

        touch("a.fsh", "void main() { gl_FragColor = vec4(1.0); }\n");
        now += 100_000_000L;
        assertFalse(hot.poll().reloaded(), "scanned again before the interval");
        now += 200_000_000L;
        ShaderHotReload.Result r = hot.poll();
        assertTrue(r.reloaded());
        assertEquals(0, r.failures().size());
        assertNotEquals(first, p.id());
        assertTrue(gl.sources.values().stream().anyMatch(s -> s.contains("vec4(1.0)")));

        now += 300_000_000L;
        assertFalse(hot.poll().reloaded(), "nothing changed");
    }

    @Test
    void brokenEditKeepsTheLastGoodProgram() throws Exception {
        Files.writeString(dir.resolve("a.vsh"), "void main() {}\n");
        Files.writeString(dir.resolve("a.fsh"), "void main() {}\n");
        ShaderLoader loader = new ShaderLoader(gl, SourceResolver.directory(dir), 120);
        ShaderProgram p = loader.load("a.vsh", "a.fsh", Map.of());
        ShaderHotReload hot = new ShaderHotReload(loader, dir, 0, () -> now);
        int good = p.id();
        gl.compiles = s -> !s.contains("broken");
        touch("a.fsh", "broken\n");
        now++;
        ShaderHotReload.Result r = hot.poll();
        assertTrue(r.reloaded());
        assertEquals(1, r.failures().size());
        assertEquals(good, p.id());
    }

    private void touch(String name, String content) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        Files.setLastModifiedTime(
                f, FileTime.fromMillis(Files.getLastModifiedTime(f).toMillis() + 5000));
    }
}
