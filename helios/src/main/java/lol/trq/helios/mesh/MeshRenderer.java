/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.gl.QuadIndices;
import lol.trq.helios.gl.StreamBuffer;
import lol.trq.helios.math.Mat4;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.ShaderProgram;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;

/**
 * Draws 3D meshes written through the sinks: {@link #quads()}, {@link #lines()} and {@link #textured()}. Vertices are
 * in the current model-view space; inside Minecraft's world render (such as {@code RenderWorldLastEvent}) that is
 * world coordinates relative to the camera, the space {@link lol.trq.helios.minecraft.MinecraftWorld} returns.
 *
 * <p>Each sink streams into its own buffer and flushes when it is full, when another sink is written to (so draws keep
 * the order they were written in), when the texture or lighting changes, and at {@link #flush()}. Call
 * {@link #flush()} when done drawing, and {@link #endFrame()} once per frame ({@link lol.trq.helios.Helios#tick()}
 * does). Each flush saves and restores every piece of GL state it touches.
 *
 * <p>Meshes that do not change can be recorded once with a {@link MeshRecorder} and drawn from a {@link StaticMesh}
 * with {@link #draw(StaticMesh, float[])}, which uploads nothing.
 */
public final class MeshRenderer implements AutoCloseable {
    /** First light direction of Minecraft 1.8.9's entity lighting, in world space, normalized. */
    public static final float[] LIGHT0 = normalize(0.2f, 1.0f, -0.7f);
    /** Second light direction of Minecraft 1.8.9's entity lighting, in world space, normalized. */
    public static final float[] LIGHT1 = normalize(-0.2f, 1.0f, 0.7f);
    /** Diffuse strength of each light. */
    public static final float DIFFUSE = 0.6f;
    /** Ambient light. */
    public static final float AMBIENT = 0.4f;
    /** Default alpha cutoff of textured meshes, as Minecraft's alpha test for entities. */
    public static final float TEXTURED_ALPHA_CUTOFF = 0.1f;

    private static final MeshFormat[] FORMATS = MeshFormat.values();
    private static final float[] IDENTITY = Mat4.identity(new float[16]);

    private final GlBackend gl;
    private final GlStateCache cache;
    private final ShaderProgram[] programs = new ShaderProgram[FORMATS.length];
    private final Stream[] streams = new Stream[FORMATS.length];
    private final float[] modelView = new float[16];
    private final float[] projection = new float[16];
    private final float[] viewProjection = new float[16];
    private final float[] mvp = new float[16];
    private final int[] viewport = new int[4];
    private Stream active;
    private int texture;
    private boolean lighting = true;
    private boolean depthTest = true;
    private boolean depthWrite = true;
    private boolean cull;
    private float texturedCutoff = TEXTURED_ALPHA_CUTOFF;
    private int drawCalls;

    /**
     * Creates a renderer.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param shaders the loader that resolves the built-in shaders
     * @param capacity quads per draw call, per sink
     */
    public MeshRenderer(GlBackend gl, GlStateCache cache, ShaderLoader shaders, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.gl = gl;
        this.cache = cache;
        for (MeshFormat f : FORMATS) {
            programs[f.ordinal()] = shaders.load(f.vertexShader(), f.fragmentShader(), Map.of());
            streams[f.ordinal()] = new Stream(f, capacity);
        }
    }

    /**
     * Returns the sink for flat-coloured quads.
     *
     * @return the sink
     */
    public QuadSink quads() {
        return streams[MeshFormat.COLOR.ordinal()].quads;
    }

    /**
     * Returns the sink for anti-aliased lines.
     *
     * @return the sink
     */
    public LineSink lines() {
        return streams[MeshFormat.LINE.ordinal()].lines;
    }

    /**
     * Returns the sink for textured, optionally lit quads; they use the texture set by {@link #texture(int)}.
     *
     * @return the sink
     */
    public TexturedSink textured() {
        return streams[MeshFormat.TEXTURED.ordinal()].textured;
    }

    /**
     * Sets the texture of the textured quads that follow, flushing those already written with another.
     *
     * @param textureId the GL texture, or 0 for none
     * @return this renderer
     */
    public MeshRenderer texture(int textureId) {
        if (textureId != texture) {
            flush(MeshFormat.TEXTURED);
            texture = textureId;
        }
        return this;
    }

