/* SPDX-License-Identifier: MIT */
package lol.trq.helios.harness.mod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import lol.trq.helios.Helios;
import lol.trq.helios.batch.Paint;
import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.effect.Passes;
import lol.trq.helios.fbo.Framebuffer;
import lol.trq.helios.math.Projector;
import lol.trq.helios.minecraft.MinecraftGlStateCache;
import lol.trq.helios.minecraft.MinecraftWorld;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.ShaderProgram;
import lol.trq.helios.shader.SourceResolver;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;
import lol.trq.helios.text.MsdfFont;
import lol.trq.helios.text.TextStyle;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/**
 * The in-game checks: every primitive against the software {@link Model}, batching, blur and bloom against reference
 * images, the post-processing chain and scissor offscreen, GL state before and after, and cache coherence.
 */
final class Checks {
    private static final int TOLERANCE = 3;
    private static final float PI = (float) Math.PI;

    /** A test shape: its model and how to draw it. */
    record TestShape(Model.Shape model, Consumer<ShapeBatch> draw) {}

    static final List<TestShape> SHAPES = shapes();

    /** Blur region: checkerboard, then a frosted rounded rect inside it. */
    static final float[] BLUR_REGION = {2, 96, 140, 100};

    /** Bloom region: dark panel, glowing shapes. */
    static final float[] BLOOM_REGION = {290, 96, 135, 100};

    /** Text region: dark panel with several sizes and styles. */
    static final float[] TEXT_REGION = {146, 70, 140, 124};

    /** Stencil region: a gradient and text clipped to a pill. */
    static final float[] STENCIL_REGION = {2, 200, 140, 36};

    private static ShaderProgram invert;
    static boolean stencilOnScreen;

    private final Helios helios;
    private final HarnessMod.Frame frame;
    private final File out;
    private final List<String> failures = new ArrayList<>();

    Checks(Helios helios, HarnessMod.Frame frame, File out) {
        this.helios = helios;
        this.frame = frame;
        this.out = out;
    }

    private static List<TestShape> shapes() {
        List<TestShape> list = new ArrayList<>();
        int[] quad = {0xFFE53935, 0xFF43A047, 0xFF1E88E5, 0xFFFDD835};
        list.add(new TestShape(
                box("rect-corners", 8, 8, 60, 30, 8, Model.Kind.RECT, 0, quad, 0),
                b -> b.rect(8, 8, 60, 30, 8, Paint.corners(quad[0], quad[1], quad[2], quad[3]))));
        int[] white = solid(0xFFFFFFFF);
        list.add(new TestShape(
                box("outline", 76, 8, 60, 30, 8, Model.Kind.RECT, 2.5f, white, 0),
                b -> b.outline(76, 8, 60, 30, 8, 2.5f, Paint.solid(0xFFFFFFFF))));
        int[] radial = {0xFFFFFFFF, 0xFF2050FF, 0xFF2050FF, 0xFF2050FF};
        list.add(new TestShape(
                box("circle-radial", 145, 8, 30, 30, 15, Model.Kind.RECT, 0, radial, 15),
                b -> b.circle(160, 23, 15, Paint.radial(0xFFFFFFFF, 0xFF2050FF, 15))));
        int[] orange = solid(0xFFFF8040);
        float start = -PI / 2;
        float sweep = 4.2f;
        list.add(new TestShape(
                new Model.Shape("arc", 200, 23, 1, 0, 15, 15, 0, Model.Kind.ARC, 4, start, sweep, orange, 0),
                b -> b.arc(200, 23, 15, 4, start, sweep, Paint.solid(0xFFFF8040))));
        float x0 = 222;
        float y0 = 10;
        float x1 = 270;
        float y1 = 36;
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int[] lineColors = {0xFF00FFAA, 0xFFAA00FF, 0xFFAA00FF, 0xFF00FFAA};
        list.add(new TestShape(
                new Model.Shape(
                        "line",
                        (x0 + x1) / 2,
                        (y0 + y1) / 2,
                        (x1 - x0) / len,
                        (y1 - y0) / len,
                        len / 2 + 1.5f,
                        1.5f,
                        1.5f,
                        Model.Kind.RECT,
                        0,
                        0,
                        0,
                        lineColors,
                        0),
                b -> b.line(x0, y0, x1, y1, 3, true, Paint.horizontal(0xFF00FFAA, 0xFFAA00FF))));
        int[] shadow = solid(0x99000000);
        list.add(new TestShape(
                box("shadow", 285, 10, 40, 26, 6, Model.Kind.SHADOW, 5, shadow, 0),
                b -> b.shadow(285, 10, 40, 26, 6, 5, 0x99000000)));
        int[] glow = solid(0xFFFFAA00);
        list.add(new TestShape(
                box("glow", 340, 8, 60, 30, 8, Model.Kind.GLOW, 6, glow, 0),
                b -> b.innerGlow(340, 8, 60, 30, 8, 6, 0xFFFFAA00)));
        int[] translucent = solid(0x8040C0FF);
        list.add(new TestShape(
                box("translucent", 8, 44, 60, 20, 10, Model.Kind.RECT, 0, translucent, 0),
                b -> b.rect(8, 44, 60, 20, 10, 0x8040C0FF)));
        return List.copyOf(list);
    }

