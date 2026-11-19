/* SPDX-License-Identifier: MIT */
package lol.trq.helios.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import lol.trq.helios.FakeGl;
import lol.trq.helios.Helios;
import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.shader.ShaderLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TextTest {
    private static MsdfFont inter;
    private final FakeGl gl = new FakeGl();
    private final ShapeBatch batch = new ShapeBatch(gl, gl, new ShaderLoader(gl, Helios.builtinShaders()), 512);
    private final TextRenderer text = new TextRenderer(gl);

    @BeforeAll
    static void load() {
        inter = MsdfFont.resource(TextTest.class.getClassLoader(), Helios.DEFAULT_FONT);
    }

    private static TextStyle style(float size) {
        return TextStyle.builder(inter).size(size).build();
    }

    /** Decodes the glyph quads of the last flush: {kind, x0, y0, colorArgb}. */
    private List<float[]> quads() {
        ByteBuffer b = gl.streamed.get(gl.streamed.size() - 1).duplicate().order(ByteOrder.nativeOrder());
        List<float[]> out = new ArrayList<>();
        while (b.remaining() >= 4 * ShapeBatch.STRIDE) {
            int start = b.position();
            float x = b.getFloat(start);
            float y = b.getFloat(start + 4);
            float kind = b.getFloat(start + 28);
            int r = b.get(start + 56) & 0xFF;
            int g = b.get(start + 57) & 0xFF;
            int bl = b.get(start + 58) & 0xFF;
            int a = b.get(start + 59) & 0xFF;
            out.add(new float[] {kind, x, y, Float.intBitsToFloat(a << 24 | r << 16 | g << 8 | bl)});
            b.position(start + 4 * ShapeBatch.STRIDE);
        }
        return out;
    }

    @Test
    void bundledFontLoads() {
        assertEquals("inter", inter.name());
        assertEquals(512, inter.atlasWidth());
        assertTrue(inter.ascent() > 0.8f && inter.ascent() < 1.1f, "ascent " + inter.ascent());
        assertTrue(inter.lineHeight() > inter.ascent());
        for (char c = 32; c < 127; c++) {
            assertNotNull(inter.glyphOrNull(c), "missing " + c);
        }
        assertNotNull(inter.glyphOrNull(0x20AC));
        assertTrue(inter.kerning('A', 'V') < 0);
        assertEquals(0f, inter.kerning('a', 'a'), 1e-6f);
    }

    @Test
    void missingGlyphFallsBack() {
        MsdfFont.Glyph replacement = inter.glyphOrNull(0xFFFD);
        assertSame(replacement != null ? replacement : inter.glyphOrNull('?'), inter.glyph(0x4E00));
    }

    @Test
    void corruptFileIsRejected() {
        assertThrows(java.io.IOException.class, () -> MsdfFont.read(new ByteArrayInputStream(new byte[16])));
    }

    @Test
    void widthIsAdvancesPlusKerningScaledBySize() {
        float a = inter.glyph('A').advance();
        float v = inter.glyph('V').advance();
        float expected = (a + v + inter.kerning('A', 'V')) * 20;
        assertEquals(expected, text.width("AV", style(20)), 1e-4f);
        assertEquals(2 * text.width("AV", style(10)), text.width("AV", style(20)), 1e-4f);
    }

    @Test
    void formattingCodesTakeNoSpaceAndCanBeTurnedOff() {
        assertEquals(text.width("Hello", style(9)), text.width("§aHel§llo§r", style(9)) - 2 * 0.02f * 9, 1e-4f);
        TextStyle raw = TextStyle.builder(inter).formatting(false).build();
        assertTrue(text.width("§aHi", raw) > text.width("Hi", raw));
        assertEquals("Hello world", TextRenderer.strip("§cHello §lworld"));
    }

    @Test
    void drawsOneQuadPerVisibleGlyphAndReturnsTheWidth() {
        MsdfFont fresh = MsdfFont.resource(TextTest.class.getClassLoader(), Helios.DEFAULT_FONT);
        TextStyle s = TextStyle.builder(fresh).size(9).build();
        batch.begin(0.5f);
        float w = text.draw(batch, "Hi there", 10, 20, s);
        batch.end();
        assertEquals(text.width("Hi there", s), w, 1e-4f);
        assertEquals(7, quads().size());
        assertEquals(1, batch.drawCalls());
        assertTrue(gl.calls.contains("createTexture 512x512"));
    }

    @Test
    void colourCodesRecolourAndKeepAlpha() {
        TextStyle s = TextStyle.builder(inter).color(0x80FFFFFF).build();
        batch.begin(1);
        text.draw(batch, "a§cb§rc", 0, 0, s);
        batch.end();
        List<float[]> q = quads();
        assertEquals(0x80FFFFFF, Float.floatToRawIntBits(q.get(0)[3]));
        assertEquals(0x80FF5555, Float.floatToRawIntBits(q.get(1)[3]));
        assertEquals(0x80FFFFFF, Float.floatToRawIntBits(q.get(2)[3]));
    }

    @Test
    void classicShadowDrawsADarkerCopyFirst() {
        TextStyle s = TextStyle.builder(inter)
                .size(9)
                .color(0xFFFFFFFF)
                .shadow(TextStyle.Shadow.CLASSIC)
                .build();
        batch.begin(1);
        text.draw(batch, "x", 0, 0, s);
        batch.end();
        List<float[]> q = quads();
        assertEquals(2, q.size());
        assertEquals(0xFF3F3F3F, Float.floatToRawIntBits(q.get(0)[3]));
        assertEquals(q.get(1)[1] + 1, q.get(0)[1], 1e-4f);
    }

    @Test
    void underlineAndStrikeAddRects() {
        batch.begin(1);
        text.draw(batch, "§nab§mc", 0, 0, style(9));
        batch.end();
        // Underline runs under a, b and c (strikethrough does not reset it); strikethrough under c.
        long rects = quads().stream().filter(v -> v[0] == 0f).count();
        assertEquals(4, rects);
    }

    @Test
    void baselineIsSnappedToThePixelGrid() {
        batch.begin(0.5f);
        text.draw(batch, "H", 0.3f, 0.3f, style(9));
        batch.end();
        float baseline = Math.round((0.3f + inter.ascent() * 9) / 0.5f) * 0.5f;
        float y0 = quads().get(0)[2];
        assertEquals(baseline + inter.glyph('H').top() * 9, y0, 1e-4f);
    }

    @Test
    void obfuscatedKeepsTheAdvance() {
        assertEquals(text.width("Hello", style(9)), text.width("§kHello", style(9)), 0.6f);
    }

    @Test
    void drawingWithoutBeginThrows() {
        assertThrows(IllegalStateException.class, () -> text.draw(batch, "x", 0, 0, style(9)));
    }
}
