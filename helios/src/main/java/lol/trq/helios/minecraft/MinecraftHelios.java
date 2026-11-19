/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import lol.trq.helios.Helios;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.Gl21Backend;

/**
 * The Minecraft 1.8.9 hook: every reference to Minecraft's own classes lives in this package, reached by reflection,
 * so the rest of Helios compiles and tests without the game.
 */
public final class MinecraftHelios {
    private MinecraftHelios() {}

    /**
     * Creates a Helios instance for the running game: the OpenGL 2.1 backend, the {@code GlStateManager} cache and
     * the window's framebuffer size. Call it on the render thread once the window exists.
     *
     * @return the instance
     */
    public static Helios create() {
        ClassLoader loader = MinecraftHelios.class.getClassLoader();
        Gl21Backend gl = new Gl21Backend();
        MinecraftGlStateCache cache = new MinecraftGlStateCache(loader);
        return Helios.create(gl, cache, surface(loader));
    }

    /**
     * Gives the main Minecraft framebuffer a stencil buffer, through Forge's {@code Framebuffer.enableStencil()}.
     * Call it once, on the render thread, before using {@link Helios#stencil()} on screen. The framebuffer is
     * recreated, so what was drawn so far this frame is lost.
     *
     * @return whether the framebuffer now has a stencil buffer
     */
    public static boolean enableStencil() {
        try {
            Class<?> mc =
                    Class.forName("net.minecraft.client.Minecraft", false, MinecraftHelios.class.getClassLoader());
            Object instance = method(mc, "func_71410_x", "getMinecraft").invoke(null);
            Object framebuffer = method(mc, "func_147110_a", "getFramebuffer").invoke(instance);
            boolean enabled =
                    (Boolean) framebuffer.getClass().getMethod("enableStencil").invoke(framebuffer);
            // enableStencil recreates the framebuffer and leaves nothing bound; bind it again so a call in the middle
            // of a frame keeps drawing into it.
            Method bind;
            try {
                bind = framebuffer.getClass().getMethod("func_147610_a", boolean.class);
            } catch (NoSuchMethodException e) {
                bind = framebuffer.getClass().getMethod("bindFramebuffer", boolean.class);
            }
            bind.invoke(framebuffer, true);
            return enabled;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot enable the stencil buffer", e);
        }
    }

    /**
     * Returns the game window's framebuffer size, read from {@code Minecraft.displayWidth} and
     * {@code displayHeight}, which are framebuffer pixels (on LWJGL 3 through Lazarus, also on high-DPI screens).
     *
     * @param loader the class loader Minecraft's classes load through
     * @return the size source
     */
    public static SurfaceSize surface(ClassLoader loader) {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, loader);
            Object instance = method(mc, "func_71410_x", "getMinecraft").invoke(null);
            Field width = field(mc, "field_71443_c", "displayWidth");
            Field height = field(mc, "field_71440_d", "displayHeight");
            return new SurfaceSize() {
                @Override
                public int width() {
                    return read(width, instance);
                }

                @Override
                public int height() {
                    return read(height, instance);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the Minecraft window size", e);
        }
    }

    private static int read(Field f, Object owner) {
        try {
            return f.getInt(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.lang.reflect.Method method(Class<?> owner, String srg, String mcp)
            throws NoSuchMethodException {
        try {
            return owner.getMethod(srg);
        } catch (NoSuchMethodException e) {
            return owner.getMethod(mcp);
        }
    }

    private static Field field(Class<?> owner, String srg, String mcp) throws NoSuchFieldException {
        try {
            return owner.getField(srg);
        } catch (NoSuchFieldException e) {
            return owner.getField(mcp);
        }
    }
}
