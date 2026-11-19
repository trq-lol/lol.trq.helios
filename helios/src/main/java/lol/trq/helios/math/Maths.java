/* SPDX-License-Identifier: MIT */
package lol.trq.helios.math;

import java.util.concurrent.ThreadLocalRandom;

/** Small numeric helpers used across rendering and animation. All static and allocation-free. */
public final class Maths {
    private Maths() {}

    /**
     * Clamps a value.
     *
     * @param v the value
     * @param min the lower bound
     * @param max the upper bound
     * @return {@code v} within {@code [min, max]}
     */
    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    /**
     * Clamps a value.
     *
     * @param v the value
     * @param min the lower bound
     * @param max the upper bound
     * @return {@code v} within {@code [min, max]}
     */
    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    /**
     * Linear interpolation, not clamped.
     *
     * @param a the value at 0
     * @param b the value at 1
     * @param t the position
     * @return {@code a + (b - a) * t}
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Linear interpolation, not clamped.
     *
     * @param a the value at 0
     * @param b the value at 1
     * @param t the position
     * @return {@code a + (b - a) * t}
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * The position of a value between two others.
     *
     * @param a the value at 0
     * @param b the value at 1
     * @param v the value
     * @return {@code (v - a) / (b - a)}, or 0 when {@code a == b}
     */
    public static double inverseLerp(double a, double b, double v) {
        return a == b ? 0 : (v - a) / (b - a);
    }

    /**
     * Maps a value from one range to another, not clamped.
     *
     * @param v the value
     * @param fromMin source range start
     * @param fromMax source range end
     * @param toMin target range start
     * @param toMax target range end
     * @return the mapped value
     */
    public static double remap(double v, double fromMin, double fromMax, double toMin, double toMax) {
        return lerp(toMin, toMax, inverseLerp(fromMin, fromMax, v));
    }

    /**
     * Moves a value towards a target by at most a step, never past it.
     *
     * @param current the value
     * @param target the target
     * @param maxStep the largest move, not negative
     * @return the moved value
     */
    public static double approach(double current, double target, double maxStep) {
        double d = target - current;
        return Math.abs(d) <= maxStep ? target : current + Math.signum(d) * maxStep;
    }

    /**
     * Wraps an angle to {@code [-180, 180)}.
     *
     * @param degrees the angle
     * @return the wrapped angle
     */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }

    /**
     * Interpolates angles the short way round.
     *
     * @param a the angle at 0, degrees
     * @param b the angle at 1, degrees
     * @param t the position
     * @return the interpolated angle, not wrapped
     */
    public static double lerpDegrees(double a, double b, double t) {
        return a + wrapDegrees(b - a) * t;
    }

    /**
     * Rounds to decimal places, half away from zero, without BigDecimal.
     *
     * @param v the value
     * @param places decimal places, 0 to 15
     * @return the rounded value
     */
    public static double round(double v, int places) {
        if (places < 0 || places > 15) {
            throw new IllegalArgumentException("places must be 0 to 15: " + places);
        }
        double scale = Math.pow(10, places);
        return Math.signum(v) * Math.round(Math.abs(v) * scale) / scale;
    }

    /**
     * Rounds to the nearest multiple of a step, such as 0.5 or a pixel size.
     *
     * @param v the value
     * @param step the step, positive
     * @return the snapped value
     */
    public static double snap(double v, double step) {
        return Math.round(v / step) * step;
    }

    /**
     * The normal distribution's density.
     *
     * @param x the offset from the mean
     * @param sigma the standard deviation
     * @return the density
     */
    public static double gaussian(double x, double sigma) {
        return Math.exp(-(x * x) / (2 * sigma * sigma)) / Math.sqrt(2 * Math.PI * sigma * sigma);
    }

    /**
     * Fills normalized one-sided Gaussian weights for a separable blur: {@code out[0]} is the centre tap, and the
     * centre plus twice the rest sums to 1.
     *
     * @param sigma the standard deviation in taps
     * @param out receives the weights; its length is the radius plus one
     */
    public static void gaussianKernel(double sigma, float[] out) {
        double sum = 0;
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) gaussian(i, sigma);
            sum += i == 0 ? out[i] : 2 * out[i];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= (float) sum;
        }
    }

    /**
     * A uniformly random value, from a thread-local generator.
     *
     * @param min inclusive lower bound
     * @param max exclusive upper bound
     * @return the value
     */
    public static double random(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    /**
     * A uniformly random int, from a thread-local generator.
     *
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return the value
     */
    public static int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