    private static Model.Shape box(
            String name,
            float x,
            float y,
            float w,
            float h,
            float r,
            Model.Kind kind,
            float param,
            int[] c,
            float rad) {
        return new Model.Shape(name, x + w / 2, y + h / 2, 1, 0, w / 2, h / 2, r, kind, param, 0, 0, c, rad);
    }

    private static int[] solid(int argb) {
        return new int[] {argb, argb, argb, argb};
    }

    static String glInfo() {
        return "\"" + GL11.glGetString(GL11.GL_VERSION) + " / " + GL11.glGetString(GL11.GL_RENDERER) + "\"";
    }

    /** Draws the whole scene. */
    static void drawScene(Helios helios, HarnessMod.Frame frame) {
        ShapeBatch b = helios.shapes().begin(frame.pixel());
        for (TestShape s : SHAPES) {
            s.draw.accept(b);
        }
        b.end();
        drawBlurScene(helios, frame);
        drawBloomScene(helios, frame);
        drawTextScene(helios, frame);
        drawStencilScene(helios, frame);
        drawGlyph(helios, frame);
    }

    static void drawTextScene(Helios helios, HarnessMod.Frame frame) {
        float[] r = TEXT_REGION;
        MsdfFont font = helios.defaultFont();
        ShapeBatch b = helios.shapes().begin(frame.pixel());
        b.rect(r[0], r[1], r[2], r[3], 0, 0xFF14161C);
        float y = r[1] + 4;
        var text = helios.text();
        text.draw(
                b,
                "Helios 6px: the quick brown fox",
                r[0] + 4,
                y,
                TextStyle.builder(font).size(6).build());
        y += 8;
        text.draw(
                b,
                "9px §cred §agreen §9blue §rwhite",
                r[0] + 4,
                y,
                TextStyle.builder(font).size(9).build());
        y += 11;
        text.draw(
                b,
                "§lBold §oitalic§r §nunder§r §mstrike",
                r[0] + 4,
                y,
                TextStyle.builder(font).size(9).shadow(TextStyle.Shadow.CLASSIC).build());
        y += 12;
        text.draw(
                b,
                "Soft shadow AV Wa",
                r[0] + 4,
                y,
                TextStyle.builder(font).size(14).shadow(TextStyle.Shadow.SOFT).build());
        y += 18;
        text.draw(
                b,
                "Big 32",
                r[0] + 4,
                y,
                TextStyle.builder(font).size(32).color(0xFFFFC857).build());
        y += 38;
        text.draw(
                b,
                "Semibold, spaced",
                r[0] + 4,
                y,
                TextStyle.builder(font)
                        .size(11)
                        .weight(0.02f)
                        .letterSpacing(0.05f)
                        .color(0xFF8BE9FD)
                        .build());
        b.end();
    }

