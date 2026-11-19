/* SPDX-License-Identifier: MIT */
package lol.trq.helios.bench;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import lol.trq.helios.Helios;
import lol.trq.helios.anim.Animation;
import lol.trq.helios.anim.Easing;
import lol.trq.helios.anim.Smooth;
import lol.trq.helios.batch.Paint;
import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.color.Colors;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.math.Mat4;
import lol.trq.helios.math.Projector;
import lol.trq.helios.shader.ShaderPreprocessor;
import lol.trq.helios.state.StateGuard;
import lol.trq.helios.text.TextStyle;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * CPU cost of the work Helios does per frame, against a backend that does nothing. GPU cost is measured in the
 * harness ({@code -Pbench}).
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HeliosBench {
    private static final String LINE = "The quick brown fox jumps over the lazy dog, 0123456789 times.";
    private static final String FORMATTED = "§aThe quick §lbrown§r fox §cjumps §oover§r the lazy dog, 0123456789.";

    private NullGl gl;
    private Helios helios;
    private ShapeBatch batch;
    private TextStyle style;
    private TextStyle shadowed;
    private Paint gradient;
    private ShaderPreprocessor preprocessor;
    private Projector projector;
    private final float[] box = new float[4];
    private Animation animation;
    private Smooth smooth;
    private float t;

    @Setup
    public void setup() {
        gl = new NullGl();
        helios = Helios.create(gl, gl, new SurfaceSize() {
            @Override
            public int width() {
                return 1920;
            }

            @Override
            public int height() {
                return 1080;
            }
        });
        batch = helios.shapes();
        style = TextStyle.builder(helios.defaultFont()).size(9).build();
        shadowed = TextStyle.builder(helios.defaultFont())
                .size(9)
                .shadow(TextStyle.Shadow.CLASSIC)
                .build();
        gradient = Paint.linear(30, 0xFFFF0000, 0xFF0000FF);
        preprocessor = new ShaderPreprocessor(Helios.builtinShaders());
        projector = new Projector()
                .set(
                        Mat4.translation(0, -1.6f, 0, new float[16]),
                        Mat4.perspective(70, 16 / 9f, 0.05f, 256, new float[16]),
                        new int[] {0, 0, 1920, 1080},
                        0.5f);
        animation = new Animation(300, Easing.CUBIC_OUT).forward(true);
        smooth = new Smooth(12).target(100);
    }

    /** One rounded rect in one colour, per shape, amortized over a 1000-shape batch and its flush. */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void rectSolid() {
        batch.begin(0.5f);
        for (int i = 0; i < 1000; i++) {
            batch.rect(i % 400, i / 400 * 10, 30, 8, 3, 0xFF3B82F6);
        }
        batch.end();
    }

    /** One rounded rect with a linear gradient, per shape. */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void rectGradient() {
        batch.begin(0.5f);
        for (int i = 0; i < 1000; i++) {
            batch.rect(i % 400, i / 400 * 10, 30, 8, 3, gradient);
        }
        batch.end();
    }

    /** One line with round caps, per shape. */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void line() {
        Paint p = Paint.solid(0xFFFFFFFF);
        batch.begin(0.5f);
        for (int i = 0; i < 1000; i++) {
            batch.line(i, 0, i + 20, 30, 2, true, p);
        }
        batch.end();
    }

    /** A 62-character line of text: layout, kerning and glyph emission, per line. */
    @Benchmark
    public void textLine() {
        batch.begin(0.5f);
        helios.text().draw(batch, LINE, 10, 10, style);
        batch.end();
    }

    /** The same line with a classic shadow (twice the glyphs). */
    @Benchmark
    public void textLineShadow() {
        batch.begin(0.5f);
        helios.text().draw(batch, LINE, 10, 10, shadowed);
        batch.end();
    }

    /** A line with colour and format codes. */
    @Benchmark
    public void textFormatted() {
        batch.begin(0.5f);
        helios.text().draw(batch, FORMATTED, 10, 10, style);
        batch.end();
    }

    /** Measuring a line without drawing it. */
    @Benchmark
    public float textWidth() {
        return helios.text().width(LINE, style);
    }

    /** Opening a guard, touching six slots and restoring them. */
    @Benchmark
    public void stateGuard() {
        try (StateGuard g = helios.guard()) {
            g.blend(true)
                    .blendFunc(1, 2, 3, 4)
                    .depthMask(false)
                    .cull(false)
                    .program(3)
                    .arrayBuffer(4);
        }
    }

    /** Preprocessing the shape fragment shader (includes, defines, line directives). */
    @Benchmark
    public void preprocessShader(Blackhole bh) {
        bh.consume(preprocessor.process("shape.fsh", Map.of("A", "1")));
    }

    /** HSB colour interpolation, the short way round the hue circle. */
    @Benchmark
    public int colorLerpHsb() {
        t = t > 1 ? 0 : t + 0.001f;
        return Colors.lerpHsb(0xFFFF0000, 0xFF0000FF, t);
    }

    /** Projecting an entity-sized box to its 2D screen bounds (eight corners). */
    @Benchmark
    public boolean projectBox() {
        return projector.projectBox(-0.3, 0, -5.3, 0.3, 1.8, -4.7, box);
    }

    /** Reading an eased animation value. */
    @Benchmark
    public double animationValue() {
        return animation.value();
    }

    /** Advancing a smoothed value. */
    @Benchmark
    public float smoothUpdate() {
        return smooth.update();
    }

    /** Blur behind a rect: capture, four-level dual Kawase, composite, all with GL as no-ops. */
    @Benchmark
    public void blurBehindCpu() {
        helios.effects().blurBehind(10, 10, 200, 100, 8, 0.5f, 4, 2, 0xFFFFFFFF);
    }
}
