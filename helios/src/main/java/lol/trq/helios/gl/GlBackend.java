/* SPDX-License-Identifier: MIT */
package lol.trq.helios.gl;

import java.nio.ByteBuffer;

/**
 * The OpenGL calls Helios makes, behind one seam.
 *
 * <p>{@link Gl41CoreBackend} implements it on an OpenGL 4.1 core profile context, the only target. Tests and
 * benchmarks use fakes. Integer constants are the standard OpenGL enum values.
 */
public interface GlBackend {
    /** {@code GL_BLEND}. */
    int BLEND = 0x0BE2;
    /** {@code GL_DEPTH_TEST}. */
    int DEPTH_TEST = 0x0B71;
    /** {@code GL_CULL_FACE}. */
    int CULL_FACE = 0x0B44;
    /** {@code GL_SCISSOR_TEST}. */
    int SCISSOR_TEST = 0x0C11;
    /** {@code GL_STENCIL_TEST}. */
    int STENCIL_TEST = 0x0B90;

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
    /** {@code GL_ARRAY_BUFFER_BINDING}. */
    int ARRAY_BUFFER_BINDING = 0x8894;
    /** {@code GL_VERTEX_ARRAY_BINDING}. */
    int VERTEX_ARRAY_BINDING = 0x85B5;
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
    /** {@code GL_ACTIVE_TEXTURE}. */
    int ACTIVE_TEXTURE = 0x84E0;
    /** {@code GL_TEXTURE0}. */
    int TEXTURE0 = 0x84C0;
    /** {@code GL_TEXTURE_BINDING_2D}. */
    int TEXTURE_BINDING_2D = 0x8069;
    /** {@code GL_SCISSOR_BOX}. */
    int SCISSOR_BOX = 0x0C10;
    /** {@code GL_COLOR_WRITEMASK}. */
    int COLOR_WRITEMASK = 0x0C23;

    /** {@code GL_ARRAY_BUFFER}. */
    int ARRAY_BUFFER = 0x8892;
    /** {@code GL_ELEMENT_ARRAY_BUFFER}. */
    int ELEMENT_ARRAY_BUFFER = 0x8893;
    /** {@code GL_STREAM_DRAW}. */
    int STREAM_DRAW = 0x88E0;
    /** {@code GL_STATIC_DRAW}. */
    int STATIC_DRAW = 0x88E4;

    /** {@code GL_TRIANGLES}. */
    int TRIANGLES = 0x0004;
    /** {@code GL_FLOAT}. */
    int FLOAT = 0x1406;
    /** {@code GL_UNSIGNED_BYTE}. */
    int UNSIGNED_BYTE = 0x1401;
    /** {@code GL_UNSIGNED_SHORT}. */
    int UNSIGNED_SHORT = 0x1403;
    /** {@code GL_UNSIGNED_INT}. */
    int UNSIGNED_INT = 0x1405;

    /** {@code GL_STENCIL_FUNC}. */
    int STENCIL_FUNC = 0x0B92;
    /** {@code GL_STENCIL_VALUE_MASK}. */
    int STENCIL_VALUE_MASK = 0x0B93;
    /** {@code GL_STENCIL_FAIL}. */
    int STENCIL_FAIL = 0x0B94;
    /** {@code GL_STENCIL_PASS_DEPTH_FAIL}. */
    int STENCIL_PASS_DEPTH_FAIL = 0x0B95;
    /** {@code GL_STENCIL_PASS_DEPTH_PASS}. */
    int STENCIL_PASS_DEPTH_PASS = 0x0B96;
    /** {@code GL_STENCIL_REF}. */
    int STENCIL_REF = 0x0B97;
    /** {@code GL_STENCIL_WRITEMASK}. */
    int STENCIL_WRITEMASK = 0x0B98;
    /** {@code GL_ALWAYS}. */
    int ALWAYS = 0x0207;
    /** {@code GL_EQUAL}. */
    int EQUAL = 0x0202;
    /** {@code GL_NOTEQUAL}. */
    int NOTEQUAL = 0x0205;
    /** {@code GL_KEEP}. */
    int KEEP = 0x1E00;
    /** {@code GL_REPLACE}. */
    int REPLACE = 0x1E01;

    /**
     * Reads one integer state value.
     *
     * @param pname the state enum
     * @return the value
     */
    int getInteger(int pname);

    /**
     * Reads an integer state vector.
     *
     * @param pname the state enum
     * @param out receives the values
     */
    void getIntegers(int pname, int[] out);

    /**
     * Reads the host's current model-view and projection matrices, column-major. On Lazarus core mode they come
     * from the emulated matrix stacks without a driver query.
     *
     * @param modelView receives the model-view matrix, 16 floats
     * @param projection receives the projection matrix, 16 floats
     */
    void matrices(float[] modelView, float[] projection);

