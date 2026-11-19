/* SPDX-License-Identifier: MIT */
package lol.trq.helios;

import lol.trq.helios.batch.ShapeBatch;
import lol.trq.helios.effect.Effects;
import lol.trq.helios.effect.Passes;
import lol.trq.helios.effect.PostChain;
import lol.trq.helios.fbo.FramebufferPool;
import lol.trq.helios.fbo.SurfaceSize;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.shader.ShaderHotReload;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.SourceResolver;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.ScissorStack;
import lol.trq.helios.state.StateGuard;
import lol.trq.helios.state.StencilMask;
import lol.trq.helios.text.MsdfFont;
import lol.trq.helios.text.TextRenderer;

/**
 * One Helios instance per GL context: the backend, the host's state cache, shaders, the framebuffer pool and the
 * renderers. Create it and use it on the render thread, after the context exists.
 *
 * <p>On Minecraft, {@link lol.trq.helios.minecraft.MinecraftHelios#create()} wires the backend, the
 * {@code GlStateManager} cache and the window size.
 */
public final class Helios implements AutoCloseable {
    /** The one GLSL version every shader uses: GLSL 4.10 core, for the OpenGL 4.1 core profile. */
    public static final String GLSL_VERSION = lol.trq.helios.shader.ShaderPreprocessor.VERSION_LINE;

    /** Class path root of the built-in shaders. */
    public static final String SHADER_ROOT = "lol/trq/helios/shaders";

    private final GlBackend gl;
    private final GlStateCache cache;
    private final ShaderLoader shaders;
    private final FramebufferPool framebuffers;
    private final ShapeBatch shapes;
    private final ShapeBatch effectShapes;
    private final Passes passes;
    private final Effects effects;
    private final ScissorStack scissor;

    private final TextRenderer text;
    private final StencilMask stencil;
    private ShaderHotReload hotReload;
    private MsdfFont defaultFont;

    /** Class path location of the bundled font, Inter Regular (SIL Open Font License 1.1). */
    public static final String DEFAULT_FONT = "lol/trq/helios/fonts/inter.hfnt";

    /** Shapes per draw call in the default batch. */
    public static final int BATCH_CAPACITY = 4096;

    private Helios(GlBackend gl, GlStateCache cache, SurfaceSize surface, SourceResolver resolver) {
        this.gl = gl;
        this.cache = cache;
        this.shaders = new ShaderLoader(gl, resolver);
        this.framebuffers = new FramebufferPool(gl, surface);
        this.shapes = new ShapeBatch(gl, cache, shaders, BATCH_CAPACITY);
        this.passes = new Passes(gl, cache, framebuffers, shaders);
        this.effectShapes = new ShapeBatch(gl, cache, shaders, 1);
        this.effects = new Effects(gl, cache, passes, effectShapes);
        this.scissor = new ScissorStack(gl, cache, surface);
        this.text = new TextRenderer(gl);
        this.stencil = new StencilMask(gl, cache, shapes);
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
     * Creates an instance that loads shaders from a source directory first and reloads them when they change. Call
     * {@link #tick()} once per frame. For development only.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param surface the window framebuffer size
     * @param shaderDir a copy of the shader sources, laid out like {@link #SHADER_ROOT}
     * @return the instance
     */
    public static Helios createWithHotReload(
            GlBackend gl, GlStateCache cache, SurfaceSize surface, java.nio.file.Path shaderDir) {
        Helios h =
                create(gl, cache, surface, SourceResolver.directory(shaderDir).or(builtinShaders()));
        h.hotReload = new ShaderHotReload(h.shaders, shaderDir, 250);
        return h;
    }

    /**
     * Per-frame housekeeping: rotates the streamed vertex buffers and polls shader hot reload when it is on. Call it
     * once per frame, after the frame's Helios drawing.
     *
     * @return what hot reload did, or a result with nothing reloaded
     */
    public ShaderHotReload.Result tick() {
        shapes.endFrame();
        effectShapes.endFrame();
        return hotReload == null ? new ShaderHotReload.Result(false, java.util.List.of()) : hotReload.poll();
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
     * Returns the full-screen passes: screen capture, blur, compositing, custom passes.
     *
     * @return the passes
     */
    public Passes passes() {
        return passes;
    }

    /**
     * Returns the screen effects: blur behind a shape, bloom.
     *
     * @return the effects
     */
    public Effects effects() {
        return effects;
    }

    /**
     * Returns the scissor stack.
     *
     * @return the stack
     */
    public ScissorStack scissor() {
        return scissor;
    }

    /**
     * Returns the stencil clipping helper.
     *
     * @return the helper
     */
    public StencilMask stencil() {
        return stencil;
    }

    /**
     * Returns the text renderer.
     *
     * @return the renderer
     */
    public TextRenderer text() {
        return text;
    }

    /**
     * Returns the bundled font, Inter Regular, loading it on first use.
     *
     * @return the font
     */
    public MsdfFont defaultFont() {
        if (defaultFont == null) {
            defaultFont = MsdfFont.resource(Helios.class.getClassLoader(), DEFAULT_FONT);
        }
        return defaultFont;
    }

    /**
     * Starts a new post-processing chain.
     *
     * @return an empty chain
     */
    public PostChain postChain() {
        return new PostChain(passes);
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
        if (defaultFont != null) {
            defaultFont.close();
        }
        effects.close();
        passes.close();
        framebuffers.close();
    }
}
