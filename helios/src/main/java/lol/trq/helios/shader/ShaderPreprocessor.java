/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code #include} directives and injects defines, keeping line numbers traceable.
 *
 * <p>The output starts with {@code #version}, then one {@code #define} per define, then the source. Each file gets a
 * GLSL source-string number, and {@code #line} directives mark every switch between files, so a driver error such as
 * {@code 0(12)} or {@code 0:12} names the file and line through {@link Preprocessed#describe}.
 *
 * <p>{@code #include "name"} resolves relative to the including file; {@code #include <name>} from the root. A file
 * is included at most once per shader; an include cycle is an error. Helios has one dialect, GLSL 4.10 core: the
 * output always declares {@code #version 410 core}, an entry file may repeat exactly that line, and any other
 * {@code #version} is an error.
 */
public final class ShaderPreprocessor {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+([\"<])([^\">]+)[\">]\\s*(//.*)?$");
    private static final Pattern VERSION = Pattern.compile("^\\s*#\\s*version\\s+(\\d+)(\\s+\\w+)?\\s*$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** The one GLSL version every Helios shader uses. */
    public static final String VERSION_LINE = "410 core";

    private final SourceResolver resolver;

    /**
     * Creates a preprocessor.
     *
     * @param resolver loads source files by name
     */
    public ShaderPreprocessor(SourceResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Expands a shader.
     *
     * @param entry the name of the entry file
     * @param defines defines to inject, by name; a null or empty value defines the name alone
     * @return the expanded source and its file table
     * @throws ShaderException if a file is missing, an include cycles, or a define name is invalid
     */
    public Preprocessed process(String entry, Map<String, String> defines) {
        Run run = new Run();
        StringBuilder body = new StringBuilder();
        run.expand(normalize(entry), body, true);
        StringBuilder out = new StringBuilder();
        out.append("#version ").append(VERSION_LINE).append('\n');
        for (Map.Entry<String, String> d : new LinkedHashMap<>(defines).entrySet()) {
            if (!NAME.matcher(d.getKey()).matches()) {
                throw new ShaderException("invalid define name: " + d.getKey());
            }
            out.append("#define ").append(d.getKey());
            if (d.getValue() != null && !d.getValue().isEmpty()) {
                out.append(' ').append(d.getValue().replace('\n', ' '));
            }
            out.append('\n');
        }
        out.append(body);
        return new Preprocessed(out.toString(), List.copyOf(run.files));
    }

    private final class Run {
        final List<String> files = new ArrayList<>();
        final Set<String> included = new HashSet<>();
        final Deque<String> stack = new ArrayDeque<>();

        String expand(String name, StringBuilder out, boolean isEntry) {
            if (stack.contains(name)) {
                throw new ShaderException("include cycle: " + String.join(" -> ", stack) + " -> " + name);
            }
            String text = resolver.resolve(name);
            if (text == null) {
                String from = stack.isEmpty() ? "" : " (included from " + stack.peek() + ")";
                throw new ShaderException("shader source not found: " + name + from);
            }
            included.add(name);
            int index = files.size();
            files.add(name);
            stack.push(name);
            String version = null;
            out.append("#line 1 ").append(index).append('\n');
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            int count = text.endsWith("\n") ? lines.length - 1 : lines.length;
            for (int i = 0; i < count; i++) {
                String line = lines[i];
                int lineNo = i + 1;
                Matcher v = VERSION.matcher(line);
                if (v.matches()) {
                    if (!isEntry) {
                        throw new ShaderException(name + ":" + lineNo + ": #version is only allowed in the entry file");
                    }
                    version = v.group(1)
                            + (v.group(2) == null ? "" : " " + v.group(2).strip());
                    if (!VERSION_LINE.equals(version)) {
                        throw new ShaderException(name + ":" + lineNo + ": Helios shaders are #version " + VERSION_LINE
                                + ", not " + version);
                    }
                    out.append('\n');
                    continue;
                }
                Matcher inc = INCLUDE.matcher(line);
                if (inc.matches()) {
                    String target = inc.group(1).equals("<") ? normalize(inc.group(2)) : relative(name, inc.group(2));
                    if (stack.contains(target) || !included.contains(target)) {
                        expand(target, out, false);
                    }
                    out.append("#line ")
                            .append(lineNo + 1)
                            .append(' ')
                            .append(index)
                            .append('\n');
                    continue;
                }
                out.append(line).append('\n');
            }
            stack.pop();
            return version;
        }
    }

    static String relative(String from, String target) {
        int slash = from.lastIndexOf('/');
        return normalize(slash < 0 ? target : from.substring(0, slash + 1) + target);
    }

    static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String p : path.replace('\\', '/').split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (parts.isEmpty()) {
                    throw new ShaderException("include escapes the shader root: " + path);
                }
                parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }
        return String.join("/", parts);
    }

    /**
     * A preprocessed shader.
     *
     * @param source the full source to hand to the driver
     * @param files the file names, indexed by GLSL source-string number
     */
    public record Preprocessed(String source, List<String> files) {
        /**
         * Rewrites a driver info log so source-string numbers become file names.
         *
         * @param log the driver's log
         * @return the log with locations as {@code file:line}
         */
        public String describe(String log) {
            return ShaderLog.describe(log, files);
        }
    }
}
