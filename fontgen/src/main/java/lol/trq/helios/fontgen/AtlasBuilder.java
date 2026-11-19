/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Builds an MSDF font atlas: one field per glyph, packed into an RGB texture, with metrics and kerning.
 *
 * <p>Metrics are stored in em units with y pointing down from the baseline, so a renderer multiplies by its font size.
 */
public final class AtlasBuilder {
    /** File magic: {@code HFNT}. */
    public static final int MAGIC = 0x48464E54;

    /** File format version. */
    public static final int VERSION = 1;

    private static final double ANGLE_THRESHOLD = 3.0;
    private static final int SPACING = 1;

    /** One glyph's metrics and place in the atlas. */
    public record Glyph(
            int codepoint,
            float advance,
            float planeLeft,
            float planeTop,
            float planeRight,
            float planeBottom,
            int atlasX,
            int atlasY,
            int atlasWidth,
            int atlasHeight,
            float[] field) {}

    /** A kerning adjustment between two characters, in em units. */
    public record Kerning(int left, int right, float amount) {}

    /** A finished atlas. */
    public record Atlas(
            String name,
            float emSize,
            float pxRange,
            float ascent,
            float descent,
            float lineGap,
            int width,
            int height,
            List<Glyph> glyphs,
            List<Kerning> kerning,
            byte[] rgb) {}

    private AtlasBuilder() {}

    /**
     * Builds an atlas.
     *
     * @param font the font, any size
     * @param name the name stored in the file
     * @param emSize glyph size in texels per em
     * @param pxRange the distance range in texels
     * @param codepoints the characters to include; ones the font lacks are skipped
     * @return the atlas
     */
    public static Atlas build(Font font, String name, float emSize, float pxRange, int[] codepoints) {
        Font f = font.deriveFont(emSize);
        FontRenderContext frc = new FontRenderContext(null, false, true);
        List<Glyph> glyphs = new ArrayList<>();
        double pad = pxRange / 2.0 + 1;
        for (int cp : codepoints) {
            if (!f.canDisplay(cp)) {
                continue;
            }
            GlyphVector gv = f.createGlyphVector(frc, Character.toChars(cp));
            float advance = gv.getGlyphMetrics(0).getAdvanceX() / emSize;
            Shape outline = gv.getGlyphOutline(0);
            Rectangle2D b = outline.getBounds2D();
            GlyphShape shape = GlyphShape.of(outline);
            if (shape.isEmpty() || b.isEmpty()) {
                glyphs.add(new Glyph(cp, advance, 0, 0, 0, 0, 0, 0, 0, 0, null));
                continue;
            }
            EdgeColoring.simple(shape, ANGLE_THRESHOLD, 0);
            int x0 = (int) Math.floor(b.getMinX() - pad);
            int y0 = (int) Math.floor(b.getMinY() - pad);
            int w = (int) Math.ceil(b.getMaxX() + pad) - x0;
            int h = (int) Math.ceil(b.getMaxY() + pad) - y0;
            float[] field = MsdfGenerator.generate(shape, w, h, x0, y0, pxRange);
            MsdfGenerator.correctErrors(field, w, h, 1.001 / pxRange);
            glyphs.add(new Glyph(
                    cp, advance, x0 / emSize, y0 / emSize, (x0 + w) / emSize, (y0 + h) / emSize, 0, 0, w, h, field));
        }
        List<Glyph> packed = pack(glyphs);
        int width = packed.isEmpty() ? 1 : packed.get(0).atlasX;
        int height = packed.isEmpty() ? 1 : packed.get(0).atlasY;
        packed = packed.subList(1, packed.size());
        byte[] rgb = new byte[width * height * 3];
        for (Glyph g : packed) {
            if (g.field == null) {
                continue;
            }
            for (int y = 0; y < g.atlasHeight; y++) {
                for (int x = 0; x < g.atlasWidth; x++) {
                    int src = (y * g.atlasWidth + x) * 3;
                    int dst = ((g.atlasY + y) * width + g.atlasX + x) * 3;
                    for (int ch = 0; ch < 3; ch++) {
                        rgb[dst + ch] = (byte) Math.max(0, Math.min(255, Math.round(g.field[src + ch] * 255f)));
                    }
                }
            }
        }
        LineMetrics lm = f.getLineMetrics("Hg", frc);
        return new Atlas(
                name,
                emSize,
                pxRange,
                lm.getAscent() / emSize,
                lm.getDescent() / emSize,
                lm.getLeading() / emSize,
                width,
                height,
                packed,
                kerning(f, frc, emSize, glyphs),
                rgb);
    }

