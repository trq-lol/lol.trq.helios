/* SPDX-License-Identifier: MIT */
package lol.trq.helios.harness.mod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import lol.trq.helios.Helios;
import lol.trq.helios.fbo.Framebuffer;
import lol.trq.helios.fbo.FramebufferPool;
import lol.trq.helios.minecraft.MinecraftGlStateCache;
import lol.trq.helios.sdf.Sdf;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** The in-game checks: pixels against the Java SDF model, GL state before and after, cache coherence, the FBO pool. */
final class Checks {
    /** The measured rect. Fractional edges so the anti-aliased band has partial pixels at any GUI scale. */
    static final float[] TEST = {12.25f, 12.3f, 120.4f, 48.3f, 12f};

    static final int TEST_COLOR = 0xFF3B82F6;
    private static final int TOLERANCE = 6;

    private final Helios helios;
    private final HarnessMod.Frame frame;
    private final File out;
    private final List<String> failures = new ArrayList<>();

    Checks(Helios helios, HarnessMod.Frame frame, File out) {
        this.helios = helios;
        this.frame = frame;
        this.out = out;
    }

    static String glInfo() {
        return "\"" + GL11.glGetString(GL11.GL_VERSION) + " / " + GL11.glGetString(GL11.GL_RENDERER) + "\"";
    }

    /** Draws the whole scene: the measured rect and the demo shapes. */
    static void drawScene(Helios helios, HarnessMod.Frame frame) {
        drawTest(helios, frame);
        drawDemo(helios, frame);
    }

    private static void drawTest(Helios helios, HarnessMod.Frame frame) {
        helios.rects().fill(TEST[0], TEST[1], TEST[2], TEST[3], TEST[4], TEST_COLOR, frame.pixel());
    }

    private static void drawDemo(Helios helios, HarnessMod.Frame frame) {
        float px = frame.pixel();
        helios.rects().fill(12, 70, 120, 24, 99, 0x80FF4060, px);
        helios.rects().fill(144, 12, 48, 48, 24, 0xFFF59E0B, px);
        helios.rects().fill(144.3f, 70.6f, 47.5f, 23.2f, 6, 0xFF10B981, px);
        helios.rects().fill(204, 12, 2.5f, 82, 1.25f, 0xFFFFFFFF, px);
    }

    boolean run() {
        out.mkdirs();
        String before = State.capture().toString();

        int[] box = testBox();
        ByteBuffer under = read(box);
        drawTest(helios, frame);
        ByteBuffer over = read(box);
        comparePixels(box, under, over);

        String afterDraw = State.capture().toString();
        expect("state unchanged by a draw", before.equals(afterDraw), before + " -> " + afterDraw);

        checkFramebufferPool();
        String afterFbo = State.capture().toString();
        expect("state unchanged by an offscreen draw", before.equals(afterFbo), before + " -> " + afterFbo);

        checkCacheCoherence();

        drawDemo(helios, frame);
        screenshots(box);

        boolean ok = failures.isEmpty();
        System.out.println("HELIOS-HARNESS " + (ok ? "OK" : "FAIL") + " java=" + System.getProperty("java.version")
                + " framebuffer=" + frame.fbWidth() + "x" + frame.fbHeight() + " gui=" + frame.guiWidth() + "x"
                + frame.guiHeight() + " state=" + before + (ok ? "" : " failures=" + failures));
        return ok;
    }

    /** Framebuffer pixel box around the test rect, with a margin: {x, y, w, h}, y from the bottom. */
    private int[] testBox() {
        float p = frame.pixel();
        int x0 = (int) Math.floor(TEST[0] / p) - 3;
        int x1 = (int) Math.ceil((TEST[0] + TEST[2]) / p) + 3;
        int top = (int) Math.floor(TEST[1] / p) - 3;
        int bottom = (int) Math.ceil((TEST[1] + TEST[3]) / p) + 3;
        return new int[] {x0, frame.fbHeight() - bottom, x1 - x0, bottom - top};
    }