    static void drawStencilScene(Helios helios, HarnessMod.Frame frame) {
        float[] r = STENCIL_REGION;
        float p = frame.pixel();
        helios.shapes().begin(p).rect(r[0], r[1], r[2], r[3], 0, 0xFF0B0C10).end();
        if (!stencilOnScreen) {
            return;
        }
        helios.stencil()
                .clip(
                        () -> helios.shapes()
                                .begin(p)
                                .rect(r[0] + 6, r[1] + 4, r[2] - 12, r[3] - 8, 14, Paint.solid(-1))
                                .end(),
                        true,
                        () -> {
                            ShapeBatch b = helios.shapes().begin(p);
                            b.rect(r[0], r[1], r[2], r[3], 0, Paint.linear(30, 0xFFFF4D8D, 0xFF4D9DFF));
                            helios.text()
                                    .draw(
                                            b,
                                            "Clipped by a stencil mask",
                                            r[0] - 4,
                                            r[1] + 10,
                                            TextStyle.builder(helios.defaultFont())
                                                    .size(14)
                                                    .build());
                            b.end();
                        });
    }

    /** One large glyph, drawn straight through the batch so the harness can model every pixel. */
    static final float[] GLYPH = {398, 36, 26};

    static void drawGlyph(Helios helios, HarnessMod.Frame frame) {
        MsdfFont font = helios.defaultFont();
        MsdfFont.Glyph g = font.glyph('g');
        float size = GLYPH[2];
        float x = GLYPH[0];
        float base = GLYPH[1];
        helios.shapes()
                .begin(frame.pixel())
                .glyph(
                        font.texture(helios.gl()),
                        x + g.left() * size,
                        base + g.top() * size,
                        x + g.right() * size,
                        base + g.bottom() * size,
                        0,
                        0,
                        g.u0(),
                        g.v0(),
                        g.u1(),
                        g.v1(),
                        0xFFFFFFFF,
                        font.pxRange(),
                        0,
                        0,
                        font.atlasWidth(),
                        font.atlasHeight())
                .end();
    }

    private static void drawBlurScene(Helios helios, HarnessMod.Frame frame) {
        float[] r = BLUR_REGION;
        ShapeBatch b = helios.shapes().begin(frame.pixel());
        int[] palette = {0xFFE53935, 0xFFFDD835, 0xFF1E88E5, 0xFFFFFFFF, 0xFF212121};
        for (int row = 0; row * 10 < r[3]; row++) {
            for (int col = 0; col * 10 < r[2]; col++) {
                b.rect(r[0] + col * 10, r[1] + row * 10, 10, 10, 0, palette[(col + row * 2) % palette.length]);
            }
        }
        b.end();
        helios.effects().blurBehind(32, 126, 80, 40, 10, frame.pixel(), 2, 2, 0xFFFFFFFF);
        helios.shapes()
                .begin(frame.pixel())
                .outline(32, 126, 80, 40, 10, 1, Paint.solid(0x80FFFFFF))
                .end();
    }

    private static void drawBloomScene(Helios helios, HarnessMod.Frame frame) {
        float[] r = BLOOM_REGION;
        helios.shapes()
                .begin(frame.pixel())
                .rect(r[0], r[1], r[2], r[3], 0, 0xFF101018)
                .end();
        Runnable sources = () -> helios.shapes()
                .begin(frame.pixel())
                .circle(330, 146, 12, Paint.solid(0xFFFF3060))
                .rect(360, 132, 40, 28, 6, Paint.vertical(0xFF30A0FF, 0xFF80FFE0))
                .end();
        helios.effects().bloom(sources, 3, 1.5f, 1.2f);
        sources.run();
    }

    boolean run() {
        out.mkdirs();
        String before = State.capture().toString();

        for (TestShape s : SHAPES) {
            checkShape(s);
        }
        drawBlurScene(helios, frame);
        compareReference("blur", BLUR_REGION);
        drawBloomScene(helios, frame);
        compareReference("bloom", BLOOM_REGION);
        drawTextScene(helios, frame);
        compareReference("text", TEXT_REGION);
        drawStencilScene(helios, frame);
        if (stencilOnScreen) {
            compareReference("stencil", STENCIL_REGION);
        }
        checkGlyph();
        String afterOnScreen = State.capture().toString();
        expect("state unchanged by on-screen drawing", before.equals(afterOnScreen), before + " -> " + afterOnScreen);

        checkOffscreen();
        String afterOffscreen = State.capture().toString();
        expect("state unchanged by offscreen drawing", before.equals(afterOffscreen), before + " -> " + afterOffscreen);

        checkCacheCoherence();
        checkProjection();
        checkWorldHelpers();
        screenshots();
        if (Boolean.getBoolean("helios.harness.bench")) {
            GpuBench.run(helios, frame);
        }

        boolean ok = failures.isEmpty();
        System.out.println("HELIOS-HARNESS " + (ok ? "OK" : "FAIL") + " java=" + System.getProperty("java.version")
                + " framebuffer=" + frame.fbWidth() + "x" + frame.fbHeight() + " gui=" + frame.guiWidth() + "x"
                + frame.guiHeight() + " state=" + before + (ok ? "" : " failures=" + failures));
        return ok;
    }

