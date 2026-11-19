/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import java.lang.reflect.Field;

/**
 * OptiFine, when it is installed, by reflection: whether it is present, its version, and its Fast Render option.
 * OptiFine is never a dependency; every method falls back to a safe answer without it. Helios's own drawing works with
 * Fast Render on or off, so nothing needs to turn it off; the switch is here for callers whose own drawing does.
 */
public final class MinecraftOptiFine {
    private final String version;
    private final Object settings;
    private final Field fastRender;

    /**
     * Looks OptiFine up once.
     *
     * @param loader the class loader Minecraft's classes load through
     */
    public MinecraftOptiFine(ClassLoader loader) {
        this.version = version(loader);
        Object s = null;
        Field f = null;
        if (version != null) {
            try {
                Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, loader);
                Object minecraft = method(mc, "func_71410_x", "getMinecraft").invoke(null);
                s = field(mc, "field_71474_y", "gameSettings").get(minecraft);
                f = s.getClass().getDeclaredField("ofFastRender");
                if (f.getType() != boolean.class) {
                    f = null;
                } else {
                    f.setAccessible(true);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                f = null;
            }
        }
        this.settings = s;
        this.fastRender = f;
    }

    /**
     * Returns whether OptiFine is installed.
     *
     * @return true with OptiFine
     */
    public boolean present() {
        return version != null;
    }

    /**
     * Returns OptiFine's version, such as {@code OptiFine_1.8.9_HD_U_M5}.
     *
     * @return the version, or {@code null} without OptiFine
     */
    public String version() {
        return version;
    }

    /**
     * Returns whether Fast Render is on.
     *
     * @return the option, or false without OptiFine
     */
    public boolean fastRender() {
        if (fastRender == null) {
            return false;
        }
        try {
            return fastRender.getBoolean(settings);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    /**
     * Sets Fast Render for this session; OptiFine saves it with its options. Does nothing without OptiFine.
     *
     * @param on whether Fast Render is on
     * @return whether the option exists and was set
     */
    public boolean fastRender(boolean on) {
        if (fastRender == null) {
            return false;
        }
        try {
            fastRender.setBoolean(settings, on);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static String version(ClassLoader loader) {
        try {
            Class<?> config = Class.forName("Config", false, loader);
            return String.valueOf(config.getMethod("getVersion").invoke(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
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
        Field f;
        try {
            f = owner.getDeclaredField(srg);
        } catch (NoSuchFieldException e) {
            f = owner.getDeclaredField(mcp);
        }
        f.setAccessible(true);
        return f;
    }
}
