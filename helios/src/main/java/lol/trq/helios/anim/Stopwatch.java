/* SPDX-License-Identifier: MIT */
package lol.trq.helios.anim;

/** Measures elapsed time since the last reset; the building block for cooldowns and delays. */
public final class Stopwatch {
    private final Clock clock;
    private long start;

    /** Starts a stopwatch on the system clock. */
    public Stopwatch() {
        this(Clock.SYSTEM);
    }

    /**
     * Starts a stopwatch.
     *
     * @param clock the time source
     */
    public Stopwatch(Clock clock) {
        this.clock = clock;
        this.start = clock.nanos();
    }

    /**
     * Restarts from zero.
     *
     * @return this stopwatch
     */
    public Stopwatch reset() {
        start = clock.nanos();
        return this;
    }

    /**
     * Returns the elapsed time.
     *
     * @return milliseconds since the last reset
     */
    public long millis() {
        return (clock.nanos() - start) / 1_000_000L;
    }

    /**
     * Returns the elapsed time.
     *
     * @return nanoseconds since the last reset
     */
    public long nanos() {
        return clock.nanos() - start;
    }

    /**
     * Returns whether at least some time has passed.
     *
     * @param millis the time
     * @return true once {@code millis} have elapsed
     */
    public boolean elapsed(long millis) {
        return clock.nanos() - start >= millis * 1_000_000L;
    }

    /**
     * Returns whether some time has passed and, if so, resets: a cooldown in one call.
     *
     * @param millis the time
     * @return true at most once per {@code millis}
     */
    public boolean elapsedAndReset(long millis) {
        if (elapsed(millis)) {
            reset();
            return true;
        }
        return false;
    }
}