    /**
     * Returns whether a capability is on.
     *
     * @param cap the capability
     * @return true when enabled
     */
    boolean isEnabled(int cap);

    /**
     * Turns a capability on or off.
     *
     * @param cap the capability
     * @param on the new state
     */
    void setEnabled(int cap, boolean on);

    /**
     * Sets separate RGB and alpha blend factors.
     *
     * @param srcRgb source RGB factor
     * @param dstRgb destination RGB factor
     * @param srcAlpha source alpha factor
     * @param dstAlpha destination alpha factor
     */
    void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    /**
     * Turns depth writes on or off.
     *
     * @param write the new state
     */
    void depthMask(boolean write);

    /**
     * Sets the viewport, in framebuffer pixels.
     *
     * @param x left
     * @param y bottom
     * @param width width
     * @param height height
     */
    void viewport(int x, int y, int width, int height);

    /**
     * Binds a program.
     *
     * @param program the program, or 0
     */
    void useProgram(int program);

    /**
     * Creates and compiles a shader.
     *
     * @param type {@link #VERTEX_SHADER} or {@link #FRAGMENT_SHADER}
     * @param source the full source
     * @return the shader name
     */
    int compileShader(int type, String source);

    /**
     * Returns whether a shader compiled.
     *
     * @param shader the shader
     * @return true on success
     */
    boolean shaderCompiled(int shader);

    /**
     * Returns a shader's info log.
     *
     * @param shader the shader
     * @return the log
     */
    String shaderLog(int shader);

    /**
     * Deletes a shader.
     *
     * @param shader the shader
     */
    void deleteShader(int shader);

    /**
     * Links a program from compiled shaders. Attribute and output locations come from {@code layout(location)}
     * qualifiers in the source.
     *
     * @param shaders the shaders
     * @return the program name
     */
    int linkProgram(int... shaders);

    /**
     * Returns whether a program linked.
     *
     * @param program the program
     * @return true on success
     */
    boolean programLinked(int program);

    /**
     * Returns a program's info log.
     *
     * @param program the program
     * @return the log
     */
    String programLog(int program);

    /**
     * Deletes a program.
     *
     * @param program the program
     */
    void deleteProgram(int program);

    /**
     * Looks up a uniform.
     *
     * @param program the program
     * @param name the uniform name
     * @return the location, or -1
     */
    int uniformLocation(int program, String name);

    /**
     * Sets an int or sampler uniform on the bound program.
     *
     * @param location the location
     * @param v the value
     */
    void uniform1i(int location, int v);

    /**
     * Sets a float uniform on the bound program.
     *
     * @param location the location
     * @param v the value
     */
    void uniform1f(int location, float v);

    /**
     * Sets a vec2 uniform on the bound program.
     *
     * @param location the location
     * @param x x
     * @param y y
     */
    void uniform2f(int location, float x, float y);

    /**
     * Sets a vec4 uniform on the bound program.
     *
     * @param location the location
     * @param x x
     * @param y y
     * @param z z
     * @param w w
     */
    void uniform4f(int location, float x, float y, float z, float w);

    /**
     * Sets a mat4 uniform on the bound program.
     *
     * @param location the location
     * @param m the matrix, column-major
     */
    void uniformMatrix4(int location, float[] m);

    /**
     * Creates a framebuffer with an RGBA8 colour texture and optionally a depth-stencil renderbuffer. Leaves the
     * previous bindings in place.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param depthStencil whether to attach a 24/8 depth-stencil buffer
     * @return framebuffer, texture and renderbuffer names (renderbuffer 0 when none)
     */
    int[] createFramebuffer(int width, int height, boolean depthStencil);

    /**
     * Deletes what {@link #createFramebuffer} made.
     *
     * @param ids the names it returned
     */
    void deleteFramebuffer(int[] ids);

    /**
     * Binds a framebuffer for drawing and reading.
     *
     * @param framebuffer the framebuffer, or 0 for the default
     */
    void bindFramebuffer(int framebuffer);

    /** Clears the bound framebuffer's colour to transparent black, keeping the clear colour. */
    void clearColorTransparent();

    /**
     * Creates a vertex array object.
     *
     * @return its name
     */
    int createVertexArray();

    /**
     * Deletes a vertex array object.
     *
     * @param vao the name
     */
    void deleteVertexArray(int vao);

    /**
     * Binds a vertex array object.
     *
     * @param vao the name, or 0
     */
    void bindVertexArray(int vao);

    /**
     * Creates a buffer object.
     *
     * @return its name
     */
    int createBuffer();

    /**
     * Deletes a buffer object.
     *
     * @param buffer the name
     */
    void deleteBuffer(int buffer);

