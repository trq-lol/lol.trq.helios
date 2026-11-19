/* SPDX-License-Identifier: MIT */
package lol.trq.helios.batch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.gl.QuadIndices;
import lol.trq.helios.gl.StreamBuffer;
import lol.trq.helios.math.Mat4;
import lol.trq.helios.sdf.Sdf;
import lol.trq.helios.shader.ShaderLoader;
import lol.trq.helios.shader.ShaderProgram;
import lol.trq.helios.state.GlStateCache;
import lol.trq.helios.state.StateGuard;

/**
 * Draws many signed distance shapes with one program and as few draw calls as possible.
 *
 * <p>Call {@link #begin(float)}, add shapes, then {@link #end()}. Shapes are drawn in the order added, in the
 * current model-view-projection space (GUI units on a Minecraft screen). A batch flushes when it is full, when an
 * image needs a different texture, and at {@link #end()}. Each flush saves and restores every piece of GL state it
 * touches. Every edge is anti-aliased over one framebuffer pixel; the {@code pixel} passed to {@code begin} is the
 * size of that pixel in drawing units, and quads are grown by it so the outer half of the edge is not clipped.
 *
 * <p>Each shape is one quad of four vertices of {@value #STRIDE} bytes: position, position relative to the shape
 * centre, shape (half size, radius, kind), parameters, texture coordinates and four corner colours. Shapes are drawn
 * as indexed triangles from the batch's own vertex array. Vertex data goes through a {@link StreamBuffer}, so a
 * frame's shapes land in one buffer; call {@link #endFrame()} once per frame.
 */
public final class ShapeBatch implements AutoCloseable {
    /** Bytes per vertex. */
    public static final int STRIDE = 72;

    static final int KIND_RECT = 0;
    static final int KIND_ARC = 1;
    static final int KIND_SHADOW = 2;
    static final int KIND_GLOW = 3;
    static final int KIND_IMAGE = 4;
    static final int KIND_GLYPH = 5;

    /** Attribute names, in the order of their {@code layout(location)} in {@code shape.vsh}. */
    public static final List<String> ATTRIBUTES =
            List.of("a_pos", "a_local", "a_shape", "a_params", "a_uv", "a_color0", "a_color1", "a_color2", "a_color3");

    private final GlBackend gl;
    private final GlStateCache cache;
    private final ShaderProgram program;
    private final int capacity;
    private final ByteBuffer vertices;
    private final int[] colors = new int[4];
    private final StreamBuffer stream;
    private final float[] modelView = new float[16];
    private final float[] projection = new float[16];
    private final float[] mvp = new float[16];
    private final int indexType;
    private int vao;
    private int ibo;
    private int vaoBuffer;
    private int count;
    private int texture;
    private float pixel;
    private boolean drawing;
    private int drawCalls;
    private float alphaCutoff;

    /**
     * Creates a batch.
     *
     * @param gl the backend
     * @param cache the host's state cache
     * @param shaders the loader that resolves the built-in shaders
     * @param capacity shapes per draw call
     */
    public ShapeBatch(GlBackend gl, GlStateCache cache, ShaderLoader shaders, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.gl = gl;
        this.cache = cache;
        this.capacity = capacity;
        this.program = shaders.load("shape.vsh", "shape.fsh", Map.of());
        this.vertices = ByteBuffer.allocateDirect(capacity * 4 * STRIDE).order(ByteOrder.nativeOrder());
        this.stream = new StreamBuffer(gl, (long) Math.max(capacity, 1024) * 4 * STRIDE * 4);
        this.indexType = QuadIndices.type(capacity);
    }

    /**
     * Starts a batch.
     *
     * @param pixel the size of one framebuffer pixel in drawing units, such as {@code 1 / guiScale}
     * @return this batch
     */
    public ShapeBatch begin(float pixel) {
        if (drawing) {
            throw new IllegalStateException("batch already begun");
        }
        if (!(pixel > 0)) {
            throw new IllegalArgumentException("pixel must be positive: " + pixel);
        }
        this.pixel = pixel;
        drawing = true;
        return this;
    }

    /** Draws everything added since {@link #begin(float)}. */
    public void end() {
        requireDrawing();
        flush();
        drawing = false;
    }

    /**
     * Discards fragments whose alpha is at or below a cutoff, in the fragment shader. Stencil masks use it so only the
     * inside of a shape is written. 0 turns it off.
     *
     * @param cutoff the alpha below which fragments are dropped, in {@code [0, 1)}
     */
    public void alphaCutoff(float cutoff) {
        this.alphaCutoff = cutoff;
    }

