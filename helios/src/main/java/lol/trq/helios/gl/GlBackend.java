/* SPDX-License-Identifier: MIT */
package lol.trq.helios.gl;

/**
 * The OpenGL calls Helios makes, behind one seam.
 *
 * <p>{@link Gl21Backend} implements it on OpenGL 2.1 with {@code ARB_framebuffer_object}, the baseline. A faster path
 * for newer contexts implements the same interface, and tests use a recording fake. Integer constants are the
 * standard OpenGL enum values.
 */
public interface GlBackend {
    /** {@code GL_BLEND}. */
    int BLEND = 0x0BE2;
    /** {@code GL_DEPTH_TEST}. */
    int DEPTH_TEST = 0x0B71;
    /** {@code GL_CULL_FACE}. */
    int CULL_FACE = 0x0B44;
    /** {@code GL_ALPHA_TEST}. */
    int ALPHA_TEST = 0x0BC0;
    /** {@code GL_SCISSOR_TEST}. */
    int SCISSOR_TEST = 0x0C11;
    /** {@code GL_TEXTURE_2D}. */
    int TEXTURE_2D = 0x0DE1;

    /** {@code GL_ONE}. */
    int ONE = 1;
    /** {@code GL_SRC_ALPHA}. */
    int SRC_ALPHA = 0x0302;
    /** {@code GL_ONE_MINUS_SRC_ALPHA}. */
    int ONE_MINUS_SRC_ALPHA = 0x0303;

    /** {@code GL_VERTEX_SHADER}. */
    int VERTEX_SHADER = 0x8B31;
    /** {@code GL_FRAGMENT_SHADER}. */
    int FRAGMENT_SHADER = 0x8B30;

    /** {@code GL_CURRENT_PROGRAM}. */
    int CURRENT_PROGRAM = 0x8B8D;
    /** {@code GL_FRAMEBUFFER_BINDING}. */
    int FRAMEBUFFER_BINDING = 0x8CA6;
    /** {@code GL_DEPTH_WRITEMASK}. */
    int DEPTH_WRITEMASK = 0x0B72;
    /** {@code GL_BLEND_SRC_RGB}. */
    int BLEND_SRC_RGB = 0x80C9;
    /** {@code GL_BLEND_DST_RGB}. */
    int BLEND_DST_RGB = 0x80C8;
    /** {@code GL_BLEND_SRC_ALPHA}. */
    int BLEND_SRC_ALPHA = 0x80CB;
    /** {@code GL_BLEND_DST_ALPHA}. */
    int BLEND_DST_ALPHA = 0x80CA;
    /** {@code GL_VIEWPORT}. */
    int VIEWPORT = 0x0BA2;
    /** {@code GL_CURRENT_COLOR}. */
    int CURRENT_COLOR = 0x0B00;

    /**
     * Reads an integer state value.
     *
     * @param pname the state to read
     * @return its first value
     */
    int getInteger(int pname);

    /**
     * Reads several integer values of one state, such as the four of {@code GL_VIEWPORT}.
     *
     * @param pname the state to read
     * @param out receives the values
     */
    void getIntegers(int pname, int[] out);

    /**
     * Reads several float values of one state, such as the four of {@code GL_CURRENT_COLOR}.
     *
     * @param pname the state to read
     * @param out receives the values
     */
    void getFloats(int pname, float[] out);

    /**
     * Returns whether a capability is enabled.
     *
     * @param cap the capability
     * @return {@code glIsEnabled(cap)}
     */
    boolean isEnabled(int cap);

    /**
     * Enables or disables a capability directly, bypassing any state cache.
     *
     * @param cap the capability
     * @param on whether to enable it
     */
    void setEnabled(int cap, boolean on);

