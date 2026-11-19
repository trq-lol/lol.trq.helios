/* SPDX-License-Identifier: MIT */
package lol.trq.helios.math;

/**
 * 4x4 matrix operations on {@code float[16]} arrays in OpenGL's column-major order: element (row r, column c) is at
 * {@code m[c * 4 + r]}. Every method writes into an output array; only {@link #multiply} with an aliased output
 * allocates (one temporary array). Outputs may alias inputs unless a method says otherwise.
 */
public final class Mat4 {
    private Mat4() {}

    /**
     * Sets the identity.
     *
     * @param out the matrix
     * @return {@code out}
     */
    public static float[] identity(float[] out) {
        for (int i = 0; i < 16; i++) {
            out[i] = i % 5 == 0 ? 1 : 0;
        }
        return out;
    }

    /**
     * Multiplies {@code a * b}: applying the result is applying {@code b} first, then {@code a}.
     *
     * @param a the left matrix
     * @param b the right matrix
     * @param out receives the product; may be {@code a} or {@code b}
     * @return {@code out}
     */
    public static float[] multiply(float[] a, float[] b, float[] out) {
        float r0;
        float r1;
        float r2;
        float r3;
        float[] tmp = out == a || out == b ? new float[16] : out;
        for (int c = 0; c < 4; c++) {
            float b0 = b[c * 4];
            float b1 = b[c * 4 + 1];
            float b2 = b[c * 4 + 2];
            float b3 = b[c * 4 + 3];
            r0 = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3;
            r1 = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3;
            r2 = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3;
            r3 = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
            tmp[c * 4] = r0;
            tmp[c * 4 + 1] = r1;
            tmp[c * 4 + 2] = r2;
            tmp[c * 4 + 3] = r3;
        }
        if (tmp != out) {
            System.arraycopy(tmp, 0, out, 0, 16);
        }
        return out;
    }