    /**
     * Returns the pixel size given to {@link #begin(float)}.
     *
     * @return one framebuffer pixel in drawing units
     */
    public float pixel() {
        return pixel;
    }

    /**
     * Returns whether the batch is between {@link #begin(float)} and {@link #end()}.
     *
     * @return true while drawing
     */
    public boolean drawing() {
        return drawing;
    }

    /**
     * One glyph from a multi-channel signed distance field atlas. Text layout lives in
     * {@link lol.trq.helios.text.TextRenderer}; this is the primitive it draws with.
     *
     * @param textureId the atlas texture
     * @param x0 quad left
     * @param y0 quad top
     * @param x1 quad right
     * @param y1 quad bottom
     * @param skewTop horizontal offset of the top edge, for italics
     * @param skewBottom horizontal offset of the bottom edge
     * @param u0 atlas left
     * @param v0 atlas top
     * @param u1 atlas right
     * @param v1 atlas bottom
     * @param argb the colour
     * @param pxRange the atlas distance range in texels
     * @param weight extra thickness in field units (range / 2 texels is 0.5); 0 for regular
     * @param softness edge softness in framebuffer pixels, for soft shadows; 0 for a crisp one-pixel edge
     * @param atlasWidth atlas width in texels
     * @param atlasHeight atlas height in texels
     * @return this batch
     */
    public ShapeBatch glyph(
            int textureId,
            float x0,
            float y0,
            float x1,
            float y1,
            float skewTop,
            float skewBottom,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb,
            float pxRange,
            float weight,
            float softness,
            int atlasWidth,
            int atlasHeight) {
        requireDrawing();
        if (!(x1 > x0 && y1 > y0) || !Float.isFinite(x0 + y0 + x1 + y1 + skewTop + skewBottom)) {
            return this;
        }
        if (texture != 0 && texture != textureId) {
            flush();
        }
        if (count == capacity) {
            flush();
        }
        texture = textureId;
        float hw = (x1 - x0) / 2;
        float hh = (y1 - y0) / 2;
        colors[0] = colors[1] = colors[2] = colors[3] = argb;
        float k = KIND_GLYPH;
        vertex(x0 + skewTop, y0, -hw, -hh, hw, hh, softness, k, pxRange, weight, atlasWidth, atlasHeight, u0, v0);
        vertex(x1 + skewTop, y0, hw, -hh, hw, hh, softness, k, pxRange, weight, atlasWidth, atlasHeight, u1, v0);
        vertex(x1 + skewBottom, y1, hw, hh, hw, hh, softness, k, pxRange, weight, atlasWidth, atlasHeight, u1, v1);
        vertex(x0 + skewBottom, y1, -hw, hh, hw, hh, softness, k, pxRange, weight, atlasWidth, atlasHeight, u0, v1);
        count++;
        return this;
    }

    /**
     * Returns how many draw calls this batch has issued, for tests and profiling.
     *
     * @return the draw call count
     */
    public int drawCalls() {
        return drawCalls;
    }

    /**
     * A filled rounded rectangle.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param radius corner radius, clamped to half the shorter side
     * @param paint the colour
     * @return this batch
     */
    public ShapeBatch rect(float x, float y, float width, float height, float radius, Paint paint) {
        return box(x, y, width, height, radius, KIND_RECT, 0, paint, 0);
    }

    /**
     * A filled rounded rectangle in one colour.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param radius corner radius
     * @param argb the colour
     * @return this batch
     */
    public ShapeBatch rect(float x, float y, float width, float height, float radius, int argb) {
        return rect(x, y, width, height, radius, Paint.solid(argb));
    }

    /**
     * The outline of a rounded rectangle, drawn inside its edge.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param radius corner radius
     * @param strokeWidth outline width
     * @param paint the colour
     * @return this batch
     */
    public ShapeBatch outline(
            float x, float y, float width, float height, float radius, float strokeWidth, Paint paint) {
        if (!(strokeWidth > 0)) {
            throw new IllegalArgumentException("stroke width must be positive: " + strokeWidth);
        }
        return box(x, y, width, height, radius, KIND_RECT, strokeWidth, paint, 0);
    }

