/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShaderPreprocessorTest {
    private static ShaderPreprocessor of(Map<String, String> files) {
        return new ShaderPreprocessor(files::get);
    }

    @Test
    void versionThenDefinesThenSource() {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("SAMPLES", "8");
        defines.put("USE_FAST", "");
        var out = of(Map.of("a.fsh", "void main() {}\n")).process("a.fsh", defines);
        assertEquals(
                "#version 410 core\n#define SAMPLES 8\n#define USE_FAST\n#line 1 0\nvoid main() {}\n", out.source());
        assertEquals(List.of("a.fsh"), out.files());
    }

    @Test
    void entryMayRepeatTheVersionAndKeepsLineNumbers() {
        var out = of(Map.of("a.fsh", "#version 410  core\nfoo;\n")).process("a.fsh", Map.of());
        assertEquals("#version 410 core\n#line 1 0\n\nfoo;\n", out.source());
    }

    @Test
    void anyOtherVersionIsRejected() {
        for (String v : List.of("#version 120", "#version 150 core", "#version 410", "#version 330 core")) {
            var pre = of(Map.of("a.fsh", v + "\nfoo;\n"));
            assertThrows(ShaderException.class, () -> pre.process("a.fsh", Map.of()), v);
        }
    }

    @Test
    void includeIsWrappedInLineDirectives() {
        var files = Map.of(
                "shaders/a.fsh", "x;\n#include \"lib/b.glsl\"\ny;\n",
                "shaders/lib/b.glsl", "b1;\nb2;\n");
        var out = of(files).process("shaders/a.fsh", Map.of());
        assertEquals("#version 410 core\n#line 1 0\nx;\n#line 1 1\nb1;\nb2;\n#line 3 0\ny;\n", out.source());
        assertEquals(List.of("shaders/a.fsh", "shaders/lib/b.glsl"), out.files());
    }

    @Test
    void angleIncludeResolvesFromRootAndDotDotWorks() {
        var files = Map.of(
                "x/a.fsh", "#include <lib/c.glsl>\n#include \"../lib/d.glsl\"\n",
                "lib/c.glsl", "c;\n",
                "lib/d.glsl", "d;\n");
        var out = of(files).process("x/a.fsh", Map.of());
        assertEquals(List.of("x/a.fsh", "lib/c.glsl", "lib/d.glsl"), out.files());
    }

    @Test
    void fileIsIncludedOnce() {
        var files = Map.of(
                "a.fsh", "#include \"b.glsl\"\n#include \"c.glsl\"\n",
                "b.glsl", "#include \"c.glsl\"\nb;\n",
                "c.glsl", "c;\n");
        String src = of(files).process("a.fsh", Map.of()).source();
        assertEquals(1, src.split("\nc;\n", -1).length - 1);
    }

    @Test
    void cycleIsAnError() {
        var files = Map.of("a.glsl", "#include \"b.glsl\"\n", "b.glsl", "#include \"a.glsl\"\n");
        var e = assertThrows(ShaderException.class, () -> of(files).process("a.glsl", Map.of()));
        assertTrue(
                e.getMessage().contains("a.glsl -> b.glsl -> a.glsl")
                        || e.getMessage().contains("b.glsl -> a.glsl"),
                e.getMessage());
    }

    @Test
    void missingIncludeNamesTheIncluder() {
        var e = assertThrows(
                ShaderException.class,
                () -> of(Map.of("a.fsh", "#include \"nope.glsl\"\n")).process("a.fsh", Map.of()));
        assertTrue(e.getMessage().contains("nope.glsl") && e.getMessage().contains("a.fsh"), e.getMessage());
    }

    @Test
    void versionInIncludeIsAnError() {
        var files = Map.of("a.fsh", "#include \"b.glsl\"\n", "b.glsl", "#version 120\n");
        var e = assertThrows(ShaderException.class, () -> of(files).process("a.fsh", Map.of()));
        assertTrue(e.getMessage().startsWith("b.glsl:1:"), e.getMessage());
    }

    @Test
    void invalidDefineNameIsAnError() {
        assertThrows(ShaderException.class, () -> of(Map.of("a.fsh", "")).process("a.fsh", Map.of("1BAD", "x")));
    }

    @Test
    void includeEscapingRootIsAnError() {
        assertThrows(
                ShaderException.class,
                () -> of(Map.of("a.fsh", "#include \"../x.glsl\"\n")).process("a.fsh", Map.of()));
    }

    @Test
    void crlfIsNormalized() {
        var out = of(Map.of("a.fsh", "a;\r\nb;\r\n")).process("a.fsh", Map.of());
        assertEquals("#version 410 core\n#line 1 0\na;\nb;\n", out.source());
    }

    @Test
    void builtinShadersPreprocess() {
        var pre = new ShaderPreprocessor(lol.trq.helios.Helios.builtinShaders());
        var out = pre.process("shape.fsh", Map.of());
        assertEquals(List.of("shape.fsh", "lib/sdf.glsl"), out.files());
        assertTrue(out.source().contains("helios_sdRoundedRect"));
        pre.process("shape.vsh", Map.of());
        for (String fsh : List.of("kawase_down.fsh", "kawase_up.fsh", "composite.fsh", "fullscreen.vsh")) {
            pre.process("effect/" + fsh, Map.of());
        }
    }
}
