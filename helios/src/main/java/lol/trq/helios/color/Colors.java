/* SPDX-License-Identifier: MIT */
package lol.trq.helios.color;

/**
 * Packed {@code 0xAARRGGBB} colour maths. Every method is static and allocation-free, except where an output array is
 * passed in.
 *
 * <p>Channels are 0-255 ints or 0-1 floats as named. Mixing happens in sRGB unless a method says linear.
 */
public final class Colors {
    private Colors() {}

    /**
     * Packs channels.
     *
     * @param r red 0-255
     * @param g green 0-255
     * @param b blue 0-255
     * @param a alpha 0-255
     * @return the colour; channels are clamped
     */
    public static int argb(int r, int g, int b, int a) {
        return clamp8(a) << 24 | clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b);
    }

    /**
     * Packs float channels.
     *
     * @param r red 0-1
     * @param g green 0-1
     * @param b blue 0-1
     * @param a alpha 0-1
     * @return the colour; channels are clamped
     */
    public static int argb(float r, float g, float b, float a) {
        return argb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), Math.round(a * 255));
    }

    /**
     * Returns the alpha channel.
     *
     * @param c the colour
     * @return alpha 0-255
     */
    public static int alpha(int c) {
        return c >>> 24;
    }

    /**
     * Returns the red channel.
     *
     * @param c the colour
     * @return red 0-255
     */
    public static int red(int c) {
        return (c >>> 16) & 0xFF;
    }

    /**
     * Returns the green channel.
     *
     * @param c the colour
     * @return green 0-255
     */
    public static int green(int c) {
        return (c >>> 8) & 0xFF;
    }

    /**
     * Returns the blue channel.
     *
     * @param c the colour
     * @return blue 0-255
     */
    public static int blue(int c) {
        return c & 0xFF;
    }

    /**
     * Replaces the alpha channel.
     *
     * @param c the colour
     * @param alpha the new alpha 0-1
     * @return the colour with that alpha
     */
    public static int withAlpha(int c, float alpha) {
        return (c & 0x00FFFFFF) | clamp8(Math.round(alpha * 255)) << 24;
    }

    /**
     * Scales the alpha channel, for fading something that already has an alpha.
     *
     * @param c the colour
     * @param factor the multiplier 0-1
     * @return the faded colour
     */
    public static int multiplyAlpha(int c, float factor) {
        return (c & 0x00FFFFFF) | clamp8(Math.round(alpha(c) * factor)) << 24;
    }

    /**
     * Mixes two colours per channel, alpha included, in sRGB.
     *
     * @param a the colour at 0
     * @param b the colour at 1
     * @param t the mix, clamped to {@code [0, 1]}
     * @return the mixed colour
     */
    public static int lerp(int a, int b, float t) {
        float k = Math.min(Math.max(t, 0f), 1f);
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }

    /**
     * Mixes two colours in linear light, which keeps the midpoint of a red to green blend from going muddy. Alpha
     * mixes linearly.
     *
     * @param a the colour at 0
     * @param b the colour at 1
     * @param t the mix, clamped to {@code [0, 1]}
     * @return the mixed colour
     */
    public static int lerpLinear(int a, int b, float t) {
        float k = Math.min(Math.max(t, 0f), 1f);
        float r = toSrgb(toLinear(red(a)) + (toLinear(red(b)) - toLinear(red(a))) * k);
        float g = toSrgb(toLinear(green(a)) + (toLinear(green(b)) - toLinear(green(a))) * k);
        float bl = toSrgb(toLinear(blue(a)) + (toLinear(blue(b)) - toLinear(blue(a))) * k);
        float al = (alpha(a) + (alpha(b) - alpha(a)) * k) / 255f;
        return argb(r, g, bl, al);
    }

    /**
     * Mixes two colours through HSB, taking the shorter way round the hue circle, so a red to blue blend passes
     * through magenta rather than grey.
     *
     * @param a the colour at 0
     * @param b the colour at 1
     * @param t the mix, clamped to {@code [0, 1]}
     * @return the mixed colour
     */
    public static int lerpHsb(int a, int b, float t) {
        float k = Math.min(Math.max(t, 0f), 1f);
        float ha = hue(a);
        float hb = hue(b);
        float dh = hb - ha;
        if (dh > 0.5f) {
            dh -= 1f;
        } else if (dh < -0.5f) {
            dh += 1f;
        }
        float h = ha + dh * k;
        h -= (float) Math.floor(h);
        float s = saturation(a) + (saturation(b) - saturation(a)) * k;
        float v = brightness(a) + (brightness(b) - brightness(a)) * k;
        float al = (alpha(a) + (alpha(b) - alpha(a)) * k) / 255f;
        return hsb(h, s, v, al);
    }

    /**
     * Builds a colour from hue, saturation and brightness.
     *
     * @param hue hue, any value; whole turns wrap
     * @param saturation saturation 0-1
     * @param brightness brightness 0-1
     * @param alpha alpha 0-1
     * @return the colour
     */
    public static int hsb(float hue, float saturation, float brightness, float alpha) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float s = Math.min(Math.max(saturation, 0f), 1f);
        float v = Math.min(Math.max(brightness, 0f), 1f);
        float f = h - (float) Math.floor(h);
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float u = v * (1 - s * (1 - f));
        return switch ((int) h % 6) {
            case 0 -> argb(v, u, p, alpha);
            case 1 -> argb(q, v, p, alpha);
            case 2 -> argb(p, v, u, alpha);
            case 3 -> argb(p, q, v, alpha);
            case 4 -> argb(u, p, v, alpha);
            default -> argb(v, p, q, alpha);
        };
    }

    /**
     * Returns the hue.
     *
     * @param c the colour
     * @return hue in {@code [0, 1)}; 0 for greys
     */
    public static float hue(int c) {
        int r = red(c);
        int g = green(c);
        int b = blue(c);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max == min) {
            return 0f;
        }
        float d = max - min;
        float h;
        if (max == r) {
            h = (g - b) / d;
        } else if (max == g) {
            h = 2f + (b - r) / d;
        } else {
            h = 4f + (r - g) / d;
        }
        h /= 6f;
        return h < 0 ? h + 1f : h;
    }

    /**
     * Returns the HSB saturation.
     *
     * @param c the colour
     * @return saturation 0-1
     */
    public static float saturation(int c) {
        int max = Math.max(red(c), Math.max(green(c), blue(c)));
        int min = Math.min(red(c), Math.min(green(c), blue(c)));
        return max == 0 ? 0f : (max - min) / (float) max;
    }

    /**
     * Returns the HSB brightness.
     *
     * @param c the colour
     * @return brightness 0-1
     */
    public static float brightness(int c) {
        return Math.max(red(c), Math.max(green(c), blue(c))) / 255f;
    }

    /**
     * Builds a colour from hue, saturation and lightness.
     *
     * @param hue hue, any value; whole turns wrap
     * @param saturation HSL saturation 0-1
     * @param lightness lightness 0-1
     * @param alpha alpha 0-1
     * @return the colour
     */
    public static int hsl(float hue, float saturation, float lightness, float alpha) {
        float l = Math.min(Math.max(lightness, 0f), 1f);
        float s = Math.min(Math.max(saturation, 0f), 1f);
        float v = l + s * Math.min(l, 1 - l);
        float sv = v == 0 ? 0 : 2 * (1 - l / v);
        return hsb(hue, sv, v, alpha);
    }

    /**
     * Returns the HSL lightness.
     *
     * @param c the colour
     * @return lightness 0-1
     */
    public static float lightness(int c) {
        int max = Math.max(red(c), Math.max(green(c), blue(c)));
        int min = Math.min(red(c), Math.min(green(c), blue(c)));
        return (max + min) / 510f;
    }

    /**
     * Scales the RGB channels, keeping alpha.
     *
     * @param c the colour
     * @param factor below 1 darkens, above 1 brightens
     * @return the scaled colour
     */
    public static int scale(int c, float factor) {
        return argb(Math.round(red(c) * factor), Math.round(green(c) * factor), Math.round(blue(c) * factor), alpha(c));
    }

    /**
     * Inverts the RGB channels, keeping alpha.
     *
     * @param c the colour
     * @return the opposite colour
     */
    public static int invert(int c) {
        return c ^ 0x00FFFFFF;
    }

    /**
     * Returns the relative luminance (Rec. 709 weights on linear light).
     *
     * @param c the colour
     * @return luminance 0-1
     */
    public static float luminance(int c) {
        return 0.2126f * toLinear(red(c)) + 0.7152f * toLinear(green(c)) + 0.0722f * toLinear(blue(c));
    }

    /**
     * Picks black or white text for a background, whichever contrasts more.
     *
     * @param background the background colour
     * @return {@code 0xFF000000} or {@code 0xFFFFFFFF}
     */
    public static int readableOn(int background) {
        return luminance(background) > 0.179f ? 0xFF000000 : 0xFFFFFFFF;
    }

    /**
     * A colour cycling through the hue circle over time.
     *
     * @param timeMillis the current time
     * @param periodMillis how long one full cycle takes
     * @param offset phase offset 0-1, such as {@code index * 0.05f} for a wave down a list
     * @param saturation saturation 0-1
     * @param brightness brightness 0-1
     * @param alpha alpha 0-1
     * @return the colour
     */
    public static int rainbow(
            long timeMillis, long periodMillis, float offset, float saturation, float brightness, float alpha) {
        float phase = (float) Math.floorMod(timeMillis, periodMillis) / periodMillis + offset;
        return hsb(phase, saturation, brightness, alpha);
    }

    /**
     * A colour sweeping from one colour to another and back over time.
     *
     * @param timeMillis the current time
     * @param periodMillis how long a full there-and-back takes
     * @param offset phase offset 0-1
     * @param a the first colour
     * @param b the second colour
     * @return the colour
     */
    public static int pingPong(long timeMillis, long periodMillis, float offset, int a, int b) {
        float phase = (float) Math.floorMod(timeMillis, periodMillis) / periodMillis + offset;
        phase -= (float) Math.floor(phase);
        float t = phase < 0.5f ? phase * 2 : 2 - phase * 2;
        return lerp(a, b, t);
    }

    /**
     * Parses {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}, with or without the {@code #}.
     *
     * @param hex the text
     * @return the colour; opaque unless alpha is given
     * @throws IllegalArgumentException if the text is not a colour
     */
    public static int parse(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return switch (s.length()) {
                case 3 -> {
                    int v = Integer.parseInt(s, 16);
                    int r = (v >> 8) & 0xF;
                    int g = (v >> 4) & 0xF;
                    int b = v & 0xF;
                    yield 0xFF000000 | (r * 17) << 16 | (g * 17) << 8 | b * 17;
                }
                case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
                case 8 -> (int) Long.parseLong(s, 16);
                default -> throw new IllegalArgumentException("not a colour: " + hex);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a colour: " + hex, e);
        }
    }

    /**
     * Formats a colour as {@code #AARRGGBB}, or {@code #RRGGBB} when opaque.
     *
     * @param c the colour
     * @return the text, upper case
     */
    public static String format(int c) {
        return alpha(c) == 0xFF ? String.format("#%06X", c & 0xFFFFFF) : String.format("#%08X", c & 0xFFFFFFFFL);
    }

    /**
     * Converts an 8-bit sRGB channel to linear light.
     *
     * @param channel 0-255
     * @return linear value 0-1
     */
    public static float toLinear(int channel) {
        float c = channel / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    /**
     * Converts linear light to sRGB.
     *
     * @param linear 0-1
     * @return sRGB value 0-1
     */
    public static float toSrgb(float linear) {
        float c = Math.min(Math.max(linear, 0f), 1f);
        return c <= 0.0031308f ? c * 12.92f : 1.055f * (float) Math.pow(c, 1 / 2.4f) - 0.055f;
    }

    private static int clamp8(int v) {
        return Math.min(Math.max(v, 0), 255);
    }
}