    /**
     * Binds a buffer.
     *
     * @param target {@link #ARRAY_BUFFER} or {@link #ELEMENT_ARRAY_BUFFER}
     * @param buffer the name, or 0
     */
    void bindBuffer(int target, int buffer);

    /**
     * Uploads a buffer's whole contents, replacing its storage.
     *
     * @param target the binding target
     * @param data the bytes, from position to limit
     * @param usage {@link #STREAM_DRAW} or {@link #STATIC_DRAW}
     */
    void bufferData(int target, ByteBuffer data, int usage);

    /**
     * Allocates, or orphans, a buffer's storage without data.
     *
     * @param target the binding target
     * @param size the size in bytes
     * @param usage the usage hint
     */
    void bufferStorage(int target, long size, int usage);

    /**
     * Writes into part of a buffer.
     *
     * @param target the binding target
     * @param offset the byte offset
     * @param data the bytes, from position to limit
     */
    void bufferSubData(int target, long offset, ByteBuffer data);

    /**
     * Writes into part of a buffer through an unsynchronized mapping that invalidates the range: no wait on the GPU.
     * The caller guarantees, with fences, that the GPU no longer reads that range.
     *
     * @param target the binding target
     * @param offset the byte offset
     * @param data the bytes, from position to limit
     */
    void bufferWriteUnsynchronized(int target, long offset, ByteBuffer data);

    /**
     * Enables a vertex attribute in the bound vertex array.
     *
     * @param index the attribute location
     */
    void enableVertexAttrib(int index);

    /**
     * Describes a vertex attribute in the bound vertex array, reading from the bound array buffer.
     *
     * @param index the attribute location
     * @param size components
     * @param type component type
     * @param normalized whether integers are normalized
     * @param stride bytes between vertices
     * @param offset byte offset of the first component
     */
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset);

    /**
     * Draws non-indexed primitives from the bound vertex array.
     *
     * @param mode the primitive mode
     * @param first the first vertex
     * @param count the vertex count
     */
    void drawArrays(int mode, int first, int count);

    /**
     * Draws indexed primitives from the bound vertex array, adding a base to every index.
     *
     * @param mode the primitive mode
     * @param count the index count
     * @param type the index type
     * @param offset byte offset into the element buffer
     * @param baseVertex added to every index
     */
    void drawElements(int mode, int count, int type, long offset, int baseVertex);

    /**
     * Inserts a fence after the commands issued so far.
     *
     * @return the sync object
     */
    long fence();

    /**
     * Waits for a fence, flushing commands first.
     *
     * @param sync the fence
     * @param timeoutNanos the longest wait
     * @return true when the fence signalled
     */
    boolean waitFence(long sync, long timeoutNanos);

    /**
     * Deletes a fence.
     *
     * @param sync the fence
     */
    void deleteFence(long sync);

    /**
     * Selects the active texture unit.
     *
     * @param unit {@link #TEXTURE0} plus the unit
     */
    void activeTexture(int unit);

    /**
     * Binds a 2D texture on the active unit.
     *
     * @param texture the name, or 0
     */
    void bindTexture(int texture);

    /**
     * Copies the bottom-left of the read framebuffer into the bound texture.
     *
     * @param width width in pixels
     * @param height height in pixels
     */
    void copyToBoundTexture(int width, int height);

    /**
     * Sets the scissor box, in framebuffer pixels.
     *
     * @param x left
     * @param y bottom
     * @param width width
     * @param height height
     */
    void scissor(int x, int y, int width, int height);

    /**
     * Creates a linear-filtered RGB8 texture.
     *
     * @param width width
     * @param height height
     * @param rgb tightly packed RGB bytes
     * @return the texture name
     */
    int createRgbTexture(int width, int height, ByteBuffer rgb);

    /**
     * Deletes a texture.
     *
     * @param texture the name
     */
    void deleteTexture(int texture);

    /**
     * Returns the stencil bits of the bound draw framebuffer.
     *
     * @return the bit count, 0 when there is no stencil buffer
     */
    int stencilBits();

    /**
     * Sets the stencil test.
     *
     * @param func the comparison
     * @param ref the reference value
     * @param mask the value mask
     */
    void stencilFunc(int func, int ref, int mask);

    /**
     * Sets the stencil operations.
     *
     * @param sfail on stencil fail
     * @param dpfail on depth fail
     * @param dppass on pass
     */
    void stencilOp(int sfail, int dpfail, int dppass);

    /**
     * Sets the stencil write mask.
     *
     * @param mask the mask
     */
    void stencilMask(int mask);

    /**
     * Clears the bound framebuffer's stencil, keeping the clear value.
     *
     * @param value the value to clear to
     */
    void clearStencil(int value);

    /**
     * Sets the colour write mask.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @param a alpha
     */
    void colorMask(boolean r, boolean g, boolean b, boolean a);
}
