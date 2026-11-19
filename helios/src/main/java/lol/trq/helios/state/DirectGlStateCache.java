/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

import lol.trq.helios.gl.GlBackend;

/** A {@link GlStateCache} for hosts that cache nothing: every call goes straight to the backend. */
public final class DirectGlStateCache implements GlStateCache {
    private final GlBackend gl;

    /**
     * Creates the cache.
     *
     * @param gl the backend
     */
    public DirectGlStateCache(GlBackend gl) {
        this.gl = gl;
    }

    @Override
    public void blend(boolean on) {
        gl.setEnabled(GlBackend.BLEND, on);
    }

    @Override
    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        gl.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void depthTest(boolean on) {
        gl.setEnabled(GlBackend.DEPTH_TEST, on);
    }

    @Override
    public void depthMask(boolean write) {
        gl.depthMask(write);
    }

    @Override
    public void cull(boolean on) {
        gl.setEnabled(GlBackend.CULL_FACE, on);
    }

    @Override
    public void alphaTest(boolean on) {
        gl.setEnabled(GlBackend.ALPHA_TEST, on);
    }

    @Override
    public void texture2D(boolean on) {
        gl.setEnabled(GlBackend.TEXTURE_2D, on);
    }

    @Override
    public void color(float r, float g, float b, float a) {
        gl.color(r, g, b, a);
    }
}
