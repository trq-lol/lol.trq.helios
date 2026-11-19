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
            default -> ints.getOrDefault(pname, 0);
        };
    }

    @Override
    public void getIntegers(int pname, int[] out) {
        if (pname == VIEWPORT) {
            System.arraycopy(viewport, 0, out, 0, 4);
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
}
