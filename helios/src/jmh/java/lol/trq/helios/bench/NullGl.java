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
        return pname == STENCIL_BITS ? 8 : 0;
    }

    @Override
    public void getIntegers(int pname, int[] out) {}

    @Override
    public void getFloats(int pname, float[] out) {}

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
    public void color(float r, float g, float b, float a) {}

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
    public int linkProgram(String[] attributes, int... shaders) {
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
    public void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {}

    @Override
    public int createBuffer() {
        return next++;
    }

    @Override
    public void deleteBuffer(int buffer) {}

    @Override
    public void bindArrayBuffer(int buffer) {}

    @Override
    public void streamArrayBuffer(ByteBuffer data) {
        bytesStreamed += data.remaining();
    }

    @Override
    public boolean vertexAttribEnabled(int index) {
        return false;
    }

    @Override
    public void vertexAttribArray(int index, boolean on) {}

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
    public void alphaFunc(int func, float ref) {}

    @Override
    public void blend(boolean on) {}

    @Override
    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {}

    @Override
    public void depthTest(boolean on) {}

    @Override
    public void cull(boolean on) {}

    @Override
    public void alphaTest(boolean on) {}

    @Override
    public void texture2D(boolean on) {}
}
