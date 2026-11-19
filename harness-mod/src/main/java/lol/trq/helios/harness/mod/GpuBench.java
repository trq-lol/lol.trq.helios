/* SPDX-License-Identifier: MIT */
package lol.trq.helios.harness.mod;

import java.util.Arrays;
import lol.trq.helios.Helios;
import lol.trq.helios.batch.Paint;
import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.fbo.Framebuffer;
import lol.trq.helios.state.StateGuard;
import lol.trq.helios.text.TextStyle;
import org.lwjgl.opengl.GL11;

/**
 * GPU timings, with {@code -Dhelios.harness.bench=true}: each workload runs into an offscreen framebuffer the size of
 * the window, between two {@code glFinish} calls, and the median of 30 runs after 20 warm-up runs is reported. The
 * numbers include the CPU work of building the batch and uploading it. Each run counts as one frame for the streamed
 * vertex buffers.
 */
final class GpuBench {
    private static final int WARMUP = 20;
    private static final int RUNS = 30;

    private GpuBench() {}

    static void run(Helios helios, HarnessMod.Frame frame) {
        GpuBench.helios = helios;
        float p = frame.pixel();
        float w = frame.guiWidth();
        float h = frame.guiHeight();
        TextStyle style = TextStyle.builder(helios.defaultFont()).size(9).build();
        String line = "The quick brown fox jumps over the lazy dog, 0123456789 times!";
        Framebuffer fb = helios.framebuffers().acquireScreen(1, false);
        try (StateGuard g = helios.guard()) {
            g.framebuffer(fb.id()).viewport(0, 0, fb.width(), fb.height());
            helios.gl().clearColorTransparent();
            time("10000 rounded rects", () -> {
                ShapeBatch b = helios.shapes().begin(p);
                for (int i = 0; i < 10000; i++) {
                    b.rect((i * 7) % w, (i * 13) % h, 24, 12, 4, 0xC03B82F6);
                }
                b.end();
            });
            time("10000 gradient circles", () -> {
                ShapeBatch b = helios.shapes().begin(p);
                Paint radial = Paint.radial(0xFFFFFFFF, 0xFF2050FF, 0);
                for (int i = 0; i < 10000; i++) {
                    b.circle((i * 7) % w, (i * 13) % h, 8, radial);
                }
                b.end();
            });
            time("2000 glyphs of 9px text (32 lines)", () -> {
                ShapeBatch b = helios.shapes().begin(p);
                for (int i = 0; i < 32; i++) {
                    helios.text().draw(b, line, 4, 4 + i * 7, style);
                }
                b.end();
            });
            time("one MSDF text batch (1 line, 1 draw call)", () -> {
                ShapeBatch b = helios.shapes().begin(p);
                helios.text().draw(b, line, 4, 4, style);
                b.end();
            });
            time(
                    "one blur pass (1 iteration, full screen)",
                    () -> helios.framebuffers()
                            .release(helios.passes().blur(fb.texture(), fb.width(), fb.height(), 1, 2)));
            time(
                    "full-screen blur behind, 4 iterations",
                    () -> helios.effects().blurBehind(0, 0, w, h, 0, p, 4, 2, 0xFFFFFFFF));
            time(
                    "full-screen bloom, 3 iterations",
                    () -> helios.effects()
                            .bloom(
                                    () -> helios.shapes()
                                            .begin(p)
                                            .circle(w / 2, h / 2, 40, Paint.solid(0xFFFF3060))
                                            .end(),
                                    3,
                                    1.5f,
                                    1f));
        }
        helios.framebuffers().release(fb);
    }

    private static Helios helios;

    private static void time(String name, Runnable work) {
        double[] ms = new double[RUNS];
        for (int i = 0; i < WARMUP + RUNS; i++) {
            GL11.glFinish();
            long t0 = System.nanoTime();
            work.run();
            GL11.glFinish();
            long t1 = System.nanoTime();
            // Each run stands for one frame: rotate the streamed vertex buffers as Helios.tick() would.
            helios.shapes().endFrame();
            if (i >= WARMUP) {
                ms[i - WARMUP] = (t1 - t0) / 1e6;
            }
        }
        Arrays.sort(ms);
        System.out.printf("HELIOS-BENCH %s: median %.3f ms, p90 %.3f ms%n", name, ms[RUNS / 2], ms[(int) (RUNS * 0.9)]);
    }
}
