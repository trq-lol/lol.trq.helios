/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fontgen;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Command line: builds an {@code .hfnt} MSDF atlas from a TrueType or OpenType font.
 *
 * <pre>
 * FontGen --font Inter-Regular.ttf --out inter.hfnt [--name inter] [--size 40] [--range 4] [--png preview.png]
 * </pre>
 */
public final class FontGen {
    private FontGen() {}

    public static void main(String[] args) throws IOException, FontFormatException {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("expected --option value pairs, got " + args[i]);
            }
            opts.put(args[i].substring(2), args[i + 1]);
        }
        Path fontPath = Path.of(require(opts, "font"));
        Path out = Path.of(require(opts, "out"));
        String name =
                opts.getOrDefault("name", fontPath.getFileName().toString().replaceFirst("\\.[^.]+$", ""));
        float size = Float.parseFloat(opts.getOrDefault("size", "40"));
        float range = Float.parseFloat(opts.getOrDefault("range", "4"));

        Font font;
        try (var in = Files.newInputStream(fontPath)) {
            font = Font.createFont(Font.TRUETYPE_FONT, in);
        }
        long start = System.nanoTime();
        AtlasBuilder.Atlas atlas = AtlasBuilder.build(font, name, size, range, AtlasBuilder.defaultCharset());
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream o = Files.newOutputStream(out)) {
            AtlasBuilder.write(atlas, o);
        }
        if (opts.containsKey("png")) {
            ImageIO.write(preview(atlas), "png", Path.of(opts.get("png")).toFile());
        }
        System.out.printf(
                "fontgen: %s, %d glyphs, %d kerning pairs, atlas %dx%d, %.0f ms%n",
                name,
                atlas.glyphs().size(),
                atlas.kerning().size(),
                atlas.width(),
                atlas.height(),
                (System.nanoTime() - start) / 1e6);
    }

    static BufferedImage preview(AtlasBuilder.Atlas atlas) {
        BufferedImage img = new BufferedImage(atlas.width(), atlas.height(), BufferedImage.TYPE_INT_RGB);
        byte[] rgb = atlas.rgb();
        for (int y = 0; y < atlas.height(); y++) {
            for (int x = 0; x < atlas.width(); x++) {
                int i = (y * atlas.width() + x) * 3;
                img.setRGB(x, y, (rgb[i] & 0xFF) << 16 | (rgb[i + 1] & 0xFF) << 8 | (rgb[i + 2] & 0xFF));
            }
        }
        return img;
    }

    private static String require(Map<String, String> opts, String key) {
        String v = opts.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing --" + key);
        }
        return v;
    }
}
