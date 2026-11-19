/* SPDX-License-Identifier: MIT */
package lol.trq.helios.gl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GLCapabilities;

/**
 * The backend on an OpenGL 4.1 core profile context, as Lazarus creates with {@code -Dlazarus.gl=core}.
 *
 * <p>Binding calls go through {@code GL15}, {@code GL20} and {@code GL30} without the {@code C} suffix, which Lazarus
 * intercepts to keep its cache of the bound buffer, program and vertex array true. Draw calls go through the
 * {@code C} classes, because Lazarus sends {@code GL11.glDrawArrays} to its fixed-function emulator. Capability
 * toggles and texture binds use {@code GL11} and {@code GL13}, which the emulator tracks without querying the driver.
 */
public final class Gl41CoreBackend implements GlBackend {
    /** {@code GL_CONTEXT_PROFILE_MASK}. */
    public static final int CONTEXT_PROFILE_MASK = 0x9126;
    /** {@code GL_CONTEXT_CORE_PROFILE_BIT}. */
    public static final int CONTEXT_CORE_PROFILE_BIT = 0x1;

    private static final String EMULATOR = "lol.trq.lazarus.lwjgl3.gl.CoreGl";
    private static final String STATE = "lol.trq.lazarus.lwjgl3.gl.GlState";
    private static final int GL_MODELVIEW_MATRIX = 0x0BA6;
    private static final int GL_PROJECTION_MATRIX = 0x0BA7;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_RGB8 = 0x8051;
    private static final int GL_DEPTH24_STENCIL8 = 0x88F0;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_COLOR_CLEAR_VALUE = 0x0C22;
    private static final int GL_STENCIL_CLEAR_VALUE = 0x0B91;

    private final float[] clear = new float[4];
    private final MethodHandle getMatrix;
    // Lazarus's GlState: matrices and bindings answered from the emulator, no driver query. Null when the running
    // Lazarus predates it; then the emulator's glGetFloat and driver queries are used.
    private final MethodHandle stateModelView;
    private final MethodHandle stateProjection;
    private final MethodHandle stateProgram;
    private final MethodHandle stateVertexArray;
    private final MethodHandle stateArrayBuffer;
    private final MethodHandle stateViewport;
    private final FloatBuffer matrix =
            ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();

    /**
     * Checks the context and binds to the emulated matrix stacks.
     *
     * @throws IllegalStateException if the context is not OpenGL 4.1 core, or Lazarus core mode is not running
     */
    public Gl41CoreBackend() {
        requireCoreContext();
        getMatrix = emulatorMatrixQuery();
        Class<?> state = stateClass();
        stateModelView = state(state, "modelView", MethodType.methodType(void.class, float[].class));
        stateProjection = state(state, "projection", MethodType.methodType(void.class, float[].class));
        stateProgram = state(state, "program", MethodType.methodType(int.class));
        stateVertexArray = state(state, "vertexArray", MethodType.methodType(int.class));
        stateArrayBuffer = state(state, "arrayBuffer", MethodType.methodType(int.class));
        stateViewport = state(state, "viewport", MethodType.methodType(boolean.class, int[].class));
    }

