/* SPDX-License-Identifier: MIT */
package lol.trq.helios.text;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import lol.trq.helios.gl.GlBackend;

/**
 * A multi-channel signed distance field font: glyph metrics, kerning and an RGB atlas, read from an {@code .hfnt}
 * file that the {@code fontgen} module writes.
 *
 * <p>Metrics are in em units, y pointing down from the baseline. The atlas texture is uploaded on first use and lives
 * until {@link #close()}.
 */
public final class MsdfFont implements AutoCloseable {
    static final int MAGIC = 0x48464E54;
    static final int VERSION = 1;

    /**
     * One glyph.
     *
     * @param codepoint the character
     * @param advance horizontal advance
     * @param left quad left, from the pen position
     * @param top quad top, from the baseline (negative is above)
     * @param right quad right
     * @param bottom quad bottom
     * @param u0 atlas left in texture coordinates
     * @param v0 atlas top
     * @param u1 atlas right
     * @param v1 atlas bottom
     */
    public record Glyph(
            int codepoint,
            float advance,
            float left,
            float top,
            float right,
            float bottom,
            float u0,
            float v0,
            float u1,
            float v1) {
        /**
         * Returns whether the glyph has anything to draw.
         *
         * @return false for spaces
         */
        public boolean visible() {
            return right > left;
        }
    }

    private final String name;
    private final float emSize;
    private final float pxRange;
    private final float ascent;
    private final float descent;
    private final float lineGap;
    private final int atlasWidth;
    private final int atlasHeight;
    private final Glyph[] ascii = new Glyph[256];
    private final Map<Integer, Glyph> others = new HashMap<>();
    private static final int DENSE = 128;
    private final float[] denseKerning = new float[DENSE * DENSE];
    private final Map<Long, Float> kerning = new HashMap<>();
    private final Glyph fallback;
    private ByteBuffer pixels;
    private int texture;
    private GlBackend uploadedTo;