    /**
     * A filled circle.
     *
     * @param cx centre x
     * @param cy centre y
     * @param radius the radius
     * @param paint the colour
     * @return this batch
     */
    public ShapeBatch circle(float cx, float cy, float radius, Paint paint) {
        return rect(cx - radius, cy - radius, radius * 2, radius * 2, radius, paint);
    }

    /**
     * A ring segment with butt caps.
     *
     * @param cx centre x
     * @param cy centre y
     * @param radius outer radius
     * @param thickness ring thickness, inward from the outer radius
     * @param startRadians start angle; 0 points along +x, angles grow clockwise on screen
     * @param sweepRadians swept angle; {@code 2 * PI} or more draws a full ring
     * @param paint the colour; radial paints are not supported on arcs
     * @return this batch
     */
    public ShapeBatch arc(
            float cx, float cy, float radius, float thickness, float startRadians, float sweepRadians, Paint paint) {
        if (paint.radial()) {
            throw new IllegalArgumentException("radial paint is not supported on arcs");
        }
        requireDrawing();
        if (radius <= 0 || thickness <= 0 || sweepRadians <= 0) {
            return this;
        }
        float t = Math.min(thickness, radius);
        paint.corners(radius, radius, colors);
        emit(cx, cy, 1, 0, radius, radius, pixel, radius, t, 0, KIND_ARC, 0, 0, startRadians, sweepRadians, 0, 0, 0, 0);
        return this;
    }

    /**
     * A straight line.
     *
     * @param x0 start x
     * @param y0 start y
     * @param x1 end x
     * @param y1 end y
     * @param width line width
     * @param roundCaps round ends reaching half the width past each end point, or square ends at the points
     * @param paint the colour; a horizontal paint runs from start to end
     * @return this batch
     */
    public ShapeBatch line(float x0, float y0, float x1, float y1, float width, boolean roundCaps, Paint paint) {
        requireDrawing();
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (!(width > 0)) {
            return this;
        }
        float ax = len > 0 ? dx / len : 1;
        float ay = len > 0 ? dy / len : 0;
        float hw = len / 2 + (roundCaps ? width / 2 : 0);
        float hh = width / 2;
        if (hw <= 0) {
            return this;
        }
        paint.corners(hw, hh, colors);
        float radialRadius = paint.radial() ? (paint.value > 0 ? paint.value : Math.max(hw, hh)) : 0;
        emit(
                (x0 + x1) / 2,
                (y0 + y1) / 2,
                ax,
                ay,
                hw,
                hh,
                pixel,
                hw,
                hh,
                roundCaps ? hh : 0,
                KIND_RECT,
                0,
                paint.radial() ? 1 : 0,
                radialRadius,
                0,
                0,
                0,
                0,
                0);
        return this;
    }

    /**
     * Connected lines with round joins and caps. Joins overlap, so a translucent colour is darker where segments
     * meet.
     *
     * @param points x, y pairs; at least two points
     * @param width line width
     * @param paint the colour of every segment
     * @return this batch
     */
    public ShapeBatch polyline(float[] points, float width, Paint paint) {
        if (points.length < 4 || points.length % 2 != 0) {
            throw new IllegalArgumentException("polyline needs at least two x, y pairs");
        }
        for (int i = 0; i + 3 < points.length; i += 2) {
            line(points[i], points[i + 1], points[i + 2], points[i + 3], width, true, paint);
        }
        return this;
    }

    /**
     * A soft drop shadow for a rounded rectangle. Draw it before the shape, usually offset down and right.
     *
     * @param x left of the shape casting it
     * @param y top of the shape casting it
     * @param width width
     * @param height height
     * @param radius corner radius
     * @param softness half the width of the falloff band around the edge
     * @param argb shadow colour; its alpha is the opacity inside
     * @return this batch
     */
    public ShapeBatch shadow(float x, float y, float width, float height, float radius, float softness, int argb) {
        if (!(softness > 0)) {
            throw new IllegalArgumentException("softness must be positive: " + softness);
        }
        return box(x, y, width, height, radius, KIND_SHADOW, softness, Paint.solid(argb), softness);
    }

    /**
     * A glow inside the edge of a rounded rectangle, strongest at the edge.
     *
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param radius corner radius
     * @param glowWidth how far the glow reaches inward
     * @param argb glow colour
     * @return this batch
     */
    public ShapeBatch innerGlow(float x, float y, float width, float height, float radius, float glowWidth, int argb) {
        if (!(glowWidth > 0)) {
            throw new IllegalArgumentException("glow width must be positive: " + glowWidth);
        }
        return box(x, y, width, height, radius, KIND_GLOW, glowWidth, Paint.solid(argb), 0);
    }

