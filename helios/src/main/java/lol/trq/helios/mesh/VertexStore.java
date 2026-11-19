/* SPDX-License-Identifier: MIT */
package lol.trq.helios.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Vertex bytes in one {@link MeshFormat}, with the sinks that write them and the CPU transform they apply. */
abstract class VertexStore {
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    final MeshFormat format;
    ByteBuffer data;
    int vertices;
    private final float[] matrix = new float[16];
    private boolean transformed;
    private float tx;
    private float ty;
    private float tz;

    final QuadSink quads = new Quads();
    final LineSink lines = new Lines();
    final TexturedSink textured = new Textured();

    VertexStore(MeshFormat format, ByteBuffer data) {
        this.format = format;
        this.data = data;
    }

    /** Makes room for four more vertices; called before the first vertex of every quad. */
    abstract void reserveQuad();

    final int quadCount() {
        return vertices / 4;
    }

    final void clearTransform() {
        transformed = false;
    }

    private void transform(float[] m) {
        transformed = m != null;
        if (transformed) {
            System.arraycopy(m, 0, matrix, 0, 16);
        }
    }

    private void point(float x, float y, float z) {
        if (transformed) {
            float[] m = matrix;
            tx = m[0] * x + m[4] * y + m[8] * z + m[12];
            ty = m[1] * x + m[5] * y + m[9] * z + m[13];
            tz = m[2] * x + m[6] * y + m[10] * z + m[14];
        } else {
            tx = x;
            ty = y;
            tz = z;
        }
    }

    private void putPoint(float x, float y, float z) {
        point(x, y, z);
        data.putFloat(tx).putFloat(ty).putFloat(tz);
    }

    private void putNormal(float x, float y, float z) {
        float nx = x;
        float ny = y;
        float nz = z;
        if (transformed) {
            float[] m = matrix;
            nx = m[0] * x + m[4] * y + m[8] * z;
            ny = m[1] * x + m[5] * y + m[9] * z;
            nz = m[2] * x + m[6] * y + m[10] * z;
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float s = len > 0 ? 127 / len : 0;
        data.put((byte) Math.round(nx * s));
        data.put((byte) Math.round(ny * s));
        data.put((byte) Math.round(nz * s));
        data.put((byte) 0);
    }

    private void putColor(int argb) {
        int abgr = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
        data.putInt(LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
    }

    private void require(MeshFormat expected) {
        if (format != expected) {
            throw new IllegalStateException("this store holds " + format + " vertices, not " + expected);
        }
    }

    private final class Quads implements QuadSink {
        @Override
        public QuadSink vertex(float x, float y, float z, int argb) {
            require(MeshFormat.COLOR);
            if ((vertices & 3) == 0) {
                reserveQuad();
            }
            putPoint(x, y, z);
            putColor(argb);
            vertices++;
            return this;
        }

        @Override
        public QuadSink transform(float[] m) {
            VertexStore.this.transform(m);
            return this;
        }
    }

    private final class Lines implements LineSink {
        @Override
        public LineSink line(float x0, float y0, float z0, float x1, float y1, float z1, int argb, float width) {
            require(MeshFormat.LINE);
            if (!(width > 0)) {
                return this;
            }
            reserveQuad();
            point(x0, y0, z0);
            float ax = tx;
            float ay = ty;
            float az = tz;
            point(x1, y1, z1);
            float bx = tx;
            float by = ty;
            float bz = tz;
            corner(ax, ay, az, bx, by, bz, 0, -1, width, argb);
            corner(ax, ay, az, bx, by, bz, 0, 1, width, argb);
            corner(ax, ay, az, bx, by, bz, 1, 1, width, argb);
            corner(ax, ay, az, bx, by, bz, 1, -1, width, argb);
            vertices += 4;
            return this;
        }

        private void corner(
                float ax, float ay, float az, float bx, float by, float bz, float along, float side, float w, int c) {
            data.putFloat(ax)
                    .putFloat(ay)
                    .putFloat(az)
                    .putFloat(bx)
                    .putFloat(by)
                    .putFloat(bz);
            data.putFloat(along).putFloat(side).putFloat(w);
            putColor(c);
        }

        @Override
        public LineSink transform(float[] m) {
            VertexStore.this.transform(m);
            return this;
        }
    }

    private final class Textured implements TexturedSink {
        @Override
        public TexturedSink vertex(
                float x, float y, float z, float u, float v, float nx, float ny, float nz, int argb) {
            require(MeshFormat.TEXTURED);
            if ((vertices & 3) == 0) {
                reserveQuad();
            }
            putPoint(x, y, z);
            data.putFloat(u).putFloat(v);
            putNormal(nx, ny, nz);
            putColor(argb);
            vertices++;
            return this;
        }

        @Override
        public TexturedSink transform(float[] m) {
            VertexStore.this.transform(m);
            return this;
        }
    }
}