    private MsdfFont(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("not an .hfnt font");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("unsupported .hfnt version " + version);
        }
        name = in.readUTF();
        emSize = in.readFloat();
        pxRange = in.readFloat();
        ascent = in.readFloat();
        descent = in.readFloat();
        lineGap = in.readFloat();
        atlasWidth = in.readInt();
        atlasHeight = in.readInt();
        int glyphs = in.readInt();
        for (int i = 0; i < glyphs; i++) {
            int cp = in.readInt();
            float advance = in.readFloat();
            float l = in.readFloat();
            float t = in.readFloat();
            float r = in.readFloat();
            float b = in.readFloat();
            int ax = in.readInt();
            int ay = in.readInt();
            int aw = in.readInt();
            int ah = in.readInt();
            Glyph g = new Glyph(
                    cp,
                    advance,
                    l,
                    t,
                    r,
                    b,
                    ax / (float) atlasWidth,
                    ay / (float) atlasHeight,
                    (ax + aw) / (float) atlasWidth,
                    (ay + ah) / (float) atlasHeight);
            if (cp >= 0 && cp < ascii.length) {
                ascii[cp] = g;
            } else {
                others.put(cp, g);
            }
        }
        int pairs = in.readInt();
        for (int i = 0; i < pairs; i++) {
            int a = in.readInt();
            int b = in.readInt();
            float k = in.readFloat();
            if (a >= 0 && a < DENSE && b >= 0 && b < DENSE) {
                denseKerning[a * DENSE + b] = k;
            } else {
                kerning.put(key(a, b), k);
            }
        }
        byte[] packed = new byte[in.readInt()];
        in.readFully(packed);
        byte[] rgb = new byte[atlasWidth * atlasHeight * 3];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            int n = inflater.inflate(rgb);
            if (n != rgb.length) {
                throw new IOException("atlas truncated: " + n + " of " + rgb.length + " bytes");
            }
        } catch (DataFormatException e) {
            throw new IOException("atlas corrupt", e);
        } finally {
            inflater.end();
        }
        pixels = ByteBuffer.allocateDirect(rgb.length).order(ByteOrder.nativeOrder());
        pixels.put(rgb).flip();
        Glyph f = glyphOrNull(0xFFFD);
        fallback = f != null ? f : glyphOrNull('?');
    }

    /**
     * Reads a font.
     *
     * @param in the {@code .hfnt} bytes; not closed
     * @return the font
     * @throws IOException if the data is not a valid font
     */
    public static MsdfFont read(InputStream in) throws IOException {
        return new MsdfFont(new DataInputStream(in));
    }

    /**
     * Reads a font from the class path.
     *
     * @param loader the class loader
     * @param resource the resource name
     * @return the font
     * @throws UncheckedIOException if it is missing or invalid
     */
    public static MsdfFont resource(ClassLoader loader, String resource) {
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("font resource not found: " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the font name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Distance from the baseline up to the top of the tallest glyphs, in em.
     *
     * @return the ascent
     */
    public float ascent() {
        return ascent;
    }

    /**
     * Distance from the baseline down to the bottom of descenders, in em.
     *
     * @return the descent
     */
    public float descent() {
        return descent;
    }

    /**
     * Distance from one baseline to the next, in em.
     *
     * @return the line height
     */
    public float lineHeight() {
        return ascent + descent + lineGap;
    }

    /**
     * The distance range of the atlas in texels.
     *
     * @return the range
     */
    public float pxRange() {
        return pxRange;
    }

    /**
     * Texels per em in the atlas.
     *
     * @return the em size
     */
    public float emSize() {
        return emSize;
    }

    /**
     * Atlas width in texels.
     *
     * @return the width
     */
    public int atlasWidth() {
        return atlasWidth;
    }

    /**
     * Atlas height in texels.
     *
     * @return the height
     */
    public int atlasHeight() {
        return atlasHeight;
    }

    /**
     * Returns a glyph, or the replacement glyph when the font lacks it.
     *
     * @param codepoint the character
     * @return the glyph; never null when the font has a replacement or {@code ?}
     */
    public Glyph glyph(int codepoint) {
        Glyph g = glyphOrNull(codepoint);
        return g != null ? g : fallback;
    }

    /**
     * Returns a glyph without falling back.
     *
     * @param codepoint the character
     * @return the glyph, or null
     */
    public Glyph glyphOrNull(int codepoint) {
        return codepoint >= 0 && codepoint < ascii.length ? ascii[codepoint] : others.get(codepoint);
    }

    /**
     * Returns the kerning between two characters.
     *
     * @param left the first character
     * @param right the second character
     * @return the adjustment in em, usually negative
     */
    public float kerning(int left, int right) {
        if (left >= 0 && left < DENSE && right >= 0 && right < DENSE) {
            return denseKerning[left * DENSE + right];
        }
        if (kerning.isEmpty()) {
            return 0;
        }
        Float k = kerning.get(key(left, right));
        return k == null ? 0 : k;
    }

    /**
     * Every glyph, for tools such as obfuscated text that pick replacements.
     *
     * @return the glyphs below U+0100 that the font has
     */
    Glyph[] asciiGlyphs() {
        return ascii.clone();
    }

    /**
     * Returns the atlas pixels: tightly packed RGB bytes, rows top to bottom.
     *
     * @return a read-only view
     */
    public ByteBuffer atlasPixels() {
        return pixels.asReadOnlyBuffer();
    }

    /**
     * Returns the atlas texture, uploading it on first use.
     *
     * @param gl the backend of the current context
     * @return the texture id
     */
    public int texture(GlBackend gl) {
        if (texture == 0) {
            texture = gl.createRgbTexture(atlasWidth, atlasHeight, pixels.duplicate());
            uploadedTo = gl;
        }
        return texture;
    }

    /** Deletes the atlas texture, if uploaded. The font can upload it again. */
    @Override
    public void close() {
        if (texture != 0) {
            uploadedTo.deleteTexture(texture);
            texture = 0;
        }
    }

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }
}