    /**
     * A textured rounded rectangle, multiplied by a tint.
     *
     * @param textureId the GL texture
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     * @param radius corner radius
     * @param u0 texture coordinate at the left
     * @param v0 texture coordinate at the top
     * @param u1 texture coordinate at the right
     * @param v1 texture coordinate at the bottom
     * @param tint colour the texture is multiplied by; {@code 0xFFFFFFFF} for none
     * @return this batch
     */
    public ShapeBatch image(
            int textureId,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float u0,
            float v0,
            float u1,
            float v1,
            int tint) {
        requireDrawing();
        if (width <= 0 || height <= 0) {
            return this;
        }
        if (texture != 0 && texture != textureId) {
            flush();
        }
        texture = textureId;
        float hw = width / 2;
        float hh = height / 2;
        colors[0] = colors[1] = colors[2] = colors[3] = tint;
        emit(
                x + hw,
                y + hh,
                1,
                0,
                hw,
                hh,
                pixel,
                hw,
                hh,
                Sdf.clampRadius(radius, hw, hh),
                KIND_IMAGE,
                0,
                0,
                0,
                0,
                u0,
                v0,
                u1,
                v1);
        return this;
    }

    private ShapeBatch box(
            float x, float y, float width, float height, float radius, int kind, float param, Paint paint, float grow) {
        requireDrawing();
        if (width <= 0 || height <= 0) {
            return this;
        }
        float hw = width / 2;
        float hh = height / 2;
        paint.corners(hw, hh, colors);
        float radialRadius = paint.radial() ? (paint.value > 0 ? paint.value : Math.max(hw, hh)) : 0;
        emit(
                x + hw,
                y + hh,
                1,
                0,
                hw,
                hh,
                pixel + grow,
                hw,
                hh,
                Sdf.clampRadius(radius, hw, hh),
                kind,
                param,
                paint.radial() ? 1 : 0,
                radialRadius,
                0,
                0,
                0,
                0,
                0);
        return this;
    }

    /**
     * Writes one quad. The quad covers the half extents grown by {@code pad}, along the axis {@code (ax, ay)}.
     */
    private void emit(
            float cx,
            float cy,
            float ax,
            float ay,
            float hw,
            float hh,
            float pad,
            float s0,
            float s1,
            float s2,
            int kind,
            float p0,
            float p1,
            float p2,
            float p3,
            float u0,
            float v0,
            float u1,
            float v1) {
        requireDrawing();
        // A NaN or infinite position reaches the GPU as a sliver across the screen; drop the shape instead.
        if (!Float.isFinite(cx + cy + ax + ay + hw + hh + pad)) {
            return;
        }
        if (count == capacity) {
            flush();
        }
        float ex = hw + pad;
        float ey = hh + pad;
        corner(cx, cy, ax, ay, -ex, -ey, hw, hh, s0, s1, s2, kind, p0, p1, p2, p3, u0, v0, u1, v1);
        corner(cx, cy, ax, ay, ex, -ey, hw, hh, s0, s1, s2, kind, p0, p1, p2, p3, u0, v0, u1, v1);
        corner(cx, cy, ax, ay, ex, ey, hw, hh, s0, s1, s2, kind, p0, p1, p2, p3, u0, v0, u1, v1);
        corner(cx, cy, ax, ay, -ex, ey, hw, hh, s0, s1, s2, kind, p0, p1, p2, p3, u0, v0, u1, v1);
        count++;
    }

    private void corner(
            float cx,
            float cy,
            float ax,
            float ay,
            float lx,
            float ly,
            float hw,
            float hh,
            float s0,
            float s1,
            float s2,
            int kind,
            float p0,
            float p1,
            float p2,
            float p3,
            float u0,
            float v0,
            float u1,
            float v1) {
        vertex(
                cx + lx * ax - ly * ay,
                cy + lx * ay + ly * ax,
                lx,
                ly,
                s0,
                s1,
                s2,
                kind,
                p0,
                p1,
                p2,
                p3,
                u0 + (lx + hw) / (2 * hw) * (u1 - u0),
                v0 + (ly + hh) / (2 * hh) * (v1 - v0));
    }