    /**
     * {@code glBlendFuncSeparate}.
     *
     * @param srcRgb source RGB factor
     * @param dstRgb destination RGB factor
     * @param srcAlpha source alpha factor
     * @param dstAlpha destination alpha factor
     */
    void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    /**
     * {@code glDepthMask}.
     *
     * @param write whether depth writes are on
     */
    void depthMask(boolean write);

    /**
     * {@code glColor4f}.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @param a alpha
     */
    void color(float r, float g, float b, float a);

    /**
     * {@code glViewport}.
     *
     * @param x left
     * @param y bottom
     * @param width width in pixels
     * @param height height in pixels
     */
    void viewport(int x, int y, int width, int height);

    /**
     * {@code glUseProgram}.
     *
     * @param program the program, or 0
     */
    void useProgram(int program);

    /**
     * Creates and compiles a shader.
     *
     * @param type {@link #VERTEX_SHADER} or {@link #FRAGMENT_SHADER}
     * @param source the full source
     * @return the shader id; check {@link #shaderCompiled}
     */
    int compileShader(int type, String source);

    /**
     * Returns whether a shader compiled.
     *
     * @param shader the shader
     * @return its {@code GL_COMPILE_STATUS}
     */
    boolean shaderCompiled(int shader);

    /**
     * Returns a shader's info log.
     *
     * @param shader the shader
     * @return the log, possibly empty
     */
    String shaderLog(int shader);

    /**
     * {@code glDeleteShader}.
     *
     * @param shader the shader
     */
    void deleteShader(int shader);

    /**
     * Creates a program from shaders and links it.
     *
     * @param shaders the compiled shaders
     * @return the program id; check {@link #programLinked}
     */
    int linkProgram(int... shaders);

    /**
     * Returns whether a program linked.
     *
     * @param program the program
     * @return its {@code GL_LINK_STATUS}
     */
    boolean programLinked(int program);

    /**
     * Returns a program's info log.
     *
     * @param program the program
     * @return the log, possibly empty
     */
    String programLog(int program);

    /**
     * {@code glDeleteProgram}.
     *
     * @param program the program
     */
    void deleteProgram(int program);

    /**
     * {@code glGetUniformLocation}.
     *
     * @param program the program
     * @param name the uniform name
     * @return the location, or -1
     */
    int uniformLocation(int program, String name);

    /**
     * {@code glUniform1i}.
     *
     * @param location the location
     * @param v the value
     */
    void uniform1i(int location, int v);

    /**
     * {@code glUniform1f}.
     *
     * @param location the location
     * @param v the value
     */
    void uniform1f(int location, float v);

    /**
     * {@code glUniform2f}.
     *
     * @param location the location
     * @param x first component
     * @param y second component
     */
    void uniform2f(int location, float x, float y);

    /**
     * {@code glUniform4f}.
     *
     * @param location the location
     * @param x first component
     * @param y second component
     * @param z third component
     * @param w fourth component
     */
    void uniform4f(int location, float x, float y, float z, float w);

    /**
     * Creates a framebuffer with an RGBA8 colour texture and, optionally, a depth-stencil renderbuffer.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param depthStencil whether to attach a {@code GL_DEPTH24_STENCIL8} renderbuffer
     * @return {@code {framebuffer, colorTexture, depthStencilRenderbuffer or 0}}
     */
    int[] createFramebuffer(int width, int height, boolean depthStencil);

    /**
     * Deletes what {@link #createFramebuffer} created.
     *
     * @param ids the array {@link #createFramebuffer} returned
     */
    void deleteFramebuffer(int[] ids);

    /**
     * {@code glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)}.
     *
     * @param framebuffer the framebuffer, or 0 for the window
     */
    void bindFramebuffer(int framebuffer);

    /** Clears the bound framebuffer's colour to transparent black, leaving the clear colour as it was. */
    void clearColorTransparent();

