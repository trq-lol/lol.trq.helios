/* SPDX-License-Identifier: MIT */
package lol.trq.helios.anim;

/** A monotonic time source in nanoseconds, so animations and timers can be driven by tests. */
@FunctionalInterface
public interface Clock {
    /** {@link System#nanoTime()}. */
    Clock SYSTEM = System::nanoTime;

    /**
     * Returns the current time.
     *
     * @return nanoseconds from an arbitrary origin
     */
    long nanos();
}
