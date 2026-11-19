/* SPDX-License-Identifier: MIT */
package lol.trq.helios;

import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.fbo.FramebufferPool;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.SourceResolver;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;

/**
 * One Helios instance per GL context: the backend, the host's state cache, shaders, the framebuffer pool and the
 * renderers. Create it and use it on the render thread, after the context exists.
 *
 * <p>On Minecraft, {@link lol.trq.helios.minecraft.MinecraftHelios#create()} wires the backend, the
 * {@code GlStateManager} cache and the window size.
 */
public final class Helios implements AutoCloseable {
    /** The GLSL version every built-in shader targets: GLSL 1.20, the OpenGL 2.1 baseline. */
    public static final int GLSL_VERSION = 120;

    /** Class path root of the built-in shaders. */
    public static final String SHADER_ROOT = "lol/trq/helios/shaders";

    private final GlBackend gl;
    private final GlStateCache cache;
    private final ShaderLoader shaders;
    private final FramebufferPool framebuffers;
    private final ShapeBatch shapes;

    /** Shapes per draw call in the default batch. */
    public static final int BATCH_CAPACITY = 4096;

    private Helios(GlBackend gl, GlStateCache cache, SurfaceSize surface, SourceResolver resolver) {
        this.gl = gl;
        this.cache = cache;
        this.shaders = new ShaderLoader(gl, resolver, GLSL_VERSION);
        this.framebuffers = new FramebufferPool(gl, surface);
        this.shapes = new ShapeBatch(gl, cache, shaders, BATCH_CAPACITY);
    }

    /**
     * Creates an instance with the built-in shaders.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param surface the window framebuffer size
     * @return the instance
     */
    public static Helios create(GlBackend gl, GlStateCache cache, SurfaceSize surface) {
        return create(gl, cache, surface, builtinShaders());
    }

    /**
     * Creates an instance with a custom shader source, for example a source directory first for hot reload.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param surface the window framebuffer size
     * @param resolver where shader sources come from; must provide the built-in ones
     * @return the instance
     */
    public static Helios create(GlBackend gl, GlStateCache cache, SurfaceSize surface, SourceResolver resolver) {
        return new Helios(gl, cache, surface, resolver);
    }

    /**
     * Returns the resolver for the shaders inside the Helios jar.
     *
     * @return the resolver
     */
    public static SourceResolver builtinShaders() {
        return SourceResolver.classpath(Helios.class.getClassLoader(), SHADER_ROOT);
    }

    /**
     * Opens a state guard bound to this instance's backend and cache.
     *
     * @return a new guard; close it when the draw ends
     */
    public StateGuard guard() {
        return StateGuard.begin(gl, cache);
    }

    /**
     * Returns the backend.
     *
     * @return the backend
     */
    public GlBackend gl() {
        return gl;
    }

    /**
     * Returns the shader loader.
     *
     * @return the loader
     */
    public ShaderLoader shaders() {
        return shaders;
    }

    /**
     * Returns the framebuffer pool.
     *
     * @return the pool
     */
    public FramebufferPool framebuffers() {
        return framebuffers;
    }

    /**
     * Returns the shape batch.
     *
     * @return the batch
     */
    public ShapeBatch shapes() {
        return shapes;
    }

    /**
     * Returns the library version from the jar manifest.
     *
     * @return the version, or {@code "dev"} when running from classes outside a jar
     */
    public static String version() {
        String v = Helios.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /** Deletes every GL object this instance owns. */
    @Override
    public void close() {
        shapes.close();
        framebuffers.close();
    }
}
