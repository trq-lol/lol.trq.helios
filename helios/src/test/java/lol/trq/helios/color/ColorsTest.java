/* SPDX-License-Identifier: MIT */
package lol.trq.helios.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorsTest {
    @Test
    void packAndUnpack() {
        int c = Colors.argb(0x12, 0x34, 0x56, 0x78);
        assertEquals(0x78123456, c);
        assertEquals(0x78, Colors.alpha(c));
        assertEquals(0x12, Colors.red(c));
        assertEquals(0x34, Colors.green(c));
        assertEquals(0x56, Colors.blue(c));
        assertEquals(0xFFFF0000, Colors.argb(300, -5, 0, 255));
        assertEquals(0xFF0080FF, Colors.argb(0f, 0.5f, 1f, 1f));
    }

    @Test
    void alphaHelpers() {
        assertEquals(0x80FFFFFF, Colors.withAlpha(0xFFFFFFFF, 128 / 255f));
        assertEquals(0x40123456, Colors.multiplyAlpha(0x80123456, 0.5f));
    }

    @Test
    void lerpEndsAndMiddle() {
        assertEquals(0xFF000000, Colors.lerp(0xFF000000, 0xFFFFFFFF, 0));
        assertEquals(0xFFFFFFFF, Colors.lerp(0xFF000000, 0xFFFFFFFF, 1));
        assertEquals(0xFF808080, Colors.lerp(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFFFFFFFF, Colors.lerp(0xFF000000, 0xFFFFFFFF, 7));
    }

    @Test
    void linearMixIsBrighterThanSrgbMix() {
        int srgb = Colors.lerp(0xFFFF0000, 0xFF00FF00, 0.5f);
        int linear = Colors.lerpLinear(0xFFFF0000, 0xFF00FF00, 0.5f);
        assertTrue(Colors.red(linear) > Colors.red(srgb));
        assertEquals(Colors.red(linear), Colors.green(linear));
        assertEquals(0xFFFF0000, Colors.lerpLinear(0xFFFF0000, 0xFF00FF00, 0));
    }

    @Test
    void hsbRoundTrips() {
        for (int c : new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF3B82F6, 0xFF808080, 0xFFFFC857}) {
            int back = Colors.hsb(Colors.hue(c), Colors.saturation(c), Colors.brightness(c), 1);
            for (int shift = 0; shift < 24; shift += 8) {
                assertEquals((c >>> shift) & 0xFF, (back >>> shift) & 0xFF, 1, Integer.toHexString(c));
            }
        }
        assertEquals(0xFF00FFFF, Colors.hsb(0.5f, 1, 1, 1));
        assertEquals(Colors.hsb(0.25f, 1, 1, 1), Colors.hsb(3.25f, 1, 1, 1));
    }

    @Test
    void hslMatchesKnownValues() {
        assertEquals(0xFFFF0000, Colors.hsl(0, 1, 0.5f, 1));
        assertEquals(0xFFFFFFFF, Colors.hsl(0, 1, 1, 1));
        assertEquals(0xFF000000, Colors.hsl(0.3f, 1, 0, 1));
        assertEquals(0.5f, Colors.lightness(0xFFFF0000), 1e-3f);
    }

    @Test
    void hueLerpTakesTheShortWay() {
        int redToBlue = Colors.lerpHsb(0xFFFF0000, 0xFF0000FF, 0.5f);
        assertEquals(0xFFFF00FF, redToBlue);
    }

    @Test
    void readableTextPicksContrast() {
        assertEquals(0xFF000000, Colors.readableOn(0xFFFFFFFF));
        assertEquals(0xFFFFFFFF, Colors.readableOn(0xFF101018));
        assertEquals(0xFF000000, Colors.readableOn(0xFFFFFF55));
    }

    @Test
    void rainbowCyclesAndPingPongReturns() {
        assertEquals(Colors.rainbow(0, 1000, 0, 1, 1, 1), Colors.rainbow(1000, 1000, 0, 1, 1, 1));
        assertEquals(0xFF00FFFF, Colors.rainbow(500, 1000, 0, 1, 1, 1));
        assertEquals(0xFFFFFFFF, Colors.pingPong(500, 1000, 0, 0xFF000000, 0xFFFFFFFF));
        assertEquals(0xFF000000, Colors.pingPong(1000, 1000, 0, 0xFF000000, 0xFFFFFFFF));
    }

    @Test
    void parseAndFormat() {
        assertEquals(0xFFFF8800, Colors.parse("#F80"));
        assertEquals(0xFF3B82F6, Colors.parse("3b82f6"));
        assertEquals(0x803B82F6, Colors.parse("#803B82F6"));
        assertEquals("#3B82F6", Colors.format(0xFF3B82F6));
        assertEquals("#803B82F6", Colors.format(0x803B82F6));
        assertThrows(IllegalArgumentException.class, () -> Colors.parse("#12345"));
        assertThrows(IllegalArgumentException.class, () -> Colors.parse("#GGGGGG"));
    }

    @Test
    void scaleAndInvert() {
        assertEquals(0x80404040, Colors.scale(0x80808080, 0.5f));
        assertEquals(0xFFFFFFFF, Colors.scale(0xFFC0C0C0, 2));
        assertEquals(0x80FF00FF, Colors.invert(0x8000FF00));
    }
}
