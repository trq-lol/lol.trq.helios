/* SPDX-License-Identifier: MIT */
package lol.trq.helios.batch;

/**
 * How a shape is coloured: one colour, a gradient across its box, or a radial gradient from its centre. Colours are
 * packed {@code 0xAARRGGBB}, not premultiplied, and mixed per channel in sRGB.
 *
 * <p>Box gradients are bilinear between four corner colours; linear gradients at any angle are expressed exactly that
 * way. For a line the box is the line itself, so a horizontal gradient runs from its start to its end.
 */
public final class Paint {
    enum Mode {
        CORNERS,
        LINEAR,
        RADIAL
    }

    final Mode mode;
    final int c0;
    final int c1;
    final int c2;
    final int c3;
    final float value;
    private final float cos;
    private final float sin;

    private Paint(Mode mode, int c0, int c1, int c2, int c3, float value) {
        this.mode = mode;
        this.c0 = c0;
        this.c1 = c1;
        this.c2 = c2;
        this.c3 = c3;
        this.value = value;
        this.cos = (float) Math.cos(value);
        this.sin = (float) Math.sin(value);
    }

    /**
     * One colour.
     *
     * @param argb the colour
     * @return the paint
     */
    public static Paint solid(int argb) {
        return new Paint(Mode.CORNERS, argb, argb, argb, argb, 0);
    }

    /**
     * A different colour in each corner, blended bilinearly.
     *
     * @param topLeft top-left colour
     * @param topRight top-right colour
     * @param bottomRight bottom-right colour
     * @param bottomLeft bottom-left colour
     * @return the paint
     */
    public static Paint corners(int topLeft, int topRight, int bottomRight, int bottomLeft) {
        return new Paint(Mode.CORNERS, topLeft, topRight, bottomRight, bottomLeft, 0);
    }

    /**
     * Left to right.
     *
     * @param left colour at the left edge
     * @param right colour at the right edge
     * @return the paint
     */
    public static Paint horizontal(int left, int right) {
        return corners(left, right, right, left);
    }

    /**
     * Top to bottom.
     *
     * @param top colour at the top edge
     * @param bottom colour at the bottom edge
     * @return the paint
     */
    public static Paint vertical(int top, int bottom) {
        return corners(top, top, bottom, bottom);
    }

    /**
     * A linear gradient across the box at an angle; 0 degrees runs left to right, 90 top to bottom.
     *
     * @param degrees the direction
     * @param from colour at the start corner
     * @param to colour at the end corner
     * @return the paint
     */
    public static Paint linear(float degrees, int from, int to) {
        return new Paint(Mode.LINEAR, from, to, 0, 0, (float) Math.toRadians(degrees));
    }

    /**
     * A radial gradient from the centre of the shape.
     *
     * @param centre colour at the centre
     * @param edge colour at {@code radius} and beyond
     * @param radius where the edge colour is reached; 0 or less means the shape's half width or half height,
     *     whichever is larger
     * @return the paint
     */
    public static Paint radial(int centre, int edge, float radius) {
        return new Paint(Mode.RADIAL, centre, edge, edge, edge, radius);
    }

    boolean radial() {
        return mode == Mode.RADIAL;
    }

    /**
     * Resolves the four corner colours for a box.
     *
     * @param halfWidth half the box width
     * @param halfHeight half the box height
     * @param out receives top-left, top-right, bottom-right, bottom-left
     */
    void corners(float halfWidth, float halfHeight, int[] out) {
        if (mode != Mode.LINEAR) {
            out[0] = c0;
            out[1] = c1;
            out[2] = c2;
            out[3] = c3;
            return;
        }
        float dx = cos;
        float dy = sin;
        float extent = Math.abs(halfWidth * dx) + Math.abs(halfHeight * dy);
        for (int i = 0; i < 4; i++) {
            float cx = i == 1 || i == 2 ? halfWidth : -halfWidth;
            float cy = i >= 2 ? halfHeight : -halfHeight;
            float t = extent <= 0 ? 0.5f : (cx * dx + cy * dy) / extent * 0.5f + 0.5f;
            out[i] = lerp(c0, c1, t);
        }
    }

    /**
     * Mixes two packed colours per channel.
     *
     * @param a the colour at 0
     * @param b the colour at 1
     * @param t the mix, clamped to {@code [0, 1]}
     * @return the mixed colour
     */
    public static int lerp(int a, int b, float t) {
        return lol.trq.helios.color.Colors.lerp(a, b, t);
    }
}
