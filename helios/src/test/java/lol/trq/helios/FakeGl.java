/* SPDX-License-Identifier: MIT */
package lol.trq.helios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.state.GlStateCache;

/** A GL backend and state cache that keeps state in fields and records every call. */
public final class FakeGl implements GlBackend, GlStateCache {
    public final List<String> calls = new ArrayList<>();
    public final Set<Integer> enabled = new HashSet<>();
    public final Map<Integer, Integer> ints = new HashMap<>();
    public final int[] blend = {ONE, 0, ONE, 0};
    public final int[] viewport = {0, 0, 854, 480};
    public final Map<Integer, String> sources = new HashMap<>();
    public final Set<Integer> liveShaders = new HashSet<>();
    public final Set<Integer> livePrograms = new HashSet<>();
    public final Set<Integer> liveFramebuffers = new HashSet<>();
    public Predicate<String> compiles = s -> true;
    public String compileLog = "";
    public boolean links = true;
    public int uniformLookups;
    public final Map<Integer, Float> floats = new HashMap<>();
    private int nextId = 1;

    @Override
    public int getInteger(int pname) {
        return switch (pname) {
            case BLEND_SRC_RGB -> blend[0];
            case BLEND_DST_RGB -> blend[1];
            case BLEND_SRC_ALPHA -> blend[2];
            case BLEND_DST_ALPHA -> blend[3];
            case ACTIVE_TEXTURE -> activeUnit;
            case STENCIL_FUNC -> stencilState[0];
            case STENCIL_REF -> stencilState[1];
            case STENCIL_VALUE_MASK -> stencilState[2];
            case STENCIL_FAIL -> stencilState[3];
            case STENCIL_PASS_DEPTH_FAIL -> stencilState[4];
            case STENCIL_PASS_DEPTH_PASS -> stencilState[5];
            case STENCIL_WRITEMASK -> stencilState[6];
            case TEXTURE_BINDING_2D -> textureByUnit.getOrDefault(activeUnit, 0);
            default -> ints.getOrDefault(pname, 0);
        };
    }

    @Override
    public void getIntegers(int pname, int[] out) {
        if (pname == VIEWPORT) {
            System.arraycopy(viewport, 0, out, 0, 4);
        } else if (pname == SCISSOR_BOX) {
            System.arraycopy(scissorBox, 0, out, 0, 4);
        } else if (pname == COLOR_WRITEMASK) {
            for (int i = 0; i < 4; i++) {
                out[i] = colorWrite[i] ? 1 : 0;
            }
        }
    }

    @Override
    public boolean isEnabled(int cap) {
        return enabled.contains(cap);
    }

