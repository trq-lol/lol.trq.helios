/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import java.util.HashMap;
import java.util.Map;
import lol.trq.helios.gl.GlBackend;

/**
 * A linked program with a uniform location cache. A uniform the driver optimised away resolves to -1 once, is
 * cached as -1, and setting it does nothing, as in OpenGL.
 *
 * <p>Setters apply to the program currently in use; bind it first, through a
 * {@link lol.trq.helios.state.StateGuard#program(int)}.
 */
public final class ShaderProgram implements AutoCloseable {
    private final GlBackend gl;
    private final String name;
    private final Map<String, Integer> locations = new HashMap<>();
    private int id;

    ShaderProgram(GlBackend gl, String name, int id) {
        this.gl = gl;
        this.name = name;
        this.id = id;
    }

    /**
     * Returns the GL program id.
     *
     * @return the id, or 0 after {@link #close()}
     */
    public int id() {
        return id;
    }

    /**
     * Returns the name this program was loaded under.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns a uniform location, from the cache after the first lookup.
     *
     * @param uniform the uniform name
     * @return the location, or -1 if the program has no such active uniform
     */
    public int location(String uniform) {
        Integer cached = locations.get(uniform);
        if (cached != null) {
            return cached;
        }
        int loc = gl.uniformLocation(id, uniform);
        locations.put(uniform, loc);
        return loc;
    }

    /**
     * Sets an {@code int} or sampler uniform.
     *
     * @param uniform the name
     * @param v the value
     */
    public void set(String uniform, int v) {
        int loc = location(uniform);
        if (loc >= 0) {
            gl.uniform1i(loc, v);
        }
    }

    /**
     * Sets a {@code float} uniform.
     *
     * @param uniform the name
     * @param v the value
     */
    public void set(String uniform, float v) {
        int loc = location(uniform);
        if (loc >= 0) {
            gl.uniform1f(loc, v);
        }
    }

    /**
     * Sets a {@code vec2} uniform.
     *
     * @param uniform the name
     * @param x first component
     * @param y second component
     */
    public void set(String uniform, float x, float y) {
        int loc = location(uniform);
        if (loc >= 0) {
            gl.uniform2f(loc, x, y);
        }
    }

    /**
     * Sets a {@code mat4} uniform.
     *
     * @param uniform the name
     * @param m the matrix, 16 floats column-major
     */
    public void set(String uniform, float[] m) {
        int loc = location(uniform);
        if (loc >= 0) {
            gl.uniformMatrix4(loc, m);
        }
    }

    /**
     * Sets a {@code vec4} uniform.
     *
     * @param uniform the name
     * @param x first component
     * @param y second component
     * @param z third component
     * @param w fourth component
     */
    public void set(String uniform, float x, float y, float z, float w) {
        int loc = location(uniform);
        if (loc >= 0) {
            gl.uniform4f(loc, x, y, z, w);
        }
    }

    /**
     * Swaps in a newly linked program, as hot reload does, and clears the location cache.
     *
     * @param newId the new program
     */
    void replace(int newId) {
        if (id != 0) {
            gl.deleteProgram(id);
        }
        id = newId;
        locations.clear();
    }

    /** Deletes the program. */
    @Override
    public void close() {
        replace(0);
    }
}