    private void comparePixels(int[] box, ByteBuffer under, ByteBuffer over) {
        float p = frame.pixel();
        float hw = TEST[2] / 2;
        float hh = TEST[3] / 2;
        float cx = TEST[0] + hw;
        float cy = TEST[1] + hh;
        float[] c = rgb(TEST_COLOR);
        int maxErr = 0;
        int partial = 0;
        int inside = 0;
        int untouchedWrong = 0;
        for (int row = 0; row < box[3]; row++) {
            for (int col = 0; col < box[2]; col++) {
                float gx = (box[0] + col + 0.5f) * p;
                float gy = (frame.fbHeight() - (box[1] + row) - 0.5f) * p;
                float d = Sdf.roundedRect(gx - cx, gy - cy, hw, hh, TEST[4]) / p;
                float cov = Sdf.coverage(d, 1f);
                int i = (row * box[2] + col) * 4;
                for (int ch = 0; ch < 3; ch++) {
                    int bg = under.get(i + ch) & 0xFF;
                    int got = over.get(i + ch) & 0xFF;
                    float expected = bg * (1 - cov) + c[ch] * 255f * cov;
                    int err = Math.round(Math.abs(got - expected));
                    maxErr = Math.max(maxErr, err);
                    if (cov == 0f && got != bg) {
                        untouchedWrong++;
                    }
                }
                if (cov > 0f && cov < 1f) {
                    partial++;
                } else if (cov == 1f) {
                    inside++;
                }
            }
        }
        int band = bandWidth(box, under, over);
        System.out.println("HELIOS-HARNESS pixels maxError=" + maxErr + "/255 inside=" + inside + " partial=" + partial
                + " outsideChanged=" + untouchedWrong + " edgeBand=" + band + "px");
        expect("pixels match the SDF model within " + TOLERANCE + "/255", maxErr <= TOLERANCE, "max " + maxErr);
        expect("nothing drawn outside the feather", untouchedWrong == 0, untouchedWrong + " channels");
        expect("edge band at most 2 px wide", band >= 1 && band <= 2, band + " px");
    }

    /** Partial pixels where the middle row crosses the left edge. */
    private static int bandWidth(int[] box, ByteBuffer under, ByteBuffer over) {
        int row = box[3] / 2;
        int[] target = {0x3B, 0x82, 0xF6};
        int count = 0;
        for (int col = 0; col < box[2] / 2; col++) {
            int i = (row * box[2] + col) * 4;
            boolean isBg = true;
            boolean isFg = true;
            for (int ch = 0; ch < 3; ch++) {
                int got = over.get(i + ch) & 0xFF;
                isBg &= Math.abs(got - (under.get(i + ch) & 0xFF)) <= 1;
                isFg &= Math.abs(got - target[ch]) <= 1;
            }
            if (!isBg && !isFg) {
                count++;
            }
        }
        return count;
    }