    @Override
    public void setEnabled(int cap, boolean on) {
        calls.add((on ? "enable " : "disable ") + cap);
        if (on) {
            enabled.add(cap);
        } else {
            enabled.remove(cap);
        }
    }

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        calls.add("blendFunc");
        blend[0] = srcRgb;
        blend[1] = dstRgb;
        blend[2] = srcAlpha;
        blend[3] = dstAlpha;
    }

    @Override
    public void depthMask(boolean write) {
        calls.add("depthMask " + write);
        ints.put(DEPTH_WRITEMASK, write ? 1 : 0);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        calls.add("viewport");
        viewport[0] = x;
        viewport[1] = y;
        viewport[2] = width;
        viewport[3] = height;
    }

    @Override
    public void useProgram(int program) {
        calls.add("useProgram " + program);
        ints.put(CURRENT_PROGRAM, program);
    }

    @Override
    public int compileShader(int type, String source) {
        int id = nextId++;
        sources.put(id, source);
        liveShaders.add(id);
        return id;
    }

    @Override
    public boolean shaderCompiled(int shader) {
        return compiles.test(sources.get(shader));
    }

    @Override
    public String shaderLog(int shader) {
        return compileLog;
    }

    @Override
    public void deleteShader(int shader) {
        liveShaders.remove(shader);
    }

    @Override
    public int linkProgram(int... shaders) {
        int id = nextId++;
        livePrograms.add(id);
        return id;
    }

    @Override
    public boolean programLinked(int program) {
        return links;
    }

    @Override
    public String programLog(int program) {
        return "link error";
    }

    @Override
    public void deleteProgram(int program) {
        livePrograms.remove(program);
    }

    @Override
    public int uniformLocation(int program, String name) {
        uniformLookups++;
        return name.startsWith("missing") ? -1 : name.length();
    }

    @Override
    public void uniform1i(int location, int v) {
        calls.add("uniform1i " + location);
    }

    @Override
    public void uniform1f(int location, float v) {
        calls.add("uniform1f " + location);
        floats.put(location, v);
    }

    @Override
    public void uniform2f(int location, float x, float y) {
        calls.add("uniform2f " + location);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        calls.add("uniform4f " + location);
    }

    @Override
    public int[] createFramebuffer(int width, int height, boolean depthStencil) {
        int fbo = nextId++;
        liveFramebuffers.add(fbo);
        return new int[] {fbo, nextId++, depthStencil ? nextId++ : 0};
    }

    @Override
    public void deleteFramebuffer(int[] ids) {
        liveFramebuffers.remove(ids[0]);
    }

    @Override
    public void bindFramebuffer(int framebuffer) {
        calls.add("bindFramebuffer " + framebuffer);
        ints.put(FRAMEBUFFER_BINDING, framebuffer);
    }

    @Override
    public void clearColorTransparent() {
        calls.add("clear");
    }

    @Override
    public void blend(boolean on) {
        setEnabled(BLEND, on);
    }

    @Override
    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void depthTest(boolean on) {
        setEnabled(DEPTH_TEST, on);
    }

    @Override
    public void cull(boolean on) {
        setEnabled(CULL_FACE, on);
    }

    // The host cache: in tests it forwards straight to the fake GL.
    // activeTexture and bindTexture are shared with GlBackend.

    public final List<java.nio.ByteBuffer> streamed = new ArrayList<>();
    public final Set<Integer> attribsEnabled = new HashSet<>();
    public final Map<Integer, Integer> textureByUnit = new HashMap<>();
    public final int[] scissorBox = new int[4];
    public int activeUnit = TEXTURE0;
    public final Set<Integer> liveBuffers = new HashSet<>();

    @Override
    public int createBuffer() {
        int id = nextId++;
        liveBuffers.add(id);
        return id;
    }

    @Override
    public void deleteBuffer(int buffer) {
        liveBuffers.remove(buffer);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        calls.add("attribPointer " + index + " " + size + " " + type + " " + normalized + " " + stride + " " + offset);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        calls.add("drawArrays " + mode + " " + first + " " + count);
    }

    @Override
    public void activeTexture(int unit) {
        calls.add("activeTexture " + (unit - TEXTURE0));
        activeUnit = unit;
        ints.put(ACTIVE_TEXTURE, unit);
    }

    @Override
    public void bindTexture(int texture) {
        calls.add("bindTexture " + texture);
        textureByUnit.put(activeUnit, texture);
    }

    @Override
    public void copyToBoundTexture(int width, int height) {
        calls.add("copy " + width + "x" + height + " -> " + textureByUnit.getOrDefault(activeUnit, 0));
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        calls.add("scissor " + x + "," + y + "," + width + "," + height);
        scissorBox[0] = x;
        scissorBox[1] = y;
        scissorBox[2] = width;
        scissorBox[3] = height;
    }

    public final Set<Integer> liveTextures = new HashSet<>();

    @Override
    public int createRgbTexture(int width, int height, java.nio.ByteBuffer rgb) {
        if (rgb.remaining() != width * height * 3) {
            throw new IllegalArgumentException("texture bytes " + rgb.remaining());
        }
        int id = nextId++;
        liveTextures.add(id);
        calls.add("createTexture " + width + "x" + height);
        return id;
    }

    @Override
    public void deleteTexture(int texture) {
        liveTextures.remove(texture);
    }

    public final int[] stencilState = {ALWAYS, 0, 0xFF, KEEP, KEEP, KEEP, 0xFF};
    public final boolean[] colorWrite = {true, true, true, true};
    public int stencilBits = 8;

    @Override
    public int stencilBits() {
        return stencilBits;
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        calls.add("stencilFunc " + func + " " + ref);
        stencilState[0] = func;
        stencilState[1] = ref;
        stencilState[2] = mask;
    }

    @Override
    public void stencilOp(int sfail, int dpfail, int dppass) {
        calls.add("stencilOp " + dppass);
        stencilState[3] = sfail;
        stencilState[4] = dpfail;
        stencilState[5] = dppass;
    }

    @Override
    public void stencilMask(int mask) {
        calls.add("stencilMask " + mask);
        stencilState[6] = mask;
    }

    @Override
    public void clearStencil(int value) {
        calls.add("clearStencil " + value);
    }

    @Override
    public void colorMask(boolean r, boolean g, boolean b, boolean a) {
        calls.add("colorMask " + r);
        colorWrite[0] = r;
        colorWrite[1] = g;
        colorWrite[2] = b;
        colorWrite[3] = a;
    }

    // Core profile additions.

    public final float[] modelView = lol.trq.helios.math.Mat4.identity(new float[16]);
    public final float[] projection = lol.trq.helios.math.Mat4.identity(new float[16]);
    public final Set<Integer> liveVertexArrays = new HashSet<>();
    public final Set<Long> liveFences = new HashSet<>();
    public final Map<Integer, Integer> bufferSizes = new HashMap<>();
    private long nextFence = 1;

    @Override
    public void matrices(float[] mv, float[] proj) {
        System.arraycopy(modelView, 0, mv, 0, 16);
        System.arraycopy(projection, 0, proj, 0, 16);
    }

    @Override
    public void uniformMatrix4(int location, float[] m) {
        calls.add("uniformMatrix4 " + location);
    }

    @Override
    public int createVertexArray() {
        int id = nextId++;
        liveVertexArrays.add(id);
        return id;
    }

    @Override
    public void deleteVertexArray(int vao) {
        liveVertexArrays.remove(vao);
    }

    @Override
    public void bindVertexArray(int vao) {
        calls.add("bindVertexArray " + vao);
        ints.put(VERTEX_ARRAY_BINDING, vao);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        calls.add((target == ARRAY_BUFFER ? "bindArrayBuffer " : "bindElementBuffer ") + buffer);
        if (target == ARRAY_BUFFER) {
            ints.put(ARRAY_BUFFER_BINDING, buffer);
        }
    }

    @Override
    public void bufferData(int target, java.nio.ByteBuffer data, int usage) {
        calls.add("bufferData " + data.remaining());
    }

    @Override
    public void bufferStorage(int target, long size, int usage) {
        calls.add("bufferStorage " + size);
    }

    @Override
    public void bufferSubData(int target, long offset, java.nio.ByteBuffer data) {
        java.nio.ByteBuffer copy =
                java.nio.ByteBuffer.allocate(data.remaining()).order(data.order());
        copy.put(data.duplicate());
        copy.flip();
        streamed.add(copy);
        calls.add("stream " + copy.remaining() + " @" + offset);
    }

    @Override
    public void bufferWriteUnsynchronized(int target, long offset, java.nio.ByteBuffer data) {
        bufferSubData(target, offset, data);
    }

    @Override
    public void enableVertexAttrib(int index) {
        calls.add("enableAttrib " + index);
        attribsEnabled.add(index);
    }

    @Override
    public void drawElements(int mode, int count, int type, long offset, int baseVertex) {
        calls.add("drawElements " + mode + " " + count + " " + type + " base " + baseVertex);
    }

    @Override
    public long fence() {
        long f = nextFence++;
        liveFences.add(f);
        calls.add("fence " + f);
        return f;
    }

    @Override
    public boolean waitFence(long sync, long timeoutNanos) {
        calls.add("waitFence " + sync);
        return true;
    }

    @Override
    public void deleteFence(long sync) {
        liveFences.remove(sync);
    }
}
