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
    public final float[] color = {1, 1, 1, 1};
    public final int[] viewport = {0, 0, 854, 480};
    public final Map<Integer, String> sources = new HashMap<>();
    public final Set<Integer> liveShaders = new HashSet<>();
    public final Set<Integer> livePrograms = new HashSet<>();
    public final Set<Integer> liveFramebuffers = new HashSet<>();
    public Predicate<String> compiles = s -> true;
    public String compileLog = "";
    public boolean links = true;
    public int uniformLookups;
    private int nextId = 1;

    @Override
    public int getInteger(int pname) {
        return switch (pname) {
            case BLEND_SRC_RGB -> blend[0];
            case BLEND_DST_RGB -> blend[1];
            case BLEND_SRC_ALPHA -> blend[2];
            case BLEND_DST_ALPHA -> blend[3];
            case ACTIVE_TEXTURE -> activeUnit;
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
        }
    }

    @Override
    public void getFloats(int pname, float[] out) {
        if (pname == CURRENT_COLOR) {
            System.arraycopy(color, 0, out, 0, 4);
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
    public void color(float r, float g, float b, float a) {
        calls.add("color");
        color[0] = r;
        color[1] = g;
        color[2] = b;
        color[3] = a;
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
    public void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        calls.add("quad " + x0 + "," + y0 + "," + x1 + "," + y1 + " uv " + u0 + "," + v0 + "," + u1 + "," + v1);
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

    @Override
    public void alphaTest(boolean on) {
        setEnabled(ALPHA_TEST, on);
    }

    @Override
    public void texture2D(boolean on) {
        setEnabled(TEXTURE_2D, on);
    }

    // The host cache: in tests it forwards straight to the fake GL.
    // activeTexture and bindTexture are shared with GlBackend.

    public final List<String[]> linkedAttributes = new ArrayList<>();
    public final List<java.nio.ByteBuffer> streamed = new ArrayList<>();
    public final Set<Integer> attribsEnabled = new HashSet<>();
    public final Map<Integer, Integer> textureByUnit = new HashMap<>();
    public final int[] scissorBox = new int[4];
    public int activeUnit = TEXTURE0;
    public final Set<Integer> liveBuffers = new HashSet<>();

    @Override
    public int linkProgram(String[] attributes, int... shaders) {
        linkedAttributes.add(attributes.clone());
        return linkProgram(shaders);
    }

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
    public void bindArrayBuffer(int buffer) {
        calls.add("bindArrayBuffer " + buffer);
        ints.put(ARRAY_BUFFER_BINDING, buffer);
    }

    @Override
    public void streamArrayBuffer(java.nio.ByteBuffer data) {
        java.nio.ByteBuffer copy =
                java.nio.ByteBuffer.allocate(data.remaining()).order(data.order());
        copy.put(data.duplicate());
        copy.flip();
        streamed.add(copy);
        calls.add("stream " + copy.remaining());
    }

    @Override
    public boolean vertexAttribEnabled(int index) {
        return attribsEnabled.contains(index);
    }

    @Override
    public void vertexAttribArray(int index, boolean on) {
        calls.add((on ? "enableAttrib " : "disableAttrib ") + index);
        if (on) {
            attribsEnabled.add(index);
        } else {
            attribsEnabled.remove(index);
        }
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
}