    /** Framebuffer pixel box for GUI bounds: {x, y, w, h}, y from the bottom. */
    private int[] pixelBox(float x0, float y0, float x1, float y1) {
        float p = frame.pixel();
        int left = Math.max(0, (int) Math.floor(x0 / p));
        int right = Math.min(frame.fbWidth(), (int) Math.ceil(x1 / p));
        int top = Math.max(0, (int) Math.floor(y0 / p));
        int bottom = Math.min(frame.fbHeight(), (int) Math.ceil(y1 / p));
        return new int[] {left, frame.fbHeight() - bottom, right - left, bottom - top};
    }

    private void checkShape(TestShape s) {
        Model.Shape m = s.model;
        float[] bounds = m.bounds(2 * frame.pixel());
        int[] box = pixelBox(bounds[0], bounds[1], bounds[2], bounds[3]);
        ByteBuffer under = read(box);
        ShapeBatch batch = helios.shapes().begin(frame.pixel());
        s.draw.accept(batch);
        batch.end();
        ByteBuffer over = read(box);
        float p = frame.pixel();
        int maxErr = 0;
        String worst = "";
        int partial = 0;
        int outsideChanged = 0;
        for (int row = 0; row < box[3]; row++) {
            for (int col = 0; col < box[2]; col++) {
                float gx = (box[0] + col + 0.5f) * p;
                float gy = (frame.fbHeight() - (box[1] + row) - 0.5f) * p;
                float[] c = m.shade(gx, gy, p);
                float a = c[3];
                int i = (row * box[2] + col) * 4;
                for (int ch = 0; ch < 3; ch++) {
                    int bg = under.get(i + ch) & 0xFF;
                    int got = over.get(i + ch) & 0xFF;
                    float expected = bg * (1 - a) + c[ch] * 255f * a;
                    int err = Math.round(Math.abs(got - expected));
                    if (err > maxErr) {
                        maxErr = err;
                        worst = String.format(
                                "at (%.2f, %.2f) ch%d a=%.3f bg=%d got=%d want=%.1f", gx, gy, ch, a, bg, got, expected);
                    }
                    if (a == 0f && got != bg) {
                        outsideChanged++;
                    }
                }
                if (a > 0f && a < 1f) {
                    partial++;
                }
            }
        }
        System.out.println("HELIOS-HARNESS shape " + m.name() + " maxError=" + maxErr + "/255 pixels="
                + (box[2] * box[3]) + " partial=" + partial + " outsideChanged=" + outsideChanged + " worst " + worst);
        expect(m.name() + " matches the model within " + TOLERANCE + "/255", maxErr <= TOLERANCE, "max " + maxErr);
        expect(m.name() + " draws nothing outside its coverage", outsideChanged == 0, outsideChanged + " channels");
    }

