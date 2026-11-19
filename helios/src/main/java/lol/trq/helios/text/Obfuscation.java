/* SPDX-License-Identifier: MIT */
package lol.trq.helios.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** The {@code §k} effect: each character is replaced by a random visible glyph of the same advance, ten times a second. */
final class Obfuscation {
    private static final Map<MsdfFont, Map<Integer, MsdfFont.Glyph[]>> BY_ADVANCE = new WeakHashMap<>();

    private Obfuscation() {}

    static MsdfFont.Glyph replace(MsdfFont font, MsdfFont.Glyph glyph, int index) {
        if (!glyph.visible()) {
            return glyph;
        }
        MsdfFont.Glyph[] same = groups(font).get(bucket(glyph.advance()));
        if (same == null || same.length < 2) {
            return glyph;
        }
        long tick = System.nanoTime() / 100_000_000L;
        long h = (tick * 0x9E3779B97F4A7C15L) ^ (index * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        return same[(int) Math.floorMod(h, (long) same.length)];
    }

    private static synchronized Map<Integer, MsdfFont.Glyph[]> groups(MsdfFont font) {
        return BY_ADVANCE.computeIfAbsent(font, f -> {
            Map<Integer, List<MsdfFont.Glyph>> lists = new HashMap<>();
            for (MsdfFont.Glyph g : f.asciiGlyphs()) {
                if (g != null && g.visible() && g.codepoint() > 32 && g.codepoint() < 127) {
                    lists.computeIfAbsent(bucket(g.advance()), k -> new ArrayList<>())
                            .add(g);
                }
            }
            Map<Integer, MsdfFont.Glyph[]> out = new HashMap<>();
            lists.forEach((k, v) -> out.put(k, v.toArray(new MsdfFont.Glyph[0])));
            return out;
        });
    }

    private static int bucket(float advance) {
        return Math.round(advance * 100);
    }
}