    /** Shelf packing, tallest first. The first element of the result only carries the atlas size. */
    private static List<Glyph> pack(List<Glyph> glyphs) {
        List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingInt((Glyph g) -> -g.atlasHeight).thenComparingInt(Glyph::codepoint));
        long area = 0;
        int widest = 1;
        for (Glyph g : sorted) {
            area += (long) (g.atlasWidth + SPACING) * (g.atlasHeight + SPACING);
            widest = Math.max(widest, g.atlasWidth + SPACING);
        }
        int width = Integer.highestOneBit((int) Math.max(widest, Math.ceil(Math.sqrt(area))) - 1) << 1;
        width = Math.max(width, 64);
        List<Glyph> out = new ArrayList<>();
        int x = SPACING;
        int y = SPACING;
        int shelf = 0;
        for (Glyph g : sorted) {
            if (g.field == null) {
                out.add(g);
                continue;
            }
            if (x + g.atlasWidth + SPACING > width) {
                x = SPACING;
                y += shelf + SPACING;
                shelf = 0;
            }
            out.add(new Glyph(
                    g.codepoint,
                    g.advance,
                    g.planeLeft,
                    g.planeTop,
                    g.planeRight,
                    g.planeBottom,
                    x,
                    y,
                    g.atlasWidth,
                    g.atlasHeight,
                    g.field));
            x += g.atlasWidth + SPACING;
            shelf = Math.max(shelf, g.atlasHeight);
        }
        int height = Integer.highestOneBit(Math.max(y + shelf + SPACING, 2) - 1) << 1;
        out.sort(Comparator.comparingInt(Glyph::codepoint));
        out.add(0, new Glyph(-1, 0, 0, 0, 0, 0, width, height, 0, 0, null));
        return out;
    }

    private static List<Kerning> kerning(Font f, FontRenderContext frc, float emSize, List<Glyph> glyphs) {
        Font kerned = f.deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
        List<Kerning> out = new ArrayList<>();
        List<Glyph> letters = glyphs.stream()
                .filter(g -> g.codepoint > 32 && g.codepoint < 0x250)
                .toList();
        for (Glyph a : letters) {
            for (Glyph b : letters) {
                String pair = new String(Character.toChars(a.codepoint)) + new String(Character.toChars(b.codepoint));
                float advance = new TextLayout(pair, kerned, frc).getAdvance() / emSize;
                float k = advance - a.advance - b.advance;
                if (Math.abs(k) > 1e-4f) {
                    out.add(new Kerning(a.codepoint, b.codepoint, k));
                }
            }
        }
        return out;
    }

    /**
     * Writes an atlas in the {@code .hfnt} format that {@code lol.trq.helios.text.MsdfFont} reads.
     *
     * @param atlas the atlas
     * @param out the stream; not closed
     * @throws IOException on a write failure
     */
    public static void write(Atlas atlas, OutputStream out) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(MAGIC);
        d.writeInt(VERSION);
        d.writeUTF(atlas.name);
        d.writeFloat(atlas.emSize);
        d.writeFloat(atlas.pxRange);
        d.writeFloat(atlas.ascent);
        d.writeFloat(atlas.descent);
        d.writeFloat(atlas.lineGap);
        d.writeInt(atlas.width);
        d.writeInt(atlas.height);
        d.writeInt(atlas.glyphs.size());
        for (Glyph g : atlas.glyphs) {
            d.writeInt(g.codepoint);
            d.writeFloat(g.advance);
            d.writeFloat(g.planeLeft);
            d.writeFloat(g.planeTop);
            d.writeFloat(g.planeRight);
            d.writeFloat(g.planeBottom);
            d.writeInt(g.atlasX);
            d.writeInt(g.atlasY);
            d.writeInt(g.atlasWidth);
            d.writeInt(g.atlasHeight);
        }
        d.writeInt(atlas.kerning.size());
        for (Kerning k : atlas.kerning) {
            d.writeInt(k.left);
            d.writeInt(k.right);
            d.writeFloat(k.amount);
        }
        java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(packed, new Deflater(Deflater.BEST_COMPRESSION))) {
            z.write(atlas.rgb);
        }
        d.writeInt(packed.size());
        packed.writeTo(d);
        d.flush();
    }

    /**
     * The default character set: printable ASCII, Latin-1 and common typographic punctuation.
     *
     * @return the code points
     */
    public static int[] defaultCharset() {
        List<Integer> cps = new ArrayList<>();
        for (int c = 32; c < 127; c++) {
            cps.add(c);
        }
        for (int c = 160; c < 256; c++) {
            cps.add(c);
        }
        for (int c :
                new int[] {0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x20AC, 0x2122, 0xFFFD}) {
            cps.add(c);
        }
        return cps.stream().mapToInt(Integer::intValue).toArray();
    }
}
