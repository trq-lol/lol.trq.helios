/* SPDX-License-Identifier: MIT */
package lol.trq.helios.gl;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;

/**
 * The baseline backend: OpenGL 2.1 plus {@code ARB_framebuffer_object}, through LWJGL 3. Must be used on the thread
 * that owns the GL context.
 */
public final class Gl21Backend implements GlBackend {
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_DEPTH24_STENCIL8 = 0x88F0;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_COLOR_CLEAR_VALUE = 0x0C22;

    private final float[] clear = new float[4];

    /**
     * Creates the backend after checking the current context.
     *
     * @throws IllegalStateException if there is no current context, or it lacks OpenGL 2.1 or
     *     {@code ARB_framebuffer_object}
     */
    public Gl21Backend() {
        GLCapabilities caps = GL.getCapabilities();
        if (!caps.OpenGL21) {
            throw new IllegalStateException("Helios needs OpenGL 2.1");
        }
        if (!caps.GL_ARB_framebuffer_object && !caps.OpenGL30) {
            throw new IllegalStateException("Helios needs ARB_framebuffer_object");
        }
    }

    @Override
    public int getInteger(int pname) {
        return GL11.glGetInteger(pname);
    }

    @Override
    public void getIntegers(int pname, int[] out) {
        GL11.glGetIntegerv(pname, out);
    }

    @Override
    public void getFloats(int pname, float[] out) {
        GL11.glGetFloatv(pname, out);
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
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void depthMask(boolean write) {
        GL11.glDepthMask(write);
    }

    @Override
    public void color(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }

    @Override
    public int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        return shader;
    }

    @Override
    public boolean shaderCompiled(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE;
    }

    @Override
    public String shaderLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }

    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    @Override
    public int linkProgram(int... shaders) {
        int program = GL20.glCreateProgram();
        for (int s : shaders) {
            GL20.glAttachShader(program, s);
        }
        GL20.glLinkProgram(program);
        for (int s : shaders) {
            GL20.glDetachShader(program, s);
        }
        return program;
    }

    @Override
    public boolean programLinked(int program) {
        return GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE;
    }

    @Override
    public String programLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }

    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    @Override
    public int uniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public void uniform1i(int location, int v) {
        GL20.glUniform1i(location, v);
    }

    @Override
    public void uniform1f(int location, float v) {
        GL20.glUniform1f(location, v);
    }

    @Override
    public void uniform2f(int location, float x, float y) {
        GL20.glUniform2f(location, x, y);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GL20.glUniform4f(location, x, y, z, w);
    }

    @Override
    public int[] createFramebuffer(int width, int height, boolean depthStencil) {
        int previousFbo = GL11.glGetInteger(ARBFramebufferObject.GL_FRAMEBUFFER_BINDING);
        int previousTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousRbo = GL11.glGetInteger(ARBFramebufferObject.GL_RENDERBUFFER_BINDING);

        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)
                        null);

        int fbo = ARBFramebufferObject.glGenFramebuffers();
        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, fbo);
        ARBFramebufferObject.glFramebufferTexture2D(
                ARBFramebufferObject.GL_FRAMEBUFFER,
                ARBFramebufferObject.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                tex,
                0);
        int rbo = 0;
        if (depthStencil) {
            rbo = ARBFramebufferObject.glGenRenderbuffers();
            ARBFramebufferObject.glBindRenderbuffer(ARBFramebufferObject.GL_RENDERBUFFER, rbo);
            ARBFramebufferObject.glRenderbufferStorage(
                    ARBFramebufferObject.GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
            ARBFramebufferObject.glFramebufferRenderbuffer(
                    ARBFramebufferObject.GL_FRAMEBUFFER,
                    ARBFramebufferObject.GL_DEPTH_STENCIL_ATTACHMENT,
                    ARBFramebufferObject.GL_RENDERBUFFER,
                    rbo);
        }
        int status = ARBFramebufferObject.glCheckFramebufferStatus(ARBFramebufferObject.GL_FRAMEBUFFER);

        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, previousFbo);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTex);
        ARBFramebufferObject.glBindRenderbuffer(ARBFramebufferObject.GL_RENDERBUFFER, previousRbo);

        int[] ids = {fbo, tex, rbo};
        if (status != ARBFramebufferObject.GL_FRAMEBUFFER_COMPLETE) {
            deleteFramebuffer(ids);
            throw new IllegalStateException(
                    "framebuffer " + width + "x" + height + " incomplete: 0x" + Integer.toHexString(status));
        }
        return ids;
    }

    @Override
    public void deleteFramebuffer(int[] ids) {
        ARBFramebufferObject.glDeleteFramebuffers(ids[0]);
        GL11.glDeleteTextures(ids[1]);
        if (ids[2] != 0) {
            ARBFramebufferObject.glDeleteRenderbuffers(ids[2]);
        }
    }

    @Override
    public void bindFramebuffer(int framebuffer) {
        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, framebuffer);
    }

    @Override
    public void clearColorTransparent() {
        GL11.glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glClearColor(clear[0], clear[1], clear[2], clear[3]);
    }

    @Override
    public void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x0, y0);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x0, y1);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x1, y0);
        GL11.glEnd();
    }

    /**
     * Returns whether the current context is enough for this backend.
     *
     * @return true when OpenGL 2.1 and framebuffer objects are available
     */
    public static boolean supported() {
        try {
            GLCapabilities caps = GL.getCapabilities();
            return caps.OpenGL21 && (caps.GL_ARB_framebuffer_object || caps.OpenGL30);
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
