/* SPDX-License-Identifier: MIT */
package lol.trq.helios.anim;

/**
 * An easing curve: maps linear progress {@code t} in {@code [0, 1]} to eased progress, 0 at 0 and 1 at 1. Some curves
 * overshoot ({@link #BACK_OUT}, {@link #ELASTIC_OUT}) and leave {@code [0, 1]} in between.
 *
 * <p>Curves follow the usual names (easings.net): {@code IN} starts slow, {@code OUT} ends slow.
 */
@FunctionalInterface
public interface Easing {
    /**
     * Eases a progress value.
     *
     * @param t linear progress, {@code [0, 1]}
     * @return eased progress
     */
    double apply(double t);

    /** No easing. */
    Easing LINEAR = t -> t;

    /** Quadratic, slow start. */
    Easing QUAD_IN = t -> t * t;

    /** Quadratic, slow end; the classic decelerate. */
    Easing QUAD_OUT = t -> 1 - (1 - t) * (1 - t);

    /** Quadratic, slow start and end. */
    Easing QUAD_IN_OUT = t -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;

    /** Cubic, slow start. */
    Easing CUBIC_IN = t -> t * t * t;

    /** Cubic, slow end. */
    Easing CUBIC_OUT = t -> 1 - Math.pow(1 - t, 3);

    /** Cubic, slow start and end. */
    Easing CUBIC_IN_OUT = t -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

    /** Quintic, slow end; a long soft landing. */
    Easing QUINT_OUT = t -> 1 - Math.pow(1 - t, 5);

    /** Sine, slow end. */
    Easing SINE_OUT = t -> Math.sin(t * Math.PI / 2);

    /** Sine, slow start and end. */
    Easing SINE_IN_OUT = t -> -(Math.cos(Math.PI * t) - 1) / 2;

    /** Exponential, slow end. */
    Easing EXPO_OUT = t -> t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);

    /** Circular, slow end. */
    Easing CIRC_OUT = t -> Math.sqrt(1 - Math.pow(t - 1, 2));

    /** Hermite smoothstep. */
    Easing SMOOTHSTEP = t -> t * t * (3 - 2 * t);

    /** Overshoots past 1 and settles back. */
    Easing BACK_OUT = back(1.70158);

    /** A springy overshoot that oscillates into place. */
    Easing ELASTIC_OUT = t -> {
        if (t <= 0 || t >= 1) {
            return t <= 0 ? 0 : 1;
        }
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
    };

    /** Bounces at the end like a dropped ball. */
    Easing BOUNCE_OUT = t -> {
        double n = 7.5625;
        double d = 2.75;
        if (t < 1 / d) {
            return n * t * t;
        } else if (t < 2 / d) {
            double u = t - 1.5 / d;
            return n * u * u + 0.75;
        } else if (t < 2.5 / d) {
            double u = t - 2.25 / d;
            return n * u * u + 0.9375;
        }
        double u = t - 2.625 / d;
        return n * u * u + 0.984375;
    };

    /**
     * An overshooting ease-out.
     *
     * @param overshoot how far past 1 it swings; 1.70158 is the common default
     * @return the curve
     */
    static Easing back(double overshoot) {
        double c3 = overshoot + 1;
        return t -> 1 + c3 * Math.pow(t - 1, 3) + overshoot * Math.pow(t - 1, 2);
    }

    /**
     * Plays this curve backwards in time: {@code 1 - f(1 - t)}, turning an ease-out into the matching ease-in.
     *
     * @return the reversed curve
     */
    default Easing reversed() {
        return t -> 1 - apply(1 - t);
    }
}