    /** Compares one glyph's pixels with a software model: bilinear atlas sample, median, screen pixel range. */
    private void checkGlyph() {
        MsdfFont font = helios.defaultFont();
        MsdfFont.Glyph g = font.glyph('g');
        float size = GLYPH[2];
        float x0 = GLYPH[0] + g.left() * size;
        float y0 = GLYPH[1] + g.top() * size;
        float x1 = GLYPH[0] + g.right() * size;
        float y1 = GLYPH[1] + g.bottom() * size;
        int[] box = pixelBox(x0, y0, x1, y1);
        ByteBuffer under = read(box);
        drawGlyph(helios, frame);
        ByteBuffer over = read(box);
        float p = frame.pixel();
        ByteBuffer atlas = font.atlasPixels();
        int aw = font.atlasWidth();
        int ah = font.atlasHeight();
        float texelsX = (g.u1() - g.u0()) * aw;
        float texelsY = (g.v1() - g.v0()) * ah;
        float spr = Math.max(0.5f * font.pxRange() * ((x1 - x0) / p / texelsX + (y1 - y0) / p / texelsY), 1f);
        int maxErr = 0;
        int partial = 0;
        int outsideChanged = 0;
        String worst = "";
        for (int row = 0; row < box[3]; row++) {
            for (int col = 0; col < box[2]; col++) {
                float gx = (box[0] + col + 0.5f) * p;
                float gy = (frame.fbHeight() - (box[1] + row) - 0.5f) * p;
                float a = 0;
                if (gx >= x0 && gx < x1 && gy >= y0 && gy < y1) {
                    float u = g.u0() + (gx - x0) / (x1 - x0) * (g.u1() - g.u0());
                    float v = g.v0() + (gy - y0) / (y1 - y0) * (g.v1() - g.v0());
                    float sd = medianSample(atlas, aw, ah, u * aw - 0.5f, v * ah - 0.5f) - 0.5f;
                    a = Math.min(Math.max(spr * sd + 0.5f, 0f), 1f);
                }
                int i = (row * box[2] + col) * 4;
                for (int ch = 0; ch < 3; ch++) {
                    int bg = under.get(i + ch) & 0xFF;
                    int got = over.get(i + ch) & 0xFF;
                    float expected = bg * (1 - a) + 255f * a;
                    int err = Math.round(Math.abs(got - expected));
                    if (err > maxErr) {
                        maxErr = err;
                        worst = String.format("at (%.2f, %.2f) a=%.3f got=%d want=%.1f", gx, gy, a, got, expected);
                    }
                    if (a == 0f && got != bg) {
                        outsideChanged++;
                    }
                }
                if (a > 0f && a < 1f) {
                    partial++;
                }
            }
        }
        System.out.println("HELIOS-HARNESS glyph g maxError=" + maxErr + "/255 partial=" + partial + " outsideChanged="
                + outsideChanged + " screenPxRange=" + spr + " worst " + worst);
        expect("glyph matches the MSDF model within " + GLYPH_TOLERANCE + "/255", maxErr <= GLYPH_TOLERANCE, worst);
        expect("glyph draws nothing outside its quad", outsideChanged == 0, outsideChanged + " channels");
    }

    /** GPU bilinear filtering uses fixed-point weights, so the glyph model gets a little more room than shapes. */
    private static final int GLYPH_TOLERANCE = 8;

