/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import lol.trq.helios.state.GlStateCache;

/**
 * Routes cached state changes through Minecraft 1.8.9's {@code GlStateManager}, so its cache, and OptiFine's
 * additions to it, stay true.
 *
 * <p>Methods are found by their SRG names (what Forge uses at run time) and, failing that, by their MCP names (a
 * deobfuscated development workspace).
 */
public final class MinecraftGlStateCache implements GlStateCache {
    private static final String GSM = "net.minecraft.client.renderer.GlStateManager";

    private final MethodHandle enableBlend;
    private final MethodHandle disableBlend;
    private final MethodHandle blendFuncSeparate;
    private final MethodHandle enableDepth;
    private final MethodHandle disableDepth;
    private final MethodHandle depthMask;
    private final MethodHandle enableCull;
    private final MethodHandle disableCull;
    private final MethodHandle enableAlpha;
    private final MethodHandle disableAlpha;
    private final MethodHandle enableTexture2D;
    private final MethodHandle disableTexture2D;
    private final MethodHandle color;
    private final MethodHandle setActiveTexture;
    private final MethodHandle bindTexture;

    /**
     * Resolves {@code GlStateManager}.
     *
     * @param loader the class loader Minecraft's classes load through
     * @throws IllegalStateException if the class or one of its methods cannot be found
     */
    public MinecraftGlStateCache(ClassLoader loader) {
        Class<?> gsm;
        try {
            gsm = Class.forName(GSM, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("GlStateManager not found", e);
        }
        MethodType v = MethodType.methodType(void.class);
        enableBlend = find(gsm, "func_179147_l", "enableBlend", v);
        disableBlend = find(gsm, "func_179084_k", "disableBlend", v);
        blendFuncSeparate = find(
                gsm,
                "func_179120_a",
                "tryBlendFuncSeparate",
                MethodType.methodType(void.class, int.class, int.class, int.class, int.class));
        enableDepth = find(gsm, "func_179126_j", "enableDepth", v);
        disableDepth = find(gsm, "func_179097_i", "disableDepth", v);
        depthMask = find(gsm, "func_179132_a", "depthMask", MethodType.methodType(void.class, boolean.class));
        enableCull = find(gsm, "func_179089_o", "enableCull", v);
        disableCull = find(gsm, "func_179129_p", "disableCull", v);
        enableAlpha = find(gsm, "func_179141_d", "enableAlpha", v);
        disableAlpha = find(gsm, "func_179118_c", "disableAlpha", v);
        enableTexture2D = find(gsm, "func_179098_w", "enableTexture2D", v);
        disableTexture2D = find(gsm, "func_179090_x", "disableTexture2D", v);
        color = find(
                gsm,
                "func_179131_c",
                "color",
                MethodType.methodType(void.class, float.class, float.class, float.class, float.class));
        MethodType i = MethodType.methodType(void.class, int.class);
        setActiveTexture = find(gsm, "func_179138_g", "setActiveTexture", i);
        bindTexture = find(gsm, "func_179144_i", "bindTexture", i);
    }

    private static MethodHandle find(Class<?> owner, String srg, String mcp, MethodType type) {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            return lookup.findStatic(owner, srg, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            try {
                return lookup.findStatic(owner, mcp, type);
            } catch (NoSuchMethodException | IllegalAccessException e2) {
                throw new IllegalStateException("GlStateManager." + mcp + " (" + srg + ") not found", e2);
            }
        }
    }

    @Override
    public void blend(boolean on) {
        run(on ? enableBlend : disableBlend);
    }

    @Override
    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        try {
            blendFuncSeparate.invokeExact(srcRgb, dstRgb, srcAlpha, dstAlpha);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void depthTest(boolean on) {
        run(on ? enableDepth : disableDepth);
    }

    @Override
    public void depthMask(boolean write) {
        try {
            depthMask.invokeExact(write);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void cull(boolean on) {
        run(on ? enableCull : disableCull);
    }

    @Override
    public void alphaTest(boolean on) {
        run(on ? enableAlpha : disableAlpha);
    }

    @Override
    public void texture2D(boolean on) {
        run(on ? enableTexture2D : disableTexture2D);
    }

    @Override
    public void color(float r, float g, float b, float a) {
        try {
            color.invokeExact(r, g, b, a);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void activeTexture(int unit) {
        try {
            setActiveTexture.invokeExact(unit);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void bindTexture(int texture) {
        try {
            bindTexture.invokeExact(texture);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void run(MethodHandle h) {
        try {
            h.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException r) {
            throw r;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }
}
