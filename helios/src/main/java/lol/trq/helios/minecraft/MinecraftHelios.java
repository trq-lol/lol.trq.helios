/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import lol.trq.helios.Helios;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.Gl41CoreBackend;

/**
 * The Minecraft 1.8.9 hook: every reference to Minecraft's own classes lives in this package, reached by reflection,
 * so the rest of Helios compiles and tests without the game.
 */
public final class MinecraftHelios {
    private MinecraftHelios() {}

    /**
     * Creates a Helios instance for the running game: the OpenGL 4.1 core backend (which checks the context), the {@code GlStateManager} cache and
     * the window's framebuffer size. Call it on the render thread once the window exists.
     *
     * @return the instance
     */
    public static Helios create() {
        ClassLoader loader = MinecraftHelios.class.getClassLoader();
        Gl41CoreBackend gl = new Gl41CoreBackend();
        MinecraftGlStateCache cache = new MinecraftGlStateCache(loader);
        String dir = System.getProperty(SHADER_DIR_PROPERTY, "");
        if (!dir.isEmpty()) {
            return Helios.createWithHotReload(gl, cache, surface(loader), java.nio.file.Path.of(dir));
        }
        return Helios.create(gl, cache, surface(loader));
    }

    /**
     * System property naming a shader source directory; when set, {@link #create()} turns on hot reload from it.
     */
    public static final String SHADER_DIR_PROPERTY = "helios.shaderDir";

    /**
     * Gives the main Minecraft framebuffer a stencil buffer, through Forge's {@code Framebuffer.enableStencil()},
     * on the render thread. The first call recreates the framebuffer, so what was drawn so far that frame is lost,
     * and binds it again. Once the stencil buffer exists, a call returns true and changes nothing, so it is safe to
     * call before every use of {@link Helios#stencil()}.
     *
     * @return whether the framebuffer has a stencil buffer
     */
    public static boolean enableStencil() {
        try {
            Class<?> mc =
                    Class.forName("net.minecraft.client.Minecraft", false, MinecraftHelios.class.getClassLoader());
            Object instance = method(mc, "func_71410_x", "getMinecraft").invoke(null);
            return enableStencil(method(mc, "func_147110_a", "getFramebuffer").invoke(instance));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot enable the stencil buffer", e);
        }
    }

    /**
     * Enables the stencil buffer of a Forge {@code Framebuffer}, leaving the GL bindings alone when it already has
     * one.
     */
    static boolean enableStencil(Object framebuffer) throws ReflectiveOperationException {
        Class<?> type = framebuffer.getClass();
        Method isEnabled = optionalMethod(type, "isStencilEnabled");
        if (isEnabled != null && (Boolean) isEnabled.invoke(framebuffer)) {
            return true;
        }
        boolean enabled = (Boolean) type.getMethod("enableStencil").invoke(framebuffer);
        // enableStencil recreates the framebuffer and leaves nothing bound; bind it again so a call in the middle of
        // a frame keeps drawing into it.
        Method bind = optionalMethod(type, "func_147610_a", boolean.class);
        if (bind == null) {
            bind = type.getMethod("bindFramebuffer", boolean.class);
        }
        bind.invoke(framebuffer, true);
        return enabled;
    }

    private static Method optionalMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
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