    private static float medianSample(ByteBuffer atlas, int w, int h, float fx, float fy) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = fx - x0;
        float ty = fy - y0;
        float[] c = new float[3];
        for (int ch = 0; ch < 3; ch++) {
            float a = texel(atlas, w, h, x0, y0, ch) * (1 - tx) + texel(atlas, w, h, x0 + 1, y0, ch) * tx;
            float b = texel(atlas, w, h, x0, y0 + 1, ch) * (1 - tx) + texel(atlas, w, h, x0 + 1, y0 + 1, ch) * tx;
            c[ch] = a * (1 - ty) + b * ty;
        }
        return Math.max(Math.min(c[0], c[1]), Math.min(Math.max(c[0], c[1]), c[2]));
    }

    private static float texel(ByteBuffer atlas, int w, int h, int x, int y, int ch) {
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        return (atlas.get((y * w + x) * 3 + ch) & 0xFF) / 255f;
    }

    private void compareReference(String name, float[] region) {
        int[] box = pixelBox(region[0], region[1], region[0] + region[2], region[1] + region[3]);
        BufferedImage actual = image(read(box), box[2], box[3], 0, 0, box[2], box[3], 1);
        String file = name + "-" + frame.fbWidth() + "x" + frame.fbHeight() + ".png";
        try {
            ImageIO.write(actual, "png", new File(out, file));
            String dir = System.getProperty("helios.harness.references", "");
            if (dir.isEmpty()) {
                System.out.println("HELIOS-HARNESS reference " + name + " skipped: no reference directory");
                return;
            }
            File ref = new File(dir, file);
            if (Boolean.getBoolean("helios.harness.updateReferences")) {
                ref.getParentFile().mkdirs();
                ImageIO.write(actual, "png", ref);
                System.out.println("HELIOS-HARNESS reference " + name + " updated " + ref.getName());
                return;
            }
            if (!ref.isFile()) {
                boolean required = Boolean.getBoolean("helios.harness.requireReferences");
                System.out.println("HELIOS-HARNESS reference " + name + " missing for this size: " + file);
                expect(name + " reference exists", !required, file);
                return;
            }
            BufferedImage expected = ImageIO.read(ref);
            if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
                expect(name + " reference size", false, "reference has another size");
                return;
            }
            long sum = 0;
            int[] histogram = new int[256];
            int n = 0;
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    int a = actual.getRGB(x, y);
                    int e = expected.getRGB(x, y);
                    for (int shift = 0; shift < 24; shift += 8) {
                        int d = Math.abs(((a >>> shift) & 0xFF) - ((e >>> shift) & 0xFF));
                        sum += d;
                        histogram[d]++;
                        n++;
                    }
                }
            }
            double mean = sum / (double) n;
            int p999 = percentile(histogram, n, 0.999);
            System.out.println("HELIOS-HARNESS reference " + name + " meanError=" + String.format("%.3f", mean)
                    + " p99.9=" + p999);
            expect(name + " matches its reference", mean <= 2.0 && p999 <= 16, "mean " + mean + " p99.9 " + p999);
        } catch (IOException e) {
            expect(name + " reference readable", false, e.toString());
        }
    }

    private static int percentile(int[] histogram, int n, double q) {
        long seen = 0;
        for (int i = 0; i < histogram.length; i++) {
            seen += histogram[i];
            if (seen >= n * q) {
                return i;
            }
        }
        return 255;
    }

    private void checkOffscreen() {
        Framebuffer fb = helios.framebuffers().acquireScreen(1, false);
        expect(
                "pooled framebuffer matches the window framebuffer",
                fb.width() == frame.fbWidth() && fb.height() == frame.fbHeight(),
                fb.width() + "x" + fb.height());
        float p = frame.pixel();
        try (StateGuard g = helios.guard()) {
            g.framebuffer(fb.id()).viewport(0, 0, fb.width(), fb.height());

            helios.gl().clearColorTransparent();
            int calls = helios.shapes().drawCalls();
            ShapeBatch b = helios.shapes().begin(p);
            for (TestShape s : SHAPES) {
                s.draw.accept(b);
            }
            b.end();
            expect(
                    SHAPES.size() + " shapes in one draw call",
                    helios.shapes().drawCalls() == calls + 1,
                    (helios.shapes().drawCalls() - calls) + " calls");

            helios.gl().clearColorTransparent();
            helios.shapes().begin(p).rect(10, 10, 40, 40, 0, 0xFF204060).end();
            helios.postChain().then(invertProgram(), prog -> {}).apply();
            int[] inverted = pixel(30, 30);
            expect(
                    "post chain inverts colour",
                    close(inverted, new int[] {0xDF, 0xBF, 0x9F, 0xFF}),
                    Arrays.toString(inverted));

            helios.gl().clearColorTransparent();
            helios.scissor().push(100, 10, 20, 20, p);
            helios.shapes().begin(p).rect(90, 0, 60, 60, 0, 0xFFFFFFFF).end();
            helios.scissor().pop();
            int[] inside = pixel(110, 20);
            int[] outside = pixel(95, 5);
            expect("scissor keeps the inside", close(inside, new int[] {255, 255, 255, 255}), Arrays.toString(inside));
            expect("scissor clips the outside", outside[3] == 0, Arrays.toString(outside));
            expect("scissor test restored", !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "still enabled");
        }
        helios.framebuffers().release(fb);

        Framebuffer sfb = helios.framebuffers().acquireScreen(1, true);
        try (StateGuard g = helios.guard()) {
            // A fresh depth-stencil buffer holds garbage depth, and GUI drawing keeps the depth test on.
            g.framebuffer(sfb.id()).viewport(0, 0, sfb.width(), sfb.height()).depthTest(false);
            for (boolean inside : new boolean[] {true, false}) {
                helios.gl().clearColorTransparent();
                helios.stencil()
                        .clip(
                                () -> helios.shapes()
                                        .begin(p)
                                        .circle(30, 30, 15, Paint.solid(-1))
                                        .end(),
                                inside,
                                () -> helios.shapes()
                                        .begin(p)
                                        .rect(0, 0, 60, 60, 0, 0xFFFFFFFF)
                                        .end());
                int[] centre = pixel(30, 30);
                int[] corner = pixel(17, 17);
                String which = inside ? "inside" : "outside";
                expect(
                        "stencil " + which + ": centre",
                        inside ? centre[3] == 255 : centre[3] == 0,
                        Arrays.toString(centre));
                expect(
                        "stencil " + which + ": corner",
                        inside ? corner[3] == 0 : corner[3] == 255,
                        Arrays.toString(corner));
            }
            expect("stencil test restored", !GL11.glIsEnabled(GL11.GL_STENCIL_TEST), "still enabled");
        }
        helios.framebuffers().release(sfb);
        expect(
                "no framebuffer left lent out",
                helios.framebuffers().lentCount() == 0,
                "lent " + helios.framebuffers().lentCount());
    }

    private ShaderProgram invertProgram() {
        if (invert == null) {
            SourceResolver mine = SourceResolver.classpath(Checks.class.getClassLoader(), "helios-harness");
            invert = new ShaderLoader(helios.gl(), mine.or(Helios.builtinShaders()))
                    .load(Passes.FULLSCREEN_VERTEX, "invert.fsh", Map.of());
        }
        return invert;
    }

    private int[] pixel(float gx, float gy) {
        int x = (int) (gx / frame.pixel());
        int y = frame.fbHeight() - 1 - (int) (gy / frame.pixel());
        ByteBuffer b = read(new int[] {x, y, 1, 1});
        return new int[] {b.get(0) & 0xFF, b.get(1) & 0xFF, b.get(2) & 0xFF, b.get(3) & 0xFF};
    }

    private static boolean close(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Flips each cached capability through GlStateManager and back, and checks GL follows. A stale cache would skip
     * one of the calls.
     */
    private void checkCacheCoherence() {
        GlStateCache gsm = new MinecraftGlStateCache(Checks.class.getClassLoader());
        int[] caps = {GL11.GL_BLEND, GL11.GL_CULL_FACE, GL11.GL_DEPTH_TEST};
        String[] names = {"blend", "cull", "depth"};
        for (int k = 0; k < caps.length; k++) {
            boolean orig = GL11.glIsEnabled(caps[k]);
            set(gsm, k, !orig);
            boolean flipped = GL11.glIsEnabled(caps[k]);
            set(gsm, k, orig);
            boolean back = GL11.glIsEnabled(caps[k]);
            expect("GlStateManager cache coherent for " + names[k], flipped == !orig && back == orig, "orig=" + orig);
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int probe = GL11.glGenTextures();
        gsm.bindTexture(probe);
        boolean took = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == probe;
        gsm.bindTexture(bound);
        boolean back = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == bound;
        GL11.glDeleteTextures(probe);
        expect("GlStateManager cache coherent for the bound texture", took && back, "took=" + took + " back=" + back);
    }

    /** Projects GUI points through the game's real GUI matrices: each must land where it was drawn. */
    private void checkProjection() {
        Projector projector = new Projector().capture(helios.gl(), frame.pixel());
        float[] out = new float[3];
        boolean ok = true;
        float worst = 0;
        for (float[] pt : new float[][] {{0, 0}, {100, 50}, {frame.guiWidth(), frame.guiHeight()}, {13.5f, 200.25f}}) {
            if (!projector.project(pt[0], pt[1], 0, out)) {
                ok = false;
                continue;
            }
            worst = Math.max(worst, Math.max(Math.abs(out[0] - pt[0]), Math.abs(out[1] - pt[1])));
        }
        expect("GUI points project onto themselves", ok && worst < 0.01f, "worst " + worst);
        float[] box = new float[4];
        boolean visible = projector.projectBox(10, 20, 0, 60, 40, 0, box);
        expect(
                "a GUI box projects to its own bounds",
                visible
                        && Math.abs(box[0] - 10) < 0.01f
                        && Math.abs(box[1] - 20) < 0.01f
                        && Math.abs(box[2] - 60) < 0.01f
                        && Math.abs(box[3] - 40) < 0.01f,
                Arrays.toString(box));
    }

    /** Resolves every world helper member against the running game; positions need a world, names do not. */
    private void checkWorldHelpers() {
        try {
            MinecraftWorld world = new MinecraftWorld(Checks.class.getClassLoader());
            float pt = world.partialTicks();
            double[] cam = world.camera(new double[3]);
            expect("partial tick in [0, 1)", pt >= 0 && pt < 1, String.valueOf(pt));
            expect("camera position readable", !Double.isNaN(cam[0]), Arrays.toString(cam));
        } catch (RuntimeException e) {
            expect("world helpers resolve", false, e + (e.getCause() == null ? "" : " / " + e.getCause()));
        }
    }

    private static void set(GlStateCache gsm, int k, boolean on) {
        switch (k) {
            case 0 -> gsm.blend(on);
            case 1 -> gsm.cull(on);
            default -> gsm.depthTest(on);
        }
    }

    private void screenshots() {
        int w = frame.fbWidth();
        int h = frame.fbHeight();
        ByteBuffer all = read(new int[] {0, 0, w, h});
        try {
            File menu = new File(out, "menu.png");
            ImageIO.write(image(all, w, h, 0, 0, w, h, 1), "png", menu);
            System.out.println("HELIOS-HARNESS screenshot " + menu.getAbsolutePath());
            zoom(all, "zoom-arc.png", 196, 4, 24, 16);
            zoom(all, "zoom-line.png", 220, 6, 20, 14);
            zoom(all, "zoom-corners.png", 4, 4, 20, 14);
        } catch (IOException e) {
            failures.add("screenshot: " + e);
        }
    }

    private void zoom(ByteBuffer all, String name, float gx, float gy, float gw, float gh) throws IOException {
        float p = frame.pixel();
        int x = (int) (gx / p);
        int y = (int) (gy / p);
        BufferedImage img = image(all, frame.fbWidth(), frame.fbHeight(), x, y, (int) (gw / p), (int) (gh / p), 10);
        File f = new File(out, name);
        ImageIO.write(img, "png", f);
        System.out.println("HELIOS-HARNESS screenshot " + f.getAbsolutePath());
    }

    /** Builds an image from a bottom-up RGBA readback; x, y are top-down. */
    private static BufferedImage image(ByteBuffer rgba, int w, int h, int x, int y, int cw, int ch, int scale) {
        BufferedImage img = new BufferedImage(cw * scale, ch * scale, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < ch; row++) {
            for (int col = 0; col < cw; col++) {
                int sx = x + col;
                int sy = h - 1 - (y + row);
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
                    continue;
                }
                int i = (sy * w + sx) * 4;
                int rgb = (rgba.get(i) & 0xFF) << 16 | (rgba.get(i + 1) & 0xFF) << 8 | (rgba.get(i + 2) & 0xFF);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        img.setRGB(col * scale + dx, row * scale + dy, rgb);
                    }
                }
            }
        }
        return img;
    }

    private static ByteBuffer read(int[] box) {
        ByteBuffer buf = BufferUtils.createByteBuffer(Math.max(1, box[2] * box[3] * 4));
        int align = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(box[0], box[1], box[2], box[3], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, align);
        return buf;
    }

    private void expect(String what, boolean ok, String detail) {
        if (!ok) {
            failures.add(what + " (" + detail + ")");
        }
        System.out.println(
                "HELIOS-HARNESS check " + (ok ? "pass" : "FAIL") + ": " + what + (ok ? "" : " (" + detail + ")"));
    }

    /** The GL state the checks compare before and after Helios draws. */
    private record State(
            boolean blend,
            int srcRgb,
            int dstRgb,
            int srcAlpha,
            int dstAlpha,
            boolean alphaTest,
            boolean cull,
            boolean depthTest,
            boolean depthMask,
            boolean texture2D,
            boolean scissor,
            int program,
            int framebuffer,
            int activeTexture,
            int texture,
            int arrayBuffer,
            int vertexArray,
            String viewport) {
        static State capture() {
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            return new State(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(0x80C9),
                    GL11.glGetInteger(0x80C8),
                    GL11.glGetInteger(0x80CB),
                    GL11.glGetInteger(0x80CA),
                    GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0,
                    GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(0x8CA6),
                    GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                    GL11.glGetInteger(0x85B5),
                    Arrays.toString(vp));
        }
    }
}
