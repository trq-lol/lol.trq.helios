/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Development hot reload: watches a shader source directory and rebuilds every program of a {@link ShaderLoader}
 * when a file changes. A program that fails to rebuild keeps running its last good version.
 *
 * <p>Call {@link #poll()} once per frame on the render thread. It scans file modification times at most every
 * {@code intervalMillis}, so it is cheap, and never spawns threads.
 */
public final class ShaderHotReload {
    /** What a poll did. */
    public record Result(boolean reloaded, List<String> failures) {
        static final Result NOTHING = new Result(false, List.of());
    }

    private final ShaderLoader loader;
    private final Path dir;
    private final long intervalNanos;
    private final LongSupplier clock;
    private long lastScan = Long.MIN_VALUE;
    private long fingerprint;

    /**
     * Starts watching.
     *
     * @param loader the loader whose programs are rebuilt; it should resolve sources from {@code dir} first
     * @param dir the shader source directory
     * @param intervalMillis the minimum time between scans
     */
    public ShaderHotReload(ShaderLoader loader, Path dir, long intervalMillis) {
        this(loader, dir, intervalMillis, System::nanoTime);
    }

    ShaderHotReload(ShaderLoader loader, Path dir, long intervalMillis, LongSupplier clock) {
        this.loader = loader;
        this.dir = dir;
        this.intervalNanos = intervalMillis * 1_000_000L;
        this.clock = clock;
        this.fingerprint = scan();
    }

    /**
     * Reloads if a source file changed since the last poll.
     *
     * @return whether a reload ran, and the programs that failed to rebuild
     */
    public Result poll() {
        long now = clock.getAsLong();
        if (lastScan != Long.MIN_VALUE && now - lastScan < intervalNanos) {
            return Result.NOTHING;
        }
        lastScan = now;
        long current = scan();
        if (current == fingerprint) {
            return Result.NOTHING;
        }
        fingerprint = current;
        return new Result(true, loader.reloadAll());
    }

    private long scan() {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return p.toString().hashCode() * 31L
                                    + Files.getLastModifiedTime(p).toMillis()
                                    + Files.size(p) * 7919L;
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
