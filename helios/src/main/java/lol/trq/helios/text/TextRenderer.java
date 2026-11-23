/* SPDX-License-Identifier: MIT */
package lol.trq.helios.text;

import lol.trq.helios.batch.Paint;
import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.gl.GlBackend;

/**
 * Lays out and draws single lines of left-to-right text with kerning, through a {@link ShapeBatch}, so text and
 * shapes share draw calls and stay in order.
 *
 * <p>With formatting on, {@code §} followed by a code changes the look of what follows: {@code 0}-{@code 9} and
 * {@code a}-{@code f} set a colour (keeping the style's alpha) and clear the formats, {@code l} bold, {@code o}
 * italic, {@code n} underline, {@code m} strikethrough, {@code k} obfuscated, {@code r} reset. Codes are not drawn and
 * take no space. Positions are the top left of the line box; the baseline is {@code ascent * size} below it and is
 * snapped to the pixel grid.
 */
public final class TextRenderer {
    /** The 16 colours of the {@code §0}-{@code §f} codes. */
    public static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    static final float BOLD_WEIGHT = 0.025f;
    static final float BOLD_ADVANCE = 0.02f;
    static final float ITALIC_SHEAR = 0.2f;

    private final GlBackend gl;

    /**
     * Creates a renderer.
     *
     * @param gl the backend, for uploading font atlases
     */
    public TextRenderer(GlBackend gl) {
        this.gl = gl;
    }

    /**
     * Draws a line of text into a batch that has begun.
     *
     * @param batch the batch
     * @param text the text
     * @param x left
     * @param y top of the line box
     * @param style the style
     * @return the advance width of the text in drawing units
     */
    public float draw(ShapeBatch batch, CharSequence text, float x, float y, TextStyle style) {
        if (!batch.drawing()) {
            throw new IllegalStateException("begin the batch before drawing text");
        }
        float pixel = batch.pixel();
        float baseline = snap(y + style.font.ascent() * style.size, pixel);
        float left = snap(x, pixel);
        switch (style.shadow) {
            case CLASSIC -> {
                float off = style.size / 9f;
                layout(batch, text, left + off, baseline + off, style, true, 0);
            }
            case SOFT ->
                layout(
                        batch,
                        text,
                        left + style.size * 0.04f,
                        baseline + style.size * 0.06f,
                        style,
                        true,
                        Math.max(1.5f, style.size * 0.12f / pixel));
            default -> {}
        }
        return layout(batch, text, left, baseline, style, false, 0);
    }

    /**
     * Measures a line of text.
     *
     * @param text the text
     * @param style the style
     * @return the advance width in drawing units
     */
    public float width(CharSequence text, TextStyle style) {
        return layout(null, text, 0, 0, style, false, 0);
    }

    /**
     * Removes formatting codes.
     *
     * @param text text with {@code §} codes
     * @return the text without them
     */
    public static String strip(CharSequence text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private float layout(
            ShapeBatch batch,
            CharSequence text,
            float x,
            float baseline,
            TextStyle style,
            boolean shadow,
            float softness) {
        MsdfFont primary = style.font;
        MsdfFont font = primary;
        float size = style.size;
        int baseColor = style.color;
        int color = baseColor;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean obfuscated = false;
        float weightField = style.weight * font.emSize() / font.pxRange();
        float pen = x;
        int prev = -1;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            int cp = Character.codePointAt(text, i);
            if (Character.charCount(cp) == 2) {
                i++;
            }
            if (style.formatting && cp == '§' && i + 1 < n) {
                int code = Character.toLowerCase(text.charAt(++i));
                int idx = "0123456789abcdef".indexOf(code);
                if (idx >= 0) {
                    color = (baseColor & 0xFF000000) | PALETTE[idx];
                    bold = italic = underline = strike = obfuscated = false;
                } else {
                    switch (code) {
                        case 'l' -> bold = true;
                        case 'o' -> italic = true;
                        case 'n' -> underline = true;
                        case 'm' -> strike = true;
                        case 'k' -> obfuscated = true;
                        case 'r' -> {
                            color = baseColor;
                            bold = italic = underline = strike = obfuscated = false;
                        }
                        default -> {}
                    }
                }
                continue;
            }
            MsdfFont.Glyph g = primary.glyphOrNull(cp);
            MsdfFont glyphFont = primary;
            if (g == null && style.fallback != null) {
                MsdfFont other = style.fallback.font(cp);
                if (other != null) {
                    g = other.glyphOrNull(cp);
                    glyphFont = other;
                }
            }
            if (g == null) {
                g = primary.glyph(cp);
                glyphFont = primary;
            }
            if (g == null) {
                continue;
            }
            if (glyphFont != font) {
                font = glyphFont;
                weightField = style.weight * font.emSize() / font.pxRange();
                prev = -1;
            }
            if (obfuscated) {
                g = Obfuscation.replace(font, g, i);
            }
            if (prev >= 0) {
                pen += font.kerning(prev, g.codepoint()) * size;
            }
            float advance = (g.advance() + style.letterSpacing + (bold ? BOLD_ADVANCE : 0)) * size;
            if (batch != null) {
                int c = shadow ? style.shadowColorFor(color) : color;
                if (g.visible()) {
                    float shear = italic ? ITALIC_SHEAR * size : 0;
                    float top = g.top() * size;
                    float bottom = g.bottom() * size;
                    float weight = weightField + (bold ? BOLD_WEIGHT * font.emSize() / font.pxRange() : 0);
                    batch.glyph(
                            font.texture(gl),
                            pen + g.left() * size,
                            baseline + top,
                            pen + g.right() * size,
                            baseline + bottom,
                            -top / size * shear,
                            -bottom / size * shear,
                            g.u0(),
                            g.v0(),
                            g.u1(),
                            g.v1(),
                            c,
                            font.pxRange(),
                            weight,
                            softness,
                            font.atlasWidth(),
                            font.atlasHeight());
                }
                float thickness = Math.max(size * 0.06f, batch.pixel());
                if (underline) {
                    batch.rect(pen, baseline + size * 0.09f, advance, thickness, 0, Paint.solid(c));
                }
                if (strike) {
                    batch.rect(pen, baseline - size * 0.3f, advance, thickness, 0, Paint.solid(c));
                }
            }
            pen += advance;
            prev = g.codepoint();
        }
        return pen - x;
    }

    private static float snap(float v, float pixel) {
        return Math.round(v / pixel) * pixel;
    }
}
