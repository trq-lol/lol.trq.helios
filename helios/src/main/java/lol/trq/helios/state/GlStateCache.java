/* SPDX-License-Identifier: MIT */
package lol.trq.helios.state;

/**
 * The state a host caches on the CPU side, such as Minecraft's {@code GlStateManager}.
 *
 * <p>A host that caches GL state skips a call when its cache already holds the value, so a change made behind its
 * back leaves the cache wrong and a later vanilla or OptiFine call silently does nothing. Every change to these
 * states therefore goes through this interface. {@link DirectGlStateCache} is the implementation for a host with no
 * cache.
 */
public interface GlStateCache {
    /**
     * Enables or disables blending.
     *
     * @param on whether blending is on
     */
    void blend(boolean on);

    /**
     * Sets the separate blend factors.
     *
     * @param srcRgb source RGB factor
     * @param dstRgb destination RGB factor
     * @param srcAlpha source alpha factor
     * @param dstAlpha destination alpha factor
     */
    void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    /**
     * Enables or disables the depth test.
     *
     * @param on whether the depth test is on
     */
    void depthTest(boolean on);

    /**
     * Sets the depth write mask.
     *
     * @param write whether depth writes are on
     */
    void depthMask(boolean write);

    /**
     * Enables or disables face culling.
     *
     * @param on whether culling is on
     */
    void cull(boolean on);

    /**
     * Enables or disables the fixed-function alpha test.
     *
     * @param on whether the alpha test is on
     */
    void alphaTest(boolean on);

    /**
     * Enables or disables fixed-function 2D texturing on the active unit.
     *
     * @param on whether texturing is on
     */
    void texture2D(boolean on);

    /**
     * Sets the current colour.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @param a alpha
     */
    void color(float r, float g, float b, float a);
}
