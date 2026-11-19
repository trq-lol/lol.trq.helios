/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lol.trq.helios.gl.GlBackend;

/**
 * Compiles and links programs from preprocessed sources, and reloads them.
 *
 * <p>Compile and link failures throw a {@link ShaderException} whose message carries the driver log with
 * {@code file:line} locations. {@link #reloadAll()} rebuilds every program from its sources; a program that fails to
 * rebuild keeps its last good version.
 */
public final class ShaderLoader {
    private final GlBackend gl;
    private final ShaderPreprocessor preprocessor;
    private final Map<ShaderProgram, Spec> programs = new LinkedHashMap<>();

    /**
     * Creates a loader.
     *
     * @param gl the backend
     * @param resolver loads source files
     * @param glslVersion the default {@code #version}
     */
    public ShaderLoader(GlBackend gl, SourceResolver resolver, int glslVersion) {
        this.gl = gl;
        this.preprocessor = new ShaderPreprocessor(resolver, glslVersion);
    }

    /**
     * Loads a program.
     *
     * @param vertex the vertex shader entry file
     * @param fragment the fragment shader entry file
     * @param defines defines injected into both stages
     * @return the linked program
     * @throws ShaderException on a preprocess, compile or link failure
     */
    public ShaderProgram load(String vertex, String fragment, Map<String, String> defines) {
        return load(vertex, fragment, defines, List.of());
    }

    /**
     * Loads a program and binds vertex attributes to fixed locations before linking.
     *
     * @param vertex the vertex shader entry file
     * @param fragment the fragment shader entry file
     * @param defines defines injected into both stages
     * @param attributes attribute names, bound to their index in this list
     * @return the linked program
     * @throws ShaderException on a preprocess, compile or link failure
     */
    public ShaderProgram load(String vertex, String fragment, Map<String, String> defines, List<String> attributes) {
        Spec spec = new Spec(vertex, fragment, Map.copyOf(defines), List.copyOf(attributes));
        ShaderProgram program = new ShaderProgram(gl, vertex + "+" + fragment, build(spec));
        programs.put(program, spec);
        return program;
    }

    /**
     * Rebuilds every program this loader made and still owns.
     *
     * @return the failures, one message per program that kept its old version
     */
    public List<String> reloadAll() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<ShaderProgram, Spec> e : programs.entrySet()) {
            if (e.getKey().id() == 0) {
                continue;
            }
            try {
                e.getKey().replace(build(e.getValue()));
            } catch (ShaderException | java.io.UncheckedIOException ex) {
                failures.add(e.getKey().name() + ": " + ex.getMessage());
            }
        }
        return failures;
    }

    private int build(Spec spec) {
        int vs = compile(GlBackend.VERTEX_SHADER, spec.vertex, spec.defines);
        int fs;
        try {
            fs = compile(GlBackend.FRAGMENT_SHADER, spec.fragment, spec.defines);
        } catch (ShaderException e) {
            gl.deleteShader(vs);
            throw e;
        }
        int program = spec.attributes.isEmpty()
                ? gl.linkProgram(vs, fs)
                : gl.linkProgram(spec.attributes.toArray(new String[0]), vs, fs);
        gl.deleteShader(vs);
        gl.deleteShader(fs);
        if (!gl.programLinked(program)) {
            String log = gl.programLog(program);
            gl.deleteProgram(program);
            throw new ShaderException("link failed for " + spec.vertex + " + " + spec.fragment + ":\n" + log.strip());
        }
        return program;
    }

    private int compile(int type, String entry, Map<String, String> defines) {
        ShaderPreprocessor.Preprocessed src = preprocessor.process(entry, defines);
        int shader = gl.compileShader(type, src.source());
        if (!gl.shaderCompiled(shader)) {
            String log = src.describe(gl.shaderLog(shader));
            gl.deleteShader(shader);
            throw new ShaderException("compile failed for " + entry + ":\n" + log.strip());
        }
        return shader;
    }

    private record Spec(String vertex, String fragment, Map<String, String> defines, List<String> attributes) {}
}
