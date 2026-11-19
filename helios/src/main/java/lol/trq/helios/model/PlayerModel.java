/* SPDX-License-Identifier: MIT */
package lol.trq.helios.model;

import lol.trq.helios.math.Mat4;

/**
 * The transforms Minecraft 1.8.9 renders a player with, as plain matrix math: the same chain as
 * {@code RenderPlayer}, {@code RendererLivingEntity}, {@code ModelPlayer} and {@code ModelBiped}, so something drawn
 * at an {@link Attachment} follows the model exactly.
 *
 * <p>The chain from model pixels to camera-relative world blocks is {@code T(position) · R_y(180 - bodyYaw) ·
 * S(-1, -1, 1) · S(0.9375) · T(0, -1.5078125, 0) · S(1/16)}, then the part's pivot and rotations. Sneaking adds
 * {@code T(0, 0.2, 0)} before the pixel scale, moves the head pivot down one pixel, tilts the body forward 0.5 radians
 * and, for players other than the client's own, lowers the position by 0.125. Riding a living entity limits how far
 * the body turns from the mount, as vanilla does. Sleeping, dying and the elytra are not modelled.
 *
 * <p>Every method writes into arrays the caller owns and allocates nothing.
 */
public final class PlayerModel {
    /** Model units per pixel. */
    public static final float PIXEL = 1f / 16f;
    /** The player renderer's scale. */
    public static final float SCALE = 0.9375f;
    /** The living renderer's vertical model offset, in model units. */
    public static final float MODEL_OFFSET = -1.5078125f;
    /** How far a sneaking model shifts down, in model units, before scaling. */
    public static final float SNEAK_SHIFT = 0.2f;
    /** How far vanilla lowers other sneaking players, in blocks. */
    public static final float SNEAK_DROP = 0.125f;
    /** Forward tilt of the body when sneaking, in radians. */
    public static final float SNEAK_BODY_PITCH = 0.5f;
    /** Head pivot height when sneaking, in pixels. */
    public static final float SNEAK_HEAD_PIVOT = 1f;
    /** Height of the head, in pixels. */
    public static final float HEAD_HEIGHT = 8f;
    /** Distance from the body pivot to the back surface, in pixels. */
    public static final float BACK_DEPTH = 2f;
    /** How far armour sits out from the skin, in pixels. */
    public static final float ARMOUR_OUTSET = 1f;
    /** The furthest the body turns from a ridden mount, in degrees. */
    public static final float MOUNT_LIMIT = 85f;

    private PlayerModel() {}

    /**
     * Returns the yaw the body is drawn at, after the riding limit.
     *
     * @param pose the pose
     * @return the body yaw in degrees
     */
    public static float bodyYaw(PlayerPose pose) {
        if (!pose.riding()) {
            return pose.bodyYaw;
        }
        float relative = Math.max(-MOUNT_LIMIT, Math.min(MOUNT_LIMIT, wrap(pose.headYaw - pose.mountYaw)));
        float yaw = pose.headYaw - relative;
        if (relative * relative > 2500) {
            yaw += relative * 0.2f;
        }
        return yaw;
    }

    /**
     * Returns the head yaw relative to the body, as the model receives it. When riding, vanilla measures it from the
     * mount rather than from the drawn body.
     *
     * @param pose the pose
     * @return the relative head yaw in degrees
     */
    public static float netHeadYaw(PlayerPose pose) {
        return pose.headYaw - (pose.riding() ? pose.mountYaw : pose.bodyYaw);
    }

    /**
     * Writes the model root: model pixels, before any part's pivot, to camera-relative world blocks.
     *
     * @param pose the pose
     * @param out receives the matrix
     * @return {@code out}
     */
    public static float[] root(PlayerPose pose, float[] out) {
        float y = (float) pose.y - (pose.sneaking && !pose.clientPlayer ? SNEAK_DROP : 0);
        Mat4.translation((float) pose.x, y, (float) pose.z, out);
        rotateY(out, 180 - bodyYaw(pose));
        scale(out, -SCALE, -SCALE, SCALE);
        translate(out, 0, MODEL_OFFSET + (pose.sneaking ? SNEAK_SHIFT : 0), 0);
        scale(out, PIXEL, PIXEL, PIXEL);
        return out;
    }

    /**
     * Writes an attachment's transform: model pixels relative to the anchor, to camera-relative world blocks. Draw an
     * attachment in pixels around the origin, with x toward the player's left, y down and z toward the back.
     *
     * @param pose the pose
     * @param attachment the anchor
     * @param out receives the matrix
     * @return {@code out}
     */
    public static float[] anchor(PlayerPose pose, Attachment attachment, float[] out) {
        root(pose, out);
        switch (attachment) {
            case HEAD -> {
                translate(out, 0, pose.sneaking ? SNEAK_HEAD_PIVOT : 0, 0);
                rotateY(out, netHeadYaw(pose));
                rotateX(out, pose.pitch);
                translate(out, 0, -HEAD_HEIGHT - (pose.helmet ? ARMOUR_OUTSET : 0), 0);
            }
            case BODY -> rotateX(out, pose.sneaking ? (float) Math.toDegrees(SNEAK_BODY_PITCH) : 0);
            case BACK -> {
                rotateX(out, pose.sneaking ? (float) Math.toDegrees(SNEAK_BODY_PITCH) : 0);
                translate(out, 0, 0, BACK_DEPTH + (pose.chestplate ? ARMOUR_OUTSET : 0));
            }
        }
        return out;
    }

    /**
     * Wraps an angle to {@code [-180, 180)}, as {@code MathHelper.wrapAngleTo180_float}.
     *
     * @param degrees the angle
     * @return the wrapped angle
     */
    public static float wrap(float degrees) {
        float d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return d;
    }

    /**
     * Interpolates between two angles the short way round, as {@code RendererLivingEntity.interpolateRotation}.
     *
     * @param previous the angle last tick
     * @param current the angle this tick
     * @param partialTicks the frame's partial tick
     * @return the interpolated angle
     */
    public static float interpolateAngle(float previous, float current, float partialTicks) {
        float d = current - previous;
        while (d < -180) {
            d += 360;
        }
        while (d >= 180) {
            d -= 360;
        }
        return previous + partialTicks * d;
    }

    private static void translate(float[] m, float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    private static void scale(float[] m, float x, float y, float z) {
        for (int r = 0; r < 4; r++) {
            m[r] *= x;
            m[4 + r] *= y;
            m[8 + r] *= z;
        }
    }

    /** {@code m = m · R_y(degrees)}, as {@code glRotatef(degrees, 0, 1, 0)}. */
    private static void rotateY(float[] m, float degrees) {
        if (degrees == 0) {
            return;
        }
        double r = Math.toRadians(degrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        for (int i = 0; i < 4; i++) {
            float x = m[i];
            float z = m[8 + i];
            m[i] = x * c - z * s;
            m[8 + i] = x * s + z * c;
        }
    }

    /** {@code m = m · R_x(degrees)}, as {@code glRotatef(degrees, 1, 0, 0)}. */
    private static void rotateX(float[] m, float degrees) {
        if (degrees == 0) {
            return;
        }
        double r = Math.toRadians(degrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        for (int i = 0; i < 4; i++) {
            float y = m[4 + i];
            float z = m[8 + i];
            m[4 + i] = y * c + z * s;
            m[8 + i] = -y * s + z * c;
        }
    }
}
