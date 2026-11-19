# Benchmarks

Helios has two kinds of numbers. The CPU cost of its per-frame work comes from JMH, against a GL backend that does
nothing. The GPU cost of real workloads comes from the harness, inside the game.

## How to run

```sh
./gradlew :helios:jmh                                  # all CPU benchmarks, GC profiler on
./gradlew :helios:jmh -Pjmh.include=textLine           # one benchmark (on Windows, avoid | in the pattern)
./gradlew :harness:runHarness25 -Pbench                # GPU timings in the game
```

JMH results land in `helios/build/results/jmh/results.json`. The harness prints `HELIOS-BENCH` lines. Each workload
draws into an offscreen framebuffer the size of the window between two `glFinish` calls: 20 warm-up runs, then the
median and p90 of 30 runs. Each run counts as one frame for the streamed vertex buffers. GPU numbers include the CPU
work of building and uploading the batch. They vary by about 0.1 ms between runs, and the first workload of a run
reads high.

## CPU (JMH)

Windows 10, Java 25.0.4, Helios 0.1.1. Time per operation and bytes allocated per operation. Shape benchmarks are per
shape, amortized over a batch of 1000 shapes and its flush.

| Benchmark | What | Time | Allocated |
|---|---|---|---|
| `rectSolid` | one rounded rect, one colour | 56.5 ns | 0.1 B |
| `rectGradient` | one rounded rect, linear gradient | 100.7 ns | 0.1 B |
| `line` | one line with round caps | 62.3 ns | 0.1 B |
| `textLine` | 62-character line: layout, kerning, glyphs | 3181 ns | 184 B |
| `textLineShadow` | the same with a classic shadow (twice the glyphs) | 6414 ns | 184 B |
| `textFormatted` | a line with colour and format codes | 2773 ns | 184 B |
| `textWidth` | measure a line | 132 ns | 0 B |
| `stateGuard` | open a guard, touch six slots, restore | 11.8 ns | 136 B |
| `blurBehindCpu` | blur-behind bookkeeping (GL calls do nothing) | 474 ns | 2584 B |
| `colorLerpHsb` | HSB interpolation of two colours | 18.4 ns | 0 B |
| `projectBox` | project an entity-sized box to its 2D bounds | 68.8 ns | 0 B |
| `animationValue` | read an eased animation, including the clock read | 34.0 ns | 0 B |
| `smoothUpdate` | advance a smoothed value, including the clock read | 33.2 ns | 0 B |
| `preprocessShader` | expand `shape.fsh` (load time only) | 41.9 µs | 146 KB |

The 184 B per text line is the state guard of the batch flush.

## GPU (harness)

Windows 10, NVIDIA GeForce RTX 4070 (driver 581.80), 1281x720 framebuffer, Java 25.0.4, Lazarus 0.4.2 core renderer,
Helios 0.1.1. One run each, without and with OptiFine HD U M5.

| Workload | Median | p90 | Median with OptiFine |
|---|---|---|---|
| 10000 rounded rects (24x12, translucent) | 4.27 ms | 5.77 ms | 5.05 ms |
| 10000 circles with a radial gradient | 2.63 ms | 3.49 ms | 1.57 ms |
| 2000 glyphs of 9 px text (32 lines) | 0.28 ms | 0.40 ms | 0.26 ms |
| One MSDF text batch (1 line, 1 draw call) | 0.033 ms | 0.034 ms | 0.032 ms |
| One blur pass (1 iteration, full screen) | 0.12 ms | 0.13 ms | 0.06 ms |
| Full-screen blur behind, 4 iterations | 0.27 ms | 0.30 ms | 0.18 ms |
| Full-screen bloom, 3 iterations | 0.23 ms | 0.26 ms | 0.15 ms |

macOS, Apple M2 ("4.1 Metal"), 1708x960 Retina framebuffer, Java 25. Measured with a pre-release build of the
Lazarus core renderer (before 0.2.0), one run per column. `map` is `-Dhelios.stream=map`, an unsynchronized
`glMapBufferRange` into the fenced ring:

| Workload | `glBufferSubData` (default) | `map` |
|---|---|---|
| 10000 rounded rects | 7.99 ms | 8.24 ms |
| 10000 circles with a radial gradient | 4.81 ms | 8.06 ms |
| 2000 glyphs of 9 px text (32 lines) | 0.53 ms | 0.60 ms |
| One MSDF text batch (1 line, 1 draw call) | 0.21 ms | 0.21 ms |
| One blur pass (1 iteration, full screen) | 0.54 ms | 0.77 ms |
| Full-screen blur behind, 4 iterations | 1.27 ms | 1.82 ms |
| Full-screen bloom, 3 iterations | 1.21 ms | 1.25 ms |

Mapping is not faster anywhere on the M2, so `glBufferSubData` is the default. The M2 framebuffer has 1.8 times the
pixels of the Windows one, which accounts for most of the gap in the full-screen passes.

## Font generation

`fontgen` builds the bundled Inter atlas (201 glyphs, 4224 kerning pairs, 512x512) during `./gradlew build`, in about
0.9 s on the Windows machine above.