    /**
     * Draws one quad in immediate mode, with texture coordinates on unit 0.
     *
     * @param x0 left
     * @param y0 top
     * @param x1 right
     * @param y1 bottom
     * @param u0 texture coordinate at the left
     * @param v0 texture coordinate at the top
     * @param u1 texture coordinate at the right
     * @param v1 texture coordinate at the bottom
     */
    void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1);

    /** {@code GL_ARRAY_BUFFER_BINDING}. */
    int ARRAY_BUFFER_BINDING = 0x8894;
    /** {@code GL_ACTIVE_TEXTURE}. */
    int ACTIVE_TEXTURE = 0x84E0;
    /** {@code GL_TEXTURE0}. */
    int TEXTURE0 = 0x84C0;
    /** {@code GL_TEXTURE_BINDING_2D}. */
    int TEXTURE_BINDING_2D = 0x8069;
    /** {@code GL_SCISSOR_BOX}. */
    int SCISSOR_BOX = 0x0C10;
    /** {@code GL_QUADS}. */
    int QUADS = 0x0007;
    /** {@code GL_FLOAT}. */
    int FLOAT = 0x1406;
    /** {@code GL_UNSIGNED_BYTE}. */
    int UNSIGNED_BYTE = 0x1401;

    /**
     * Links a program after binding attribute names to fixed locations.
     *
     * @param attributes attribute names, bound to their index in this array
     * @param shaders the compiled shaders
     * @return the program id; check {@link #programLinked}
     */
    int linkProgram(String[] attributes, int... shaders);

    /**
     * {@code glGenBuffers}.
     *
     * @return a new buffer name
     */
    int createBuffer();

    /**
     * {@code glDeleteBuffers}.
     *
     * @param buffer the buffer
     */
    void deleteBuffer(int buffer);

    /**
     * {@code glBindBuffer(GL_ARRAY_BUFFER, buffer)}.
     *
     * @param buffer the buffer, or 0
     */
    void bindArrayBuffer(int buffer);

    /**
     * Replaces the bound array buffer's storage with the bytes from position to limit, as a stream draw. Allocating
     * new storage each time lets the driver keep using the old one for draws still in flight.
     *
     * @param data the vertex bytes
     */
    void streamArrayBuffer(java.nio.ByteBuffer data);

    /**
     * Returns whether a generic vertex attribute array is enabled.
     *
     * @param index the attribute index
     * @return {@code GL_VERTEX_ATTRIB_ARRAY_ENABLED}
     */
    boolean vertexAttribEnabled(int index);

    /**
     * Enables or disables a generic vertex attribute array.
     *
     * @param index the attribute index
     * @param on whether it is enabled
     */
    void vertexAttribArray(int index, boolean on);

    /**
     * {@code glVertexAttribPointer} into the bound array buffer.
     *
     * @param index the attribute index
     * @param size components
     * @param type {@link #FLOAT} or {@link #UNSIGNED_BYTE}
     * @param normalized whether integers map to {@code [0, 1]}
     * @param stride bytes per vertex
     * @param offset byte offset in the vertex
     */
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset);

    /**
     * {@code glDrawArrays}.
     *
     * @param mode the primitive, such as {@link #QUADS}
     * @param first the first vertex
     * @param count the vertex count
     */
    void drawArrays(int mode, int first, int count);

    /**
     * {@code glActiveTexture}, directly.
     *
     * @param unit {@link #TEXTURE0} plus the unit number
     */
    void activeTexture(int unit);

    /**
     * {@code glBindTexture(GL_TEXTURE_2D, texture)}, directly.
     *
     * @param texture the texture
     */
    void bindTexture(int texture);

    /**
     * Copies the lower-left corner of the current read framebuffer into the bound 2D texture.
     *
     * @param width width in pixels
     * @param height height in pixels
     */
    void copyToBoundTexture(int width, int height);

    /**
     * {@code glScissor}.
     *
     * @param x left
     * @param y bottom
     * @param width width in pixels
     * @param height height in pixels
     */
    void scissor(int x, int y, int width, int height);
}
