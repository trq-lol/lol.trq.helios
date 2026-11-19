/* SPDX-License-Identifier: MIT */
package lol.trq.helios.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class AnimTest {
    private long now;
    private final Clock clock = () -> now;

    private void advance(long millis) {
        now += millis * 1_000_000L;
    }

    @Test
    void everyEasingStartsAtZeroAndEndsAtOne() throws Exception {
        for (Field f : Easing.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == Easing.class) {
                Easing e = (Easing) f.get(null);
                assertEquals(0, e.apply(0), 1e-9, f.getName());
                assertEquals(1, e.apply(1), 1e-9, f.getName());
                assertEquals(0, e.reversed().apply(0), 1e-9, f.getName());
                assertEquals(1, e.reversed().apply(1), 1e-9, f.getName());
            }
        }
    }

    @Test
    void monotoneCurvesNeverOvershoot() {
        for (Easing e : new Easing[] {
            Easing.LINEAR,
            Easing.QUAD_OUT,
            Easing.QUAD_IN_OUT,
            Easing.CUBIC_OUT,
            Easing.SINE_OUT,
            Easing.SMOOTHSTEP,
            Easing.EXPO_OUT,
            Easing.CIRC_OUT
        }) {
            double prev = 0;
            for (int i = 1; i <= 100; i++) {
                double v = e.apply(i / 100.0);
                assertTrue(v >= prev - 1e-12 && v <= 1 + 1e-12);
                prev = v;
            }
        }
    }

    @Test
    void backAndElasticOvershoot() {
        double max = 0;
        for (int i = 0; i <= 100; i++) {
            max = Math.max(max, Easing.BACK_OUT.apply(i / 100.0));
        }
        assertTrue(max > 1.05, "back overshoot " + max);
        assertTrue(Easing.ELASTIC_OUT.apply(0.15) > 1);
    }

    @Test
    void outEasingsAreAheadOfLinear() {
        assertEquals(0.75, Easing.QUAD_OUT.apply(0.5), 1e-12);
        assertEquals(0.25, Easing.QUAD_IN.apply(0.5), 1e-12);
        assertEquals(Easing.QUAD_IN.apply(0.3), Easing.QUAD_OUT.reversed().apply(0.3), 1e-12);
    }

    @Test
    void animationRunsAndRests() {
        Animation a = new Animation(200, Easing.LINEAR, clock);
        assertEquals(0, a.value());
        assertTrue(a.done());
        a.forward(true);
        advance(50);
        assertEquals(0.25, a.value(), 1e-9);
        assertFalse(a.done());
        advance(200);
        assertEquals(1, a.value());
        assertTrue(a.done());
        assertEquals(10f, a.between(0, 10));
    }

    @Test
    void reversingMidwayContinuesWithoutAJump() {
        Animation a = new Animation(200, Easing.LINEAR, clock);
        a.forward(true);
        advance(150);
        double before = a.value();
        a.forward(false);
        assertEquals(before, a.value(), 1e-9);
        advance(100);
        assertEquals(0.25, a.value(), 1e-9);
        advance(100);
        assertEquals(0, a.value());
        assertTrue(a.done());
    }

    @Test
    void sameDirectionIsANoOp() {
        Animation a = new Animation(100, Easing.LINEAR, clock).forward(true);
        advance(50);
        a.forward(true);
        assertEquals(0.5, a.value(), 1e-9);
        a.toggle();
        assertFalse(a.forward());
    }

    @Test
    void snapRestartAndDuration() {
        Animation a = new Animation(100, Easing.LINEAR, clock).snap(true);
        assertEquals(1, a.value());
        a.restart();
        assertEquals(0, a.value());
        advance(50);
        a.duration(1000);
        assertEquals(0.5, a.value(), 1e-9);
        advance(100);
        assertEquals(0.6, a.value(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> a.duration(0));
    }

    @Test
    void smoothIsFrameRateIndependent() {
        Smooth coarse = new Smooth(10, clock);
        Smooth fine = new Smooth(10, clock);
        coarse.target(100);
        fine.target(100);
        long start = now;
        advance(100);
        float c = coarse.update();
        now = start;
        float f = 0;
        for (int i = 0; i < 10; i++) {
            advance(10);
            f = fine.update();
        }
        assertEquals(c, f, 1e-3f);
        assertEquals(100 * (1 - Math.exp(-1)), c, 1e-3);
    }

    @Test
    void smoothSettlesOnTheTarget() {
        Smooth s = new Smooth(20, clock).snap(5);
        assertEquals(5f, s.update());
        s.target(6);
        advance(5000);
        assertEquals(6f, s.update());
    }

    @Test
    void stopwatch() {
        Stopwatch w = new Stopwatch(clock);
        advance(30);
        assertEquals(30, w.millis());
        assertFalse(w.elapsed(31));
        assertTrue(w.elapsed(30));
        assertTrue(w.elapsedAndReset(20));
        assertFalse(w.elapsedAndReset(20));
        advance(20);
        assertTrue(w.elapsedAndReset(20));
    }
}
