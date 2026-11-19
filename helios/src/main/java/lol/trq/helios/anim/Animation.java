/* SPDX-License-Identifier: MIT */
package lol.trq.helios.anim;

import java.util.Objects;

/**
 * A timed transition between 0 and 1 that can be reversed at any moment without jumping.
 *
 * <p>Typical use: a menu that opens and closes. Call {@link #forward(boolean)} with whether it should be open, and draw
 * with {@link #value()}. Reversing halfway continues from the current position, taking half the duration to get back.
 *
 * <pre>{@code
 * Animation open = new Animation(250, Easing.CUBIC_OUT);
 * open.forward(menuOpen);
 * float scale = (float) open.value();
 * }</pre>
 */
public final class Animation {
    private final Clock clock;
    private final Easing easing;
    private long durationNanos;
    private boolean forward = true;
    private double progressAtMark;
    private long mark;

    /**
     * Creates an animation at rest at 0, using the system clock.
     *
     * @param durationMillis how long a full 0-to-1 run takes
     * @param easing the curve
     */
    public Animation(long durationMillis, Easing easing) {
        this(durationMillis, easing, Clock.SYSTEM);
    }

    /**
     * Creates an animation at rest at 0.
     *
     * @param durationMillis how long a full 0-to-1 run takes
     * @param easing the curve
     * @param clock the time source
     */
    public Animation(long durationMillis, Easing easing, Clock clock) {
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("duration must be positive: " + durationMillis);
        }
        this.durationNanos = durationMillis * 1_000_000L;
        this.easing = Objects.requireNonNull(easing, "easing");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.forward = false;
        this.mark = clock.nanos();
    }

    /**
     * Sets the direction: towards 1 when true, towards 0 when false. Does nothing if it already goes that way.
     *
     * @param towardsOne the direction
     * @return this animation
     */
    public Animation forward(boolean towardsOne) {
        if (towardsOne != forward) {
            progressAtMark = progress();
            mark = clock.nanos();
            forward = towardsOne;
        }
        return this;
    }

    /**
     * Reverses the direction.
     *
     * @return this animation
     */
    public Animation toggle() {
        return forward(!forward);
    }

    /**
     * Jumps to an end and rests there.
     *
     * @param atOne rest at 1 when true, at 0 when false
     * @return this animation
     */
    public Animation snap(boolean atOne) {
        forward = atOne;
        progressAtMark = atOne ? 1 : 0;
        mark = clock.nanos();
        return this;
    }

    /**
     * Restarts from 0 towards 1.
     *
     * @return this animation
     */
    public Animation restart() {
        snap(false);
        return forward(true);
    }

    /**
     * Changes the duration of a full run, keeping the current position.
     *
     * @param durationMillis the new duration
     */
    public void duration(long durationMillis) {
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("duration must be positive: " + durationMillis);
        }
        progressAtMark = progress();
        mark = clock.nanos();
        durationNanos = durationMillis * 1_000_000L;
    }

    /**
     * Returns the linear progress.
     *
     * @return {@code [0, 1]}
     */
    public double progress() {
        double moved = (clock.nanos() - mark) / (double) durationNanos;
        double p = forward ? progressAtMark + moved : progressAtMark - moved;
        return Math.min(Math.max(p, 0), 1);
    }

    /**
     * Returns the eased value. Going towards 0 replays the curve backwards, so a menu closes the way it opened.
     *
     * @return the eased progress
     */
    public double value() {
        return easing.apply(progress());
    }

    /**
     * Interpolates between two values by {@link #value()}.
     *
     * @param from the value at 0
     * @param to the value at 1
     * @return the current value
     */
    public float between(float from, float to) {
        return (float) (from + (to - from) * value());
    }

    /**
     * Returns whether the animation has reached the end it is heading to.
     *
     * @return true at rest
     */
    public boolean done() {
        double p = progress();
        return forward ? p >= 1 : p <= 0;
    }

    /**
     * Returns the direction.
     *
     * @return true when heading towards 1
     */
    public boolean forward() {
        return forward;
    }
}