    /**
     * A perspective projection, as {@code gluPerspective}.
     *
     * @param fovYDegrees vertical field of view
     * @param aspect width over height
     * @param near near plane distance, positive
     * @param far far plane distance
     * @param out receives the matrix
     * @return {@code out}
     */
    public static float[] perspective(float fovYDegrees, float aspect, float near, float far, float[] out) {
        float f = (float) (1 / Math.tan(Math.toRadians(fovYDegrees) / 2));
        identity(out);
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) / (near - far);
        out[11] = -1;
        out[14] = 2 * far * near / (near - far);
        out[15] = 0;
        return out;
    }

    /**
     * An orthographic projection, as {@code glOrtho}.
     *
     * @param left left plane
     * @param right right plane
     * @param bottom bottom plane
     * @param top top plane
     * @param near near plane
     * @param far far plane
     * @param out receives the matrix
     * @return {@code out}
     */
    public static float[] ortho(float left, float right, float bottom, float top, float near, float far, float[] out) {
        identity(out);
        out[0] = 2 / (right - left);
        out[5] = 2 / (top - bottom);
        out[10] = -2 / (far - near);
        out[12] = -(right + left) / (right - left);
        out[13] = -(top + bottom) / (top - bottom);
        out[14] = -(far + near) / (far - near);
        return out;
    }

    /**
     * A translation.
     *
     * @param x x offset
     * @param y y offset
     * @param z z offset
     * @param out receives the matrix
     * @return {@code out}
     */
    public static float[] translation(float x, float y, float z, float[] out) {
        identity(out);
        out[12] = x;
        out[13] = y;
        out[14] = z;
        return out;
    }

    /**
     * A rotation about an axis, as {@code glRotatef}.
     *
     * @param degrees the angle, counter-clockwise looking down the axis
     * @param x axis x
     * @param y axis y
     * @param z axis z
     * @param out receives the matrix
     * @return {@code out}
     */
    public static float[] rotation(float degrees, float x, float y, float z, float[] out) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        float ax = x / len;
        float ay = y / len;
        float az = z / len;
        float c = (float) Math.cos(Math.toRadians(degrees));
        float s = (float) Math.sin(Math.toRadians(degrees));
        float t = 1 - c;
        identity(out);
        out[0] = t * ax * ax + c;
        out[1] = t * ax * ay + s * az;
        out[2] = t * ax * az - s * ay;
        out[4] = t * ax * ay - s * az;
        out[5] = t * ay * ay + c;
        out[6] = t * ay * az + s * ax;
        out[8] = t * ax * az + s * ay;
        out[9] = t * ay * az - s * ax;
        out[10] = t * az * az + c;
        return out;
    }

    /**
     * Transforms a point: {@code m * (x, y, z, w)}.
     *
     * @param m the matrix
     * @param x x
     * @param y y
     * @param z z
     * @param w w, 1 for points and 0 for directions
     * @param out receives the four components
     * @return {@code out}
     */
    public static float[] transform(float[] m, double x, double y, double z, double w, float[] out) {
        out[0] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12] * w);
        out[1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13] * w);
        out[2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14] * w);
        out[3] = (float) (m[3] * x + m[7] * y + m[11] * z + m[15] * w);
        return out;
    }

    /**
     * Inverts a matrix.
     *
     * @param m the matrix
     * @param out receives the inverse; must not be {@code m}
     * @return false, leaving {@code out} unspecified, when the matrix is singular
     */
    public static boolean invert(float[] m, float[] out) {
        if (out == m) {
            throw new IllegalArgumentException("out must not alias m");
        }
        out[0] = m[5] * m[10] * m[15]
                - m[5] * m[11] * m[14]
                - m[9] * m[6] * m[15]
                + m[9] * m[7] * m[14]
                + m[13] * m[6] * m[11]
                - m[13] * m[7] * m[10];
        out[4] = -m[4] * m[10] * m[15]
                + m[4] * m[11] * m[14]
                + m[8] * m[6] * m[15]
                - m[8] * m[7] * m[14]
                - m[12] * m[6] * m[11]
                + m[12] * m[7] * m[10];
        out[8] = m[4] * m[9] * m[15]
                - m[4] * m[11] * m[13]
                - m[8] * m[5] * m[15]
                + m[8] * m[7] * m[13]
                + m[12] * m[5] * m[11]
                - m[12] * m[7] * m[9];
        out[12] = -m[4] * m[9] * m[14]
                + m[4] * m[10] * m[13]
                + m[8] * m[5] * m[14]
                - m[8] * m[6] * m[13]
                - m[12] * m[5] * m[10]
                + m[12] * m[6] * m[9];
        out[1] = -m[1] * m[10] * m[15]
                + m[1] * m[11] * m[14]
                + m[9] * m[2] * m[15]
                - m[9] * m[3] * m[14]
                - m[13] * m[2] * m[11]
                + m[13] * m[3] * m[10];
        out[5] = m[0] * m[10] * m[15]
                - m[0] * m[11] * m[14]
                - m[8] * m[2] * m[15]
                + m[8] * m[3] * m[14]
                + m[12] * m[2] * m[11]
                - m[12] * m[3] * m[10];
        out[9] = -m[0] * m[9] * m[15]
                + m[0] * m[11] * m[13]
                + m[8] * m[1] * m[15]
                - m[8] * m[3] * m[13]
                - m[12] * m[1] * m[11]
                + m[12] * m[3] * m[9];
        out[13] = m[0] * m[9] * m[14]
                - m[0] * m[10] * m[13]
                - m[8] * m[1] * m[14]
                + m[8] * m[2] * m[13]
                + m[12] * m[1] * m[10]
                - m[12] * m[2] * m[9];
        out[2] = m[1] * m[6] * m[15]
                - m[1] * m[7] * m[14]
                - m[5] * m[2] * m[15]
                + m[5] * m[3] * m[14]
                + m[13] * m[2] * m[7]
                - m[13] * m[3] * m[6];
        out[6] = -m[0] * m[6] * m[15]
                + m[0] * m[7] * m[14]
                + m[4] * m[2] * m[15]
                - m[4] * m[3] * m[14]
                - m[12] * m[2] * m[7]
                + m[12] * m[3] * m[6];
        out[10] = m[0] * m[5] * m[15]
                - m[0] * m[7] * m[13]
                - m[4] * m[1] * m[15]
                + m[4] * m[3] * m[13]
                + m[12] * m[1] * m[7]
                - m[12] * m[3] * m[5];
        out[14] = -m[0] * m[5] * m[14]
                + m[0] * m[6] * m[13]
                + m[4] * m[1] * m[14]
                - m[4] * m[2] * m[13]
                - m[12] * m[1] * m[6]
                + m[12] * m[2] * m[5];
        out[3] = -m[1] * m[6] * m[11]
                + m[1] * m[7] * m[10]
                + m[5] * m[2] * m[11]
                - m[5] * m[3] * m[10]
                - m[9] * m[2] * m[7]
                + m[9] * m[3] * m[6];
        out[7] = m[0] * m[6] * m[11]
                - m[0] * m[7] * m[10]
                - m[4] * m[2] * m[11]
                + m[4] * m[3] * m[10]
                + m[8] * m[2] * m[7]
                - m[8] * m[3] * m[6];
        out[11] = -m[0] * m[5] * m[11]
                + m[0] * m[7] * m[9]
                + m[4] * m[1] * m[11]
                - m[4] * m[3] * m[9]
                - m[8] * m[1] * m[7]
                + m[8] * m[3] * m[5];
        out[15] = m[0] * m[5] * m[10]
                - m[0] * m[6] * m[9]
                - m[4] * m[1] * m[10]
                + m[4] * m[2] * m[9]
                + m[8] * m[1] * m[6]
                - m[8] * m[2] * m[5];
        float det = m[0] * out[0] + m[1] * out[4] + m[2] * out[8] + m[3] * out[12];
        if (det == 0) {
            return false;
        }
        float inv = 1 / det;
        for (int i = 0; i < 16; i++) {
            out[i] *= inv;
        }
        return true;
    }
}