    private static Class<?> stateClass() {
        try {
            Class<?> c = Class.forName(STATE, true, ClassLoader.getSystemClassLoader());
            boolean active = (boolean) MethodHandles.publicLookup()
                    .findStatic(c, "active", MethodType.methodType(boolean.class))
                    .invokeExact();
            return active ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static MethodHandle state(Class<?> c, String name, MethodType type) {
        if (c == null) {
            return null;
        }
        try {
            return MethodHandles.publicLookup().findStatic(c, name, type);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int cached(MethodHandle h) {
        if (h == null) {
            return -1;
        }
        try {
            return (int) h.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Checks that the current context is an OpenGL 4.1 or later core profile.
     *
     * @throws IllegalStateException with instructions when it is not
     */
    public static void requireCoreContext() {
        GLCapabilities caps;
        try {
            caps = GL.getCapabilities();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Helios needs a current OpenGL context", e);
        }
        boolean core = caps.OpenGL32 && (GL11C.glGetInteger(CONTEXT_PROFILE_MASK) & CONTEXT_CORE_PROFILE_BIT) != 0;
        if (!core || !caps.OpenGL41) {
            String version = GL11C.glGetString(GL11C.GL_VERSION);
            throw new IllegalStateException("Helios requires an OpenGL 4.1 core profile context (got " + version
                    + "). Start the game through Lazarus with -Dlazarus.gl=core.");
        }
    }

    private static MethodHandle emulatorMatrixQuery() {
        try {
            Class<?> emulator = Class.forName(EMULATOR, true, ClassLoader.getSystemClassLoader());
            return MethodHandles.publicLookup()
                    .findStatic(
                            emulator, "glGetFloat", MethodType.methodType(void.class, int.class, FloatBuffer.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Lazarus core mode is not running; start the game with -Dlazarus.gl=core", e);
        }
    }

    @Override
    public int getInteger(int pname) {
        int known =
                switch (pname) {
                    case CURRENT_PROGRAM -> cached(stateProgram);
                    case VERTEX_ARRAY_BINDING -> cached(stateVertexArray);
                    case ARRAY_BUFFER_BINDING -> cached(stateArrayBuffer);
                    default -> -1;
                };
        if (known >= 0) {
            return known;
        }
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void getIntegers(int pname, int[] out) {
        if (pname == VIEWPORT && stateViewport != null) {
            try {
                if ((boolean) stateViewport.invokeExact(out)) {
                    return;
                }
            } catch (Throwable t) {
                // fall through to the driver
            }
        }
        GL11C.glGetIntegerv(pname, out);
    }

    @Override
    public void matrices(float[] modelView, float[] projection) {
        if (stateModelView != null && stateProjection != null) {
            try {
                stateModelView.invokeExact(modelView);
                stateProjection.invokeExact(projection);
                return;
            } catch (Throwable t) {
                // fall back to the emulator's glGetFloat
            }
        }
        read(GL_MODELVIEW_MATRIX, modelView);
        read(GL_PROJECTION_MATRIX, projection);
    }

    private void read(int pname, float[] out) {
        try {
            matrix.clear();
            getMatrix.invokeExact(pname, matrix);
            matrix.get(0, out, 0, 16);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot read the emulated matrix stack", t);
        }
    }

    @Override
    public boolean isEnabled(int cap) {
        return GL11.glIsEnabled(cap);
    }

    @Override
    public void setEnabled(int cap, boolean on) {
        if (on) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GL14C.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void depthMask(boolean write) {
        GL11C.glDepthMask(write);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11C.glViewport(x, y, width, height);
    }

    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }

    @Override
    public int compileShader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        return shader;
    }

    @Override
    public boolean shaderCompiled(int shader) {
        return GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_TRUE;
    }

    @Override
    public String shaderLog(int shader) {
        return GL20C.glGetShaderInfoLog(shader);
    }

    @Override
    public void deleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    @Override
    public int linkProgram(int... shaders) {
        int program = GL20C.glCreateProgram();
        for (int s : shaders) {
            GL20C.glAttachShader(program, s);
        }
        GL20C.glLinkProgram(program);
        for (int s : shaders) {
            GL20C.glDetachShader(program, s);
        }
        return program;
    }

    @Override
    public boolean programLinked(int program) {
        return GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_TRUE;
    }

    @Override
    public String programLog(int program) {
        return GL20C.glGetProgramInfoLog(program);
    }

    @Override
    public void deleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    @Override
    public int uniformLocation(int program, String name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    @Override
    public void uniform1i(int location, int v) {
        GL20C.glUniform1i(location, v);
    }

    @Override
    public void uniform1f(int location, float v) {
        GL20C.glUniform1f(location, v);
    }

    @Override
    public void uniform2f(int location, float x, float y) {
        GL20C.glUniform2f(location, x, y);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GL20C.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void uniformMatrix4(int location, float[] m) {
        GL20C.glUniformMatrix4fv(location, false, m);
    }

    @Override
    public int[] createFramebuffer(int width, int height, boolean depthStencil) {
        int previousFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        int previousTex = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int previousRbo = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);

        int tex = GL11C.glGenTextures();
        GL11.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(
                GL11C.GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer)
                        null);

        int fbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, tex, 0);
        int rbo = 0;
        if (depthStencil) {
            rbo = GL30C.glGenRenderbuffers();
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, rbo);
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
            GL30C.glFramebufferRenderbuffer(
                    GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL30C.GL_RENDERBUFFER, rbo);
        }
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);

        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFbo);
        GL11.glBindTexture(GL11C.GL_TEXTURE_2D, previousTex);
        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, previousRbo);

        int[] ids = {fbo, tex, rbo};
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            deleteFramebuffer(ids);
            throw new IllegalStateException(
                    "framebuffer " + width + "x" + height + " incomplete: 0x" + Integer.toHexString(status));
        }
        return ids;
    }

    @Override
    public void deleteFramebuffer(int[] ids) {
        GL30C.glDeleteFramebuffers(ids[0]);
        GL11.glDeleteTextures(ids[1]);
        if (ids[2] != 0) {
            GL30C.glDeleteRenderbuffers(ids[2]);
        }
    }

    @Override
    public void bindFramebuffer(int framebuffer) {
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
    }

    @Override
    public void clearColorTransparent() {
        GL11C.glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        GL11C.glClearColor(0, 0, 0, 0);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GL11C.glClearColor(clear[0], clear[1], clear[2], clear[3]);
    }

    @Override
    public int createVertexArray() {
        return GL30C.glGenVertexArrays();
    }

    @Override
    public void deleteVertexArray(int vao) {
        GL30C.glDeleteVertexArrays(vao);
    }

    @Override
    public void bindVertexArray(int vao) {
        GL30.glBindVertexArray(vao);
    }

    @Override
    public int createBuffer() {
        return GL15C.glGenBuffers();
    }

    @Override
    public void deleteBuffer(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferStorage(int target, long size, int usage) {
        GL15C.glBufferData(target, size, usage);
    }

    @Override
    public void bufferSubData(int target, long offset, ByteBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferWriteUnsynchronized(int target, long offset, ByteBuffer data) {
        int size = data.remaining();
        ByteBuffer mapped = GL30C.glMapBufferRange(
                target,
                offset,
                size,
                GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_UNSYNCHRONIZED_BIT | GL30C.GL_MAP_INVALIDATE_RANGE_BIT);
        if (mapped == null) {
            GL15C.glBufferSubData(target, offset, data);
            return;
        }
        mapped.put(data.duplicate());
        GL15C.glUnmapBuffer(target);
    }

    @Override
    public void enableVertexAttrib(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        GL11C.glDrawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, long offset, int baseVertex) {
        if (baseVertex == 0) {
            GL11C.glDrawElements(mode, count, type, offset);
        } else {
            GL32C.glDrawElementsBaseVertex(mode, count, type, offset, baseVertex);
        }
    }

    @Override
    public long fence() {
        return GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    @Override
    public boolean waitFence(long sync, long timeoutNanos) {
        int r = GL32C.glClientWaitSync(sync, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos);
        return r == GL32C.GL_ALREADY_SIGNALED || r == GL32C.GL_CONDITION_SATISFIED;
    }

    @Override
    public void deleteFence(long sync) {
        GL32C.glDeleteSync(sync);
    }

    @Override
    public void activeTexture(int unit) {
        GL13.glActiveTexture(unit);
    }

    @Override
    public void bindTexture(int texture) {
        GL11.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
    }

    @Override
    public void copyToBoundTexture(int width, int height) {
        GL11C.glCopyTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    @Override
    public int createRgbTexture(int width, int height, ByteBuffer rgb) {
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int align = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT);
        int tex = GL11C.glGenTextures();
        GL11.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        GL11C.glTexImage2D(
                GL11C.GL_TEXTURE_2D, 0, GL_RGB8, width, height, 0, GL11C.GL_RGB, GL11C.GL_UNSIGNED_BYTE, rgb);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, align);
        GL11.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        return tex;
    }

    @Override
    public void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }

    @Override
    public int stencilBits() {
        boolean defaultFb = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING) == 0;
        int attachment = defaultFb ? GL11C.GL_STENCIL : GL30C.GL_STENCIL_ATTACHMENT;
        int type = GL30C.glGetFramebufferAttachmentParameteri(
                GL30C.GL_DRAW_FRAMEBUFFER, attachment, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL11C.GL_NONE) {
            return 0;
        }
        return GL30C.glGetFramebufferAttachmentParameteri(
                GL30C.GL_DRAW_FRAMEBUFFER, attachment, GL30C.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        GL11C.glStencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int sfail, int dpfail, int dppass) {
        GL11C.glStencilOp(sfail, dpfail, dppass);
    }

    @Override
    public void stencilMask(int mask) {
        GL11C.glStencilMask(mask);
    }

    @Override
    public void clearStencil(int value) {
        int previous = GL11C.glGetInteger(GL_STENCIL_CLEAR_VALUE);
        GL11C.glClearStencil(value);
        GL11C.glClear(GL11C.GL_STENCIL_BUFFER_BIT);
        GL11C.glClearStencil(previous);
    }

    @Override
    public void colorMask(boolean r, boolean g, boolean b, boolean a) {
        GL11C.glColorMask(r, g, b, a);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        GL11C.glScissor(x, y, width, height);
    }
}
