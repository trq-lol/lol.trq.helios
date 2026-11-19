/* SPDX-License-Identifier: MIT */
package lol.trq.helios.anim;

/**
 * A value that glides towards a target with exponential damping, the same at any frame rate. Good for things whose
 * target moves every frame: a slider knob, a health bar, a scroll offset.
 *
 * <pre>{@code
 * Smooth health = new Smooth(12);   // 12 per second: closes about 63% of the gap in 1/12 s
 * health.target(player.health());
 * float shown = health.update();
 * }</pre>
 */
public final class Smooth {
    private final Clock clock;
    private final double rate;
    private double value;
    private double target;
    private long last;

    /**
     * Creates a smoothed value at 0, using the system clock.
     *
     * @param ratePerSecond how quickly it closes the gap; higher is snappier
     */
    public Smooth(double ratePerSecond) {
        this(ratePerSecond, Clock.SYSTEM);
    }

    /**
     * Creates a smoothed value at 0.
     *
     * @param ratePerSecond how quickly it closes the gap
     * @param clock the time source
     */
    public Smooth(double ratePerSecond, Clock clock) {
        if (!(ratePerSecond > 0)) {
            throw new IllegalArgumentException("rate must be positive: " + ratePerSecond);
        }
        this.rate = ratePerSecond;
        this.clock = clock;
        this.last = clock.nanos();
    }

    /**
     * Sets where the value is heading.
     *
     * @param target the target
     * @return this
     */
    public Smooth target(double target) {
        this.target = target;
        return this;
    }

    /**
     * Jumps to a value and rests there.
     *
     * @param v the value
     * @return this
     */
    public Smooth snap(double v) {
        this.value = v;
        this.target = v;
        this.last = clock.nanos();
        return this;
    }

    /**
     * Advances to now and returns the value. Call once per frame.
     *
     * @return the current value
     */
    public float update() {
        long now = clock.nanos();
        double dt = Math.max(0, (now - last) / 1e9);
        last = now;
        value = target + (value - target) * Math.exp(-rate * dt);
        if (Math.abs(target - value) < 1e-6) {
            value = target;
        }
        return (float) value;
    }

    /**
     * Returns the value without advancing.
     *
     * @return the value at the last update
     */
    public double value() {
        return value;
    }

    /**
     * Returns the target.
     *
     * @return the target
     */
    public double target() {
        return target;
    }
}
