/* SPDX-License-Identifier: MIT */
package lol.trq.helios.bench;

import java.nio.ByteBuffer;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.state.GlStateCache;

/** A backend that does nothing, so benchmarks measure only the CPU work Helios does around GL calls. */
final class NullGl implements GlBackend, GlStateCache {
    private int next = 1;
    long bytesStreamed;
    int draws;

    @Override
    public int getInteger(int pname) {
        return 0;
    }

    @Override
    public void getIntegers(int pname, int[] out) {}

    @Override
    public boolean isEnabled(int cap) {
        return false;
    }

    @Override
    public void setEnabled(int cap, boolean on) {}

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {}

    @Override
    public void depthMask(boolean write) {}

    @Override
    public void viewport(int x, int y, int width, int height) {}

    @Override
    public void useProgram(int program) {}

    @Override
    public int compileShader(int type, String source) {
        return next++;
    }

    @Override
    public boolean shaderCompiled(int shader) {
        return true;
    }

    @Override
    public String shaderLog(int shader) {
        return "";
    }

    @Override
    public void deleteShader(int shader) {}

    @Override
    public int linkProgram(int... shaders) {
        return next++;
    }

    @Override
    public boolean programLinked(int program) {
        return true;
    }

    @Override
    public String programLog(int program) {
        return "";
    }

    @Override
    public void deleteProgram(int program) {}

    @Override
    public int uniformLocation(int program, String name) {
        return name.length();
    }

    @Override
    public void uniform1i(int location, int v) {}

    @Override
    public void uniform1f(int location, float v) {}

    @Override
    public void uniform2f(int location, float x, float y) {}

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {}

    @Override
    public int[] createFramebuffer(int width, int height, boolean depthStencil) {
        return new int[] {next++, next++, depthStencil ? next++ : 0};
    }

    @Override
    public void deleteFramebuffer(int[] ids) {}

    @Override
    public void bindFramebuffer(int framebuffer) {}

    @Override
    public void clearColorTransparent() {}

    @Override
    public int createBuffer() {
        return next++;
    }

    @Override
    public void deleteBuffer(int buffer) {}

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {}

    @Override
    public void drawArrays(int mode, int first, int count) {
        draws++;
    }

    @Override
    public void activeTexture(int unit) {}

    @Override
    public void bindTexture(int texture) {}

    @Override
    public void copyToBoundTexture(int width, int height) {}

    @Override
    public void scissor(int x, int y, int width, int height) {}

    @Override
    public int createRgbTexture(int width, int height, ByteBuffer rgb) {
        return next++;
    }

    @Override
    public void deleteTexture(int texture) {}

    @Override
    public int stencilBits() {
        return 8;
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {}

    @Override
    public void stencilOp(int sfail, int dpfail, int dppass) {}

    @Override
    public void stencilMask(int mask) {}

    @Override
    public void clearStencil(int value) {}

    @Override
    public void colorMask(boolean r, boolean g, boolean b, boolean a) {}

    @Override
    public void blend(boolean on) {}

    @Override
    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {}

    @Override
    public void depthTest(boolean on) {}

    @Override
    public void cull(boolean on) {}

    @Override
    public void matrices(float[] modelView, float[] projection) {}

    @Override
    public void uniformMatrix4(int location, float[] m) {}

    @Override
    public int createVertexArray() {
        return next++;
    }

    @Override
    public void deleteVertexArray(int vao) {}

    @Override
    public void bindVertexArray(int vao) {}

    @Override
    public void bindBuffer(int target, int buffer) {}

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {}

    @Override
    public void bufferStorage(int target, long size, int usage) {}

    @Override
    public void bufferSubData(int target, long offset, ByteBuffer data) {
        bytesStreamed += data.remaining();
    }

    @Override
    public void enableVertexAttrib(int index) {}

    @Override
    public void drawElements(int mode, int count, int type, long offset, int baseVertex) {
        draws++;
    }

    @Override
    public long fence() {
        return next++;
    }

    @Override
    public boolean waitFence(long sync, long timeoutNanos) {
        return true;
    }

    @Override
    public void deleteFence(long sync) {}
}