    private void vertex(
            float x,
            float y,
            float lx,
            float ly,
            float s0,
            float s1,
            float s2,
            float kind,
            float p0,
            float p1,
            float p2,
            float p3,
            float u,
            float v) {
        ByteBuffer b = vertices;
        b.putFloat(x).putFloat(y);
        b.putFloat(lx).putFloat(ly);
        b.putFloat(s0).putFloat(s1).putFloat(s2).putFloat(kind);
        b.putFloat(p0).putFloat(p1).putFloat(p2).putFloat(p3);
        b.putFloat(u).putFloat(v);
        for (int c : colors) {
            b.putInt(rgbaBytes(c));
        }
    }

    private void flush() {
        if (count == 0) {
            texture = 0;
            return;
        }
        vertices.flip();
        try (StateGuard g = StateGuard.begin(gl, cache)) {
            g.blend(true)
                    .blendFunc(
                            GlBackend.SRC_ALPHA,
                            GlBackend.ONE_MINUS_SRC_ALPHA,
                            GlBackend.ONE,
                            GlBackend.ONE_MINUS_SRC_ALPHA)
                    .cull(false)
                    .program(program.id())
                    .vertexArray(vao)
                    .arrayBuffer(0);
            ensureVertexArray();
            if (texture != 0) {
                g.texture(0, texture);
            }
            gl.matrices(modelView, projection);
            Mat4.multiply(projection, modelView, mvp);
            program.set("u_mvp", mvp);
            program.set("u_texture", 0);
            program.set("u_alphaCutoff", alphaCutoff);
            long offset = stream.upload(vertices, STRIDE);
            if (stream.buffer() != vaoBuffer) {
                pointAttributes();
            }
            gl.drawElements(GlBackend.TRIANGLES, count * 6, indexType, 0, (int) (offset / STRIDE));
            drawCalls++;
        } finally {
            vertices.clear();
            count = 0;
            texture = 0;
        }
    }

    /**
     * Builds the vertex array and its index buffer once, two triangles per shape (0-1-2, 2-3-0), and leaves it bound.
     * Called after the guard has saved the host's binding.
     */
    private void ensureVertexArray() {
        if (vao == 0) {
            vao = gl.createVertexArray();
            gl.bindVertexArray(vao);
            ibo = gl.createBuffer();
            gl.bindBuffer(GlBackend.ELEMENT_ARRAY_BUFFER, ibo);
            gl.bufferData(GlBackend.ELEMENT_ARRAY_BUFFER, quadIndices(capacity, indexType), GlBackend.STATIC_DRAW);
            for (int a = 0; a < ATTRIBUTES.size(); a++) {
                gl.enableVertexAttrib(a);
            }
        }
    }

    /** Points the attributes at the current stream buffer; the vertex array remembers the buffer per attribute. */
    private void pointAttributes() {
        vaoBuffer = stream.buffer();
        gl.vertexAttribPointer(0, 2, GlBackend.FLOAT, false, STRIDE, 0);
        gl.vertexAttribPointer(1, 2, GlBackend.FLOAT, false, STRIDE, 8);
        gl.vertexAttribPointer(2, 4, GlBackend.FLOAT, false, STRIDE, 16);
        gl.vertexAttribPointer(3, 4, GlBackend.FLOAT, false, STRIDE, 32);
        gl.vertexAttribPointer(4, 2, GlBackend.FLOAT, false, STRIDE, 48);
        for (int c = 0; c < 4; c++) {
            gl.vertexAttribPointer(5 + c, 4, GlBackend.UNSIGNED_BYTE, true, STRIDE, 56 + 4L * c);
        }
    }

    static ByteBuffer quadIndices(int quads, int type) {
        return QuadIndices.create(quads, type);
    }

    /**
     * Marks the end of a frame for the streamed vertex buffers. {@link lol.trq.helios.Helios#tick()} calls it.
     */
    public void endFrame() {
        stream.endFrame();
    }

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    /** Reorders {@code 0xAARRGGBB} so a native-order int write lays down the bytes R, G, B, A. */
    private static int rgbaBytes(int argb) {
        int abgr = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
        return LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr);
    }

    private void requireDrawing() {
        if (!drawing) {
            throw new IllegalStateException("call begin(pixel) first");
        }
    }

    /** Deletes the program, the vertex array and the buffers. */
    @Override
    public void close() {
        program.close();
        stream.close();
        if (vao != 0) {
            gl.deleteVertexArray(vao);
            gl.deleteBuffer(ibo);
            vao = 0;
            ibo = 0;
        }
    }
}
