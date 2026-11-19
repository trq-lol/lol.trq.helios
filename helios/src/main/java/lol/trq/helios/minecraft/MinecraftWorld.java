/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * World-space helpers for overlays such as ESP boxes and nametags: the render partial tick, the camera position, and
 * entity positions and boxes interpolated between ticks, relative to the camera (the space Minecraft's world
 * matrices use). Reached by reflection with SRG names, MCP names as fallback.
 */
public final class MinecraftWorld {
    private final Object minecraft;
    private final Field timer;
    private final Field partialTicks;
    private final MethodHandle renderManager;
    private final Field viewerX;
    private final Field viewerY;
    private final Field viewerZ;
    private final Field lastX;
    private final Field lastY;
    private final Field lastZ;
    private final Field posX;
    private final Field posY;
    private final Field posZ;
    private final Field width;
    private final Field height;

    /**
     * Resolves everything up front.
     *
     * @param loader the class loader Minecraft's classes load through
     * @throws IllegalStateException if a class or member cannot be found
     */
    public MinecraftWorld(ClassLoader loader) {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, loader);
            Class<?> timerClass = Class.forName("net.minecraft.util.Timer", false, loader);
            Class<?> rm = Class.forName("net.minecraft.client.renderer.entity.RenderManager", false, loader);
            Class<?> entity = Class.forName("net.minecraft.entity.Entity", false, loader);
            minecraft = method(mc, "func_71410_x", "getMinecraft").invoke(null);
            timer = field(mc, "field_71428_T", "timer");
            partialTicks = field(timerClass, "field_74281_c", "renderPartialTicks");
            renderManager = MethodHandles.publicLookup()
                    .unreflect(method(mc, "func_175598_ae", "getRenderManager"))
                    .asType(MethodType.methodType(Object.class, Object.class));
            viewerX = field(rm, "field_78730_l", "viewerPosX");
            viewerY = field(rm, "field_78731_m", "viewerPosY");
            viewerZ = field(rm, "field_78728_n", "viewerPosZ");
            lastX = field(entity, "field_70142_S", "lastTickPosX");
            lastY = field(entity, "field_70137_T", "lastTickPosY");
            lastZ = field(entity, "field_70136_U", "lastTickPosZ");
            posX = field(entity, "field_70165_t", "posX");
            posY = field(entity, "field_70163_u", "posY");
            posZ = field(entity, "field_70161_v", "posZ");
            width = field(entity, "field_70130_N", "width");
            height = field(entity, "field_70131_O", "height");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot resolve Minecraft world members", e);
        }
    }

    /**
     * Returns how far the current frame is between the last tick and the next.
     *
     * @return the partial tick, {@code [0, 1)}
     */
    public float partialTicks() {
        try {
            return partialTicks.getFloat(timer.get(minecraft));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the camera position in world coordinates.
     *
     * @param out receives x, y, z
     * @return {@code out}
     */
    public double[] camera(double[] out) {
        try {
            Object rm = (Object) renderManager.invokeExact(minecraft);
            out[0] = viewerX.getDouble(rm);
            out[1] = viewerY.getDouble(rm);
            out[2] = viewerZ.getDouble(rm);
            return out;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Returns an entity's position interpolated to this frame, relative to the camera.
     *
     * @param entity a {@code net.minecraft.entity.Entity}
     * @param partialTicks the partial tick, from {@link #partialTicks()}
     * @param camera the camera position, from {@link #camera(double[])}
     * @param out receives x, y (feet), z
     * @return {@code out}
     */
    public double[] position(Object entity, float partialTicks, double[] camera, double[] out) {
        try {
            out[0] = lerp(lastX.getDouble(entity), posX.getDouble(entity), partialTicks) - camera[0];
            out[1] = lerp(lastY.getDouble(entity), posY.getDouble(entity), partialTicks) - camera[1];
            out[2] = lerp(lastZ.getDouble(entity), posZ.getDouble(entity), partialTicks) - camera[2];
            return out;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns an entity's hitbox interpolated to this frame, relative to the camera.
     *
     * @param entity a {@code net.minecraft.entity.Entity}
     * @param partialTicks the partial tick
     * @param camera the camera position
     * @param out receives minX, minY, minZ, maxX, maxY, maxZ
     * @return {@code out}
     */
    public double[] box(Object entity, float partialTicks, double[] camera, double[] out) {
        position(entity, partialTicks, camera, out);
        try {
            double hw = width.getFloat(entity) / 2.0;
            double h = height.getFloat(entity);
            double x = out[0];
            double y = out[1];
            double z = out[2];
            out[0] = x - hw;
            out[1] = y;
            out[2] = z - hw;
            out[3] = x + hw;
            out[4] = y + h;
            out[5] = z + hw;
            return out;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
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
