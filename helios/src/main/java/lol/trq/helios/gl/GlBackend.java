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
}
