/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads shader source text by a {@code /}-separated name. */
@FunctionalInterface
public interface SourceResolver {
    /**
     * Loads a file.
     *
     * @param name the normalized name, with no leading slash
     * @return the text, or null when there is no such file
     */
    String resolve(String name);

    /**
     * Resolves names as class path resources under a root, through a class loader.
     *
     * @param loader the class loader
     * @param root the resource root, such as {@code "lol/trq/helios/shaders"}
     * @return the resolver
     */
    static SourceResolver classpath(ClassLoader loader, String root) {
        String prefix = root.endsWith("/") ? root : root + "/";
        return name -> {
            try (InputStream in = loader.getResourceAsStream(prefix + name)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * Resolves names as files under a directory, which is what hot reload during development uses.
     *
     * @param root the directory
     * @return the resolver
     */
    static SourceResolver directory(Path root) {
        return name -> {
            Path file = root.resolve(name).normalize();
            if (!file.startsWith(root.normalize()) || !Files.isRegularFile(file)) {
                return null;
            }
            try {
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * Tries this resolver, then another.
     *
     * @param next the fallback
     * @return the combined resolver
     */
    default SourceResolver or(SourceResolver next) {
        return name -> {
            String s = resolve(name);
            return s != null ? s : next.resolve(name);
        };
    }
}