    /**
     * Turns Minecraft 1.8.9 entity lighting on or off for the textured quads that follow: each fragment's colour is
     * multiplied by {@code min(1, AMBIENT + DIFFUSE * (max(0, n·LIGHT0) + max(0, n·LIGHT1)))}. On by default. The
     * light directions are in world space, so normals must be too, as they are inside Minecraft's world render.
     *
     * @param on whether lighting is on
     * @return this renderer
     */
    public MeshRenderer lighting(boolean on) {
        if (on != lighting) {
            flush(MeshFormat.TEXTURED);
            lighting = on;
        }
        return this;
    }

    /**
     * Sets the alpha at or below which textured fragments are discarded, so transparent texels write no depth.
     * Defaults to {@value #TEXTURED_ALPHA_CUTOFF}. Coloured quads and lines discard only fully transparent fragments.
     *
     * @param cutoff the cutoff in {@code [0, 1)}
     * @return this renderer
     */
    public MeshRenderer alphaCutoff(float cutoff) {
        if (cutoff != texturedCutoff) {
            flush(MeshFormat.TEXTURED);
            texturedCutoff = cutoff;
        }
        return this;
    }

    /**
     * Sets depth testing and writing for what follows. Both are on by default; turn writing off for translucent
     * meshes, and testing off to draw through walls.
     *
     * @param test whether fragments are depth tested
     * @param write whether fragments write depth
     * @return this renderer
     */
    public MeshRenderer depth(boolean test, boolean write) {
        if (test != depthTest || write != depthWrite) {
            flush();
            depthTest = test;
            depthWrite = write;
        }
        return this;
    }

    /**
     * Sets back-face culling for what follows. Off by default, so quads show from both sides. With culling on, quads
     * wound counter-clockwise on screen face the viewer.
     *
     * @param on whether back faces are culled
     * @return this renderer
     */
    public MeshRenderer cull(boolean on) {
        if (on != cull) {
            flush();
            cull = on;
        }
        return this;
    }

    /** Draws everything written so far. */
    public void flush() {
        if (active != null) {
            flushStream(active);
        }
    }

    /**
     * Draws a recorded mesh, after flushing what was written so far. Uses the current texture, lighting, depth and
     * cull settings, as the streamed sinks do.
     *
     * @param mesh the mesh
     * @param model a transform applied on the GPU, or {@code null} for none
     */
    public void draw(StaticMesh mesh, float[] model) {
        flush();
        if (mesh.quads == 0) {
            return;
        }
        try (StateGuard g = begin(mesh.format)) {
            g.vertexArray(mesh.vao);
            uniforms(mesh.format, model == null ? IDENTITY : model);
            gl.drawElements(GlBackend.TRIANGLES, mesh.quads * 6, mesh.indexType, 0, 0);
            drawCalls++;
        }
    }

    /**
     * Returns how many draw calls this renderer has issued, for tests and profiling.
     *
     * @return the draw call count
     */
    public int drawCalls() {
        return drawCalls;
    }

    /** Marks the end of a frame for the streamed vertex buffers. {@link lol.trq.helios.Helios#tick()} calls it. */
    public void endFrame() {
        for (Stream s : streams) {
            s.stream.endFrame();
        }
    }

