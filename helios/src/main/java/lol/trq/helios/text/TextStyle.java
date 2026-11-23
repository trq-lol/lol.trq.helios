/* SPDX-License-Identifier: MIT */
package lol.trq.helios.text;

import java.util.Objects;

/**
 * How text is drawn: font, size, colour, shadow, weight, spacing and whether {@code §} formatting codes apply.
 * Immutable; build one with {@link #builder(MsdfFont)} and reuse it.
 */
public final class TextStyle {
    /** Text shadows. */
    public enum Shadow {
        /** No shadow. */
        NONE,
        /** The vanilla look: a copy one size/9 down and right, in the colour darkened to a quarter. */
        CLASSIC,
        /** A blurred dark copy under the text. */
        SOFT
    }

    /** Finds fonts for characters a style's own font lacks. */
    @FunctionalInterface
    public interface Fallback {
        /**
         * Returns a font that has a character.
         *
         * @param codepoint the character
         * @return the font, or null to draw the replacement glyph
         */
        MsdfFont font(int codepoint);
    }

    final MsdfFont font;
    final float size;
    final int color;
    final Shadow shadow;
    final int shadowColor;
    final float weight;
    final float letterSpacing;
    final boolean formatting;
    final Fallback fallback;

    private TextStyle(Builder b) {
        this.font = b.font;
        this.size = b.size;
        this.color = b.color;
        this.shadow = b.shadow;
        this.shadowColor = b.shadowColor;
        this.weight = b.weight;
        this.letterSpacing = b.letterSpacing;
        this.formatting = b.formatting;
        this.fallback = b.fallback;
    }

    /**
     * Starts a style.
     *
     * @param font the font
     * @return a builder with size 9, white, no shadow, regular weight and formatting codes on
     */
    public static Builder builder(MsdfFont font) {
        return new Builder(font);
    }

    /**
     * Returns the font.
     *
     * @return the font
     */
    public MsdfFont font() {
        return font;
    }

    /**
     * Returns the size: the em height in drawing units.
     *
     * @return the size
     */
    public float size() {
        return size;
    }

    /**
     * Returns the distance from one baseline to the next in drawing units.
     *
     * @return the line height
     */
    public float lineHeight() {
        return font.lineHeight() * size;
    }

    /** Accumulates a {@link TextStyle}. */
    public static final class Builder {
        private final MsdfFont font;
        private float size = 9;
        private int color = 0xFFFFFFFF;
        private Shadow shadow = Shadow.NONE;
        private int shadowColor;
        private boolean shadowColorSet;
        private float weight;
        private float letterSpacing;
        private boolean formatting = true;
        private Fallback fallback;

        private Builder(MsdfFont font) {
            this.font = Objects.requireNonNull(font, "font");
        }

        /**
         * Sets the size.
         *
         * @param size the em height in drawing units
         * @return this builder
         */
        public Builder size(float size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the colour.
         *
         * @param argb packed {@code 0xAARRGGBB}
         * @return this builder
         */
        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        /**
         * Sets the shadow.
         *
         * @param shadow the kind
         * @return this builder
         */
        public Builder shadow(Shadow shadow) {
            this.shadow = Objects.requireNonNull(shadow, "shadow");
            return this;
        }

        /**
         * Sets a fixed shadow colour instead of one derived from the text colour.
         *
         * @param argb packed {@code 0xAARRGGBB}
         * @return this builder
         */
        public Builder shadowColor(int argb) {
            this.shadowColor = argb;
            this.shadowColorSet = true;
            return this;
        }

        /**
         * Sets extra stroke weight.
         *
         * @param em thickening in em, such as 0.02 for semibold; negative thins
         * @return this builder
         */
        public Builder weight(float em) {
            this.weight = em;
            return this;
        }

        /**
         * Sets extra space after every character.
         *
         * @param em spacing in em
         * @return this builder
         */
        public Builder letterSpacing(float em) {
            this.letterSpacing = em;
            return this;
        }

        /**
         * Sets whether {@code §} codes change colour and format, or are drawn as text.
         *
         * @param on whether codes apply
         * @return this builder
         */
        public Builder formatting(boolean on) {
            this.formatting = on;
            return this;
        }

        /**
         * Sets where characters the font lacks come from. They share the line's baseline and size. Each switch of font
         * costs a draw call, since every font has its own atlas.
         *
         * @param fallback asked once per missing character per layout, so it should be cheap; null for none
         * @return this builder
         */
        public Builder fallback(Fallback fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * Builds the style.
         *
         * @return the style
         * @throws IllegalArgumentException if the size is not positive
         */
        public TextStyle build() {
            if (!(size > 0)) {
                throw new IllegalArgumentException("size must be positive: " + size);
            }
            if (!shadowColorSet) {
                shadowColor = shadow == Shadow.SOFT ? 0xA0000000 : 0;
            }
            return new TextStyle(this);
        }
    }

    int shadowColorFor(int textColor) {
        if (shadowColor != 0) {
            return shadowColor;
        }
        return (textColor & 0xFF000000) | ((textColor & 0xFCFCFC) >> 2);
    }
}
