# Benchmarks

Two kinds of numbers: the CPU cost of what Helios does per frame (JMH, against a GL backend that does nothing), and
the GPU cost of real workloads in the game (the harness). Report what you measured, including regressions.

## How to run

```
./gradlew :helios:jmh                                  # all CPU benchmarks, GC profiler on
./gradlew :helios:jmh -Pjmh.include=textLine           # one benchmark (on Windows, avoid | in the pattern)
./gradlew :harness:runHarness25 -Pbench                # GPU timings in the game (opens a window)
```

JMH results land in `helios/build/results/jmh/results.json`. The harness prints `HELIOS-BENCH` lines: each workload
draws into an offscreen framebuffer the size of the window between two `glFinish` calls, 20 warm-up runs, median and
p90 of 30 runs. GPU numbers include the CPU work of building the batch. They are noisy at the 1 ms level; the first
workload of a run tends to read high.

## CPU (JMH)

Windows 10, Java 25.0.4, measured 2026-11-19. `ns/op` and `B/op` (allocated bytes per operation). Shape benchmarks
are per shape, amortized over a 1000-shape batch and its flush.

| Benchmark | What | Before | After |
|---|---|---|---|
| `rectSolid` | one rounded rect, one colour | 68.7 ns, 0.5 B | 56.3 ns, 0.2 B |
| `rectGradient` | one rounded rect, linear gradient | 119.5 ns, 128 B | 97.4 ns, 0.2 B |
| `line` | one line with round caps | 69.6 ns, 0.5 B | 61.6 ns, 0.2 B |
| `textLine` | 62-character line, layout + kerning + glyphs | 4509 ns, 4808 B | 2971 ns, 208 B |
| `textLineShadow` | same, classic shadow (twice the glyphs) | 9134 ns, 9072 B | 6010 ns, 208 B |
| `textFormatted` | line with colour and format codes | 4426 ns, 4440 B | 2699 ns, 208 B |
| `textWidth` | measure a line | 1477 ns, 4264 B | 128 ns, 0 B |
| `stateGuard` | open a guard, touch six slots, restore | 41.7 ns, 544 B | 12.8 ns, 160 B |
| `blurBehindCpu` | blur-behind bookkeeping (GL calls are no-ops) | 857 ns, 5928 B | 446 ns, 2824 B |
| `preprocessShader` | expand `shape.fsh` (load time only) | 39.6 µs | 38.7 µs |

Added with the utilities (H3), same machine:

| Benchmark | What | Result |
|---|---|---|
| `colorLerpHsb` | HSB interpolation of two colours | 19.7 ns, 0 B |
| `projectBox` | project an entity-sized box to its 2D bounds | 68.2 ns, 0 B |
| `animationValue` | read an eased animation (includes the clock read) | 32.5 ns, 0 B |
| `smoothUpdate` | advance a smoothed value (includes the clock read) | 33.4 ns, 0 B |

"Before" is the first measurement of the H2 code. The changes: kerning lookups for ASCII pairs go through a dense
table instead of a boxed `HashMap` key; `StateGuard` keeps saved state in fields and bit masks instead of an
`EnumSet`, a `List` and eight arrays; linear gradients no longer build a corner array; vertex colours are written as
one int each. The 208 B left per text line is the state guard of the batch flush.

## GPU (harness)

Windows 10, NVIDIA GeForce RTX 4070 (driver 581.80), 1281x720, Java 25, measured 2026-11-19.

| Workload | Median | p90 | With OptiFine HD U M5 |
|---|---|---|---|
| 10000 rounded rects (24x12, translucent) | 3.50 ms | 7.54 ms | 3.47 ms |
| 10000 circles with a radial gradient | 2.45 ms | 5.72 ms | 2.17 ms |
| 2000 glyphs of 9 px text (32 lines) | 0.35 ms | 0.80 ms | 0.27 ms |
| Full-screen blur behind, 4 iterations | 0.42 ms | 0.84 ms | 0.33 ms |
| Full-screen bloom, 3 iterations | 0.36 ms | 0.86 ms | 0.29 ms |

## Font generation

`fontgen` builds the bundled Inter atlas (201 glyphs, 4224 kerning pairs, 512x512) in about 0.9 s on the same
machine, as part of `./gradlew build`.