    StaticMesh upload(MeshFormat format, ByteBuffer data, int quads) {
        int indexType = QuadIndices.type(Math.max(quads, 1));
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.vertexArray(0).arrayBuffer(0);
            int vao = gl.createVertexArray();
            gl.bindVertexArray(vao);
            int vbo = gl.createBuffer();
            gl.bindBuffer(GlBackend.ARRAY_BUFFER, vbo);
            gl.bufferData(GlBackend.ARRAY_BUFFER, data, GlBackend.STATIC_DRAW);
            int ibo = gl.createBuffer();
            gl.bindBuffer(GlBackend.ELEMENT_ARRAY_BUFFER, ibo);
            gl.bufferData(GlBackend.ELEMENT_ARRAY_BUFFER, QuadIndices.create(quads, indexType), GlBackend.STATIC_DRAW);
            for (int a = 0; a < format.attributes(); a++) {
                gl.enableVertexAttrib(a);
            }
            format.pointAttributes(gl);
            return new StaticMesh(gl, format, vao, vbo, ibo, quads, indexType);
        }
    }

    private void activate(Stream s) {
        if (active != s) {
            flush();
            active = s;
        }
    }

    private void flush(MeshFormat format) {
        Stream s = streams[format.ordinal()];
        if (s.vertices > 0) {
            flushStream(s);
        }
    }

    private StateGuard begin(MeshFormat format) {
        StateGuard g = StateGuard.begin(gl, cache);
        g.blend(true)
                .blendFunc(
                        GlBackend.SRC_ALPHA,
                        GlBackend.ONE_MINUS_SRC_ALPHA,
                        GlBackend.ONE,
                        GlBackend.ONE_MINUS_SRC_ALPHA)
                .depthTest(depthTest)
                .depthMask(depthWrite)
                .cull(cull)
                .program(programs[format.ordinal()].id());
        if (format == MeshFormat.TEXTURED && texture != 0) {
            g.texture(0, texture);
        }
        return g;
    }

    private void uniforms(MeshFormat format, float[] model) {
        ShaderProgram p = programs[format.ordinal()];
        gl.matrices(modelView, projection);
        Mat4.multiply(projection, modelView, viewProjection);
        Mat4.multiply(viewProjection, model, mvp);
        p.set("u_mvp", mvp);
        switch (format) {
            case COLOR -> {}
            case LINE -> {
                gl.getIntegers(GlBackend.VIEWPORT, viewport);
                p.set("u_viewport", viewport[2], viewport[3]);
            }
            case TEXTURED -> {
                p.set("u_model", model);
                p.set("u_texture", 0);
                p.set("u_alphaCutoff", texturedCutoff);
                p.set("u_lighting", lighting ? 1 : 0);
                p.set("u_light0", LIGHT0[0], LIGHT0[1], LIGHT0[2], DIFFUSE);
                p.set("u_light1", LIGHT1[0], LIGHT1[1], LIGHT1[2], AMBIENT);
            }
        }
    }

    private void flushStream(Stream s) {
        if (s.vertices == 0) {
            return;
        }
        s.data.flip();
        try (StateGuard g = begin(s.format)) {
            g.vertexArray(s.vao).arrayBuffer(0);
            s.ensureVertexArray();
            uniforms(s.format, IDENTITY);
            int stride = s.format.stride();
            long offset = s.stream.upload(s.data, stride);
            if (s.stream.buffer() != s.vaoBuffer) {
                s.vaoBuffer = s.stream.buffer();
                s.format.pointAttributes(gl);
            }
            gl.drawElements(GlBackend.TRIANGLES, s.quadCount() * 6, s.indexType, 0, (int) (offset / stride));
            drawCalls++;
        } finally {
            s.data.clear();
            s.vertices = 0;
        }
    }

    /** Deletes the programs, vertex arrays and buffers. Recorded meshes are closed separately. */
    @Override
    public void close() {
        for (int i = 0; i < FORMATS.length; i++) {
            programs[i].close();
            streams[i].close();
        }
    }

    private static float[] normalize(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / len, y / len, z / len};
    }

    private final class Stream extends VertexStore {
        final int capacity;
        final StreamBuffer stream;
        final int indexType;
        int vao;
        int ibo;
        int vaoBuffer;

        Stream(MeshFormat format, int capacity) {
            super(
                    format,
                    ByteBuffer.allocateDirect(capacity * 4 * format.stride()).order(ByteOrder.nativeOrder()));
            this.capacity = capacity;
            this.stream = new StreamBuffer(gl, (long) Math.max(capacity, 1024) * 4 * format.stride() * 4);
            this.indexType = QuadIndices.type(capacity);
        }

        @Override
        void reserveQuad() {
            activate(this);
            if (quadCount() == capacity) {
                flushStream(this);
            }
        }

        void ensureVertexArray() {
            if (vao == 0) {
                vao = gl.createVertexArray();
                gl.bindVertexArray(vao);
                ibo = gl.createBuffer();
                gl.bindBuffer(GlBackend.ELEMENT_ARRAY_BUFFER, ibo);
                gl.bufferData(
                        GlBackend.ELEMENT_ARRAY_BUFFER, QuadIndices.create(capacity, indexType), GlBackend.STATIC_DRAW);
                for (int a = 0; a < format.attributes(); a++) {
                    gl.enableVertexAttrib(a);
                }
            }
        }

        void close() {
            stream.close();
            if (vao != 0) {
                gl.deleteVertexArray(vao);
                gl.deleteBuffer(ibo);
                vao = 0;
                ibo = 0;
            }
        }
    }
}