    private void checkFramebufferPool() {
        FramebufferPool pool = helios.framebuffers();
        Framebuffer fb = pool.acquireScreen(1, false);
        expect(
                "pooled framebuffer matches the window framebuffer",
                fb.width() == frame.fbWidth() && fb.height() == frame.fbHeight(),
                fb.width() + "x" + fb.height());
        int[] centre = {
            Math.round((TEST[0] + TEST[2] / 2) / frame.pixel()),
            frame.fbHeight() - Math.round((TEST[1] + TEST[3] / 2) / frame.pixel())
        };
        int[] corner = {
            (int) Math.floor(TEST[0] / frame.pixel()) + 1,
            frame.fbHeight() - (int) Math.floor(TEST[1] / frame.pixel()) - 2
        };
        ByteBuffer px;
        ByteBuffer cornerPx;
        try (StateGuard g = helios.guard()) {
            g.framebuffer(fb.id()).viewport(0, 0, fb.width(), fb.height());
            helios.gl().clearColorTransparent();
            drawTest(helios, frame);
            px = read(new int[] {centre[0], centre[1], 1, 1});
            cornerPx = read(new int[] {corner[0], corner[1], 1, 1});
        }
        pool.release(fb);
        Framebuffer again = pool.acquireScreen(1, false);
        expect(
                "pool reuses a released framebuffer",
                again == fb && pool.createdCount() == 1,
                "created " + pool.createdCount());
        pool.release(again);
        int[] got = {px.get(0) & 0xFF, px.get(1) & 0xFF, px.get(2) & 0xFF, px.get(3) & 0xFF};
        expect(
                "offscreen centre is the fill colour",
                Arrays.equals(got, new int[] {0x3B, 0x82, 0xF6, 0xFF}),
                Arrays.toString(got));
        expect(
                "offscreen corner outside the radius is clear",
                (cornerPx.get(3) & 0xFF) == 0,
                "alpha " + (cornerPx.get(3) & 0xFF));
    }

    /**
     * Flips each cached capability through GlStateManager and back, and checks GL follows. A stale cache would skip
     * one of the calls.
     */
    private void checkCacheCoherence() {
        GlStateCache gsm = new MinecraftGlStateCache(Checks.class.getClassLoader());
        int[] caps = {GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_CULL_FACE, GL11.GL_DEPTH_TEST, GL11.GL_TEXTURE_2D};
        String[] names = {"blend", "alpha", "cull", "depth", "texture2D"};
        for (int k = 0; k < caps.length; k++) {
            boolean orig = GL11.glIsEnabled(caps[k]);
            set(gsm, k, !orig);
            boolean flipped = GL11.glIsEnabled(caps[k]);
            set(gsm, k, orig);
            boolean back = GL11.glIsEnabled(caps[k]);
            expect("GlStateManager cache coherent for " + names[k], flipped == !orig && back == orig, "orig=" + orig);
        }
    }

    private static void set(GlStateCache gsm, int k, boolean on) {
        switch (k) {
            case 0 -> gsm.blend(on);
            case 1 -> gsm.alphaTest(on);
            case 2 -> gsm.cull(on);
            case 3 -> gsm.depthTest(on);
            default -> gsm.texture2D(on);
        }
    }

    private void screenshots(int[] box) {
        int w = frame.fbWidth();
        int h = frame.fbHeight();
        ByteBuffer all = read(new int[] {0, 0, w, h});
        BufferedImage full = image(all, w, h, 0, 0, w, h, 1);
        int cw = Math.min(32, box[2]);
        int ch = Math.min(32, box[3]);
        int cx = box[0];
        int cy = h - (box[1] + box[3]);
        BufferedImage corner = image(all, w, h, cx, cy, cw, ch, 12);
        try {
            File menu = new File(out, "menu.png");
            File zoom = new File(out, "corner-12x.png");
            ImageIO.write(full, "png", menu);
            ImageIO.write(corner, "png", zoom);
            System.out.println("HELIOS-HARNESS screenshot " + menu.getAbsolutePath());
            System.out.println("HELIOS-HARNESS screenshot " + zoom.getAbsolutePath());
        } catch (IOException e) {
            failures.add("screenshot: " + e);
        }
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
        ByteBuffer buf = BufferUtils.createByteBuffer(box[2] * box[3] * 4);
        int align = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(box[0], box[1], box[2], box[3], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, align);
        return buf;
    }

    private static float[] rgb(int argb) {
        return new float[] {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
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
            int program,
            int framebuffer,
            int activeTexture,
            int arrayBuffer,
            String viewport,
            String color) {
        static State capture() {
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            float[] col = new float[4];
            GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, col);
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
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(0x8CA6),
                    GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                    Arrays.toString(vp),
                    Arrays.toString(col));
        }
    }
}
