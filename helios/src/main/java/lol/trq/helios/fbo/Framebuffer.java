/* SPDX-License-Identifier: MIT */
package lol.trq.helios.fbo;

/**
 * An offscreen framebuffer with an RGBA8 colour texture and an optional depth-stencil buffer, lent out by a
 * {@link FramebufferPool}.
 *
 * @param id the GL framebuffer
 * @param texture the colour attachment texture
 * @param width width in pixels
 * @param height height in pixels
 * @param depthStencil whether a depth-stencil buffer is attached
 * @param ids the raw ids the backend created
 */
public record Framebuffer(int id, int texture, int width, int height, boolean depthStencil, int[] ids) {}
