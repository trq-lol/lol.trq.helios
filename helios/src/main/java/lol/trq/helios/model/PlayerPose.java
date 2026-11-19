/* SPDX-License-Identifier: MIT */
package lol.trq.helios.model;

/**
 * What a player model's attachment points depend on, for one frame: position, the body and head angles interpolated
 * to the frame, and the states that move parts of the 1.8.9 model. Mutable and reused, so reading a pose each frame
 * allocates nothing. {@link lol.trq.helios.minecraft.MinecraftPlayerPose} fills one from a live player.
 *
 * <p>Angles are in degrees, as Minecraft stores them: yaw 0 faces +Z (south) and grows clockwise seen from above;
 * pitch is positive looking down.
 */
public final class PlayerPose {
    /** Feet position relative to the camera, x. */
    public double x;
    /** Feet position relative to the camera, y. */
    public double y;
    /** Feet position relative to the camera, z. */
    public double z;
    /** Body yaw ({@code renderYawOffset}), interpolated to the frame. */
    public float bodyYaw;
    /** Head yaw ({@code rotationYawHead}), interpolated to the frame. */
    public float headYaw;
    /** Head pitch ({@code rotationPitch}), interpolated to the frame. */
    public float pitch;
    /** Whether the player is sneaking. */
    public boolean sneaking;
    /**
     * Whether this is the player the client controls. Vanilla lowers every other sneaking player by 0.125 blocks, but
     * not this one.
     */
    public boolean clientPlayer;
    /**
     * Body yaw of the living entity being ridden, interpolated to the frame, or {@code NaN} when not riding one.
     * Riding clamps how far the body turns from the mount, as vanilla does.
     */
    public float mountYaw = Float.NaN;
    /** Whether a helmet is worn: head attachments sit on the armour, one model pixel out. */
    public boolean helmet;
    /** Whether a chestplate is worn: back attachments sit on the armour, one model pixel out. */
    public boolean chestplate;

    /**
     * Resets to a player standing at the camera, facing south, with nothing worn.
     *
     * @return this pose
     */
    public PlayerPose reset() {
        x = 0;
        y = 0;
        z = 0;
        bodyYaw = 0;
        headYaw = 0;
        pitch = 0;
        sneaking = false;
        clientPlayer = false;
        mountYaw = Float.NaN;
        helmet = false;
        chestplate = false;
        return this;
    }

    /**
     * Returns whether the player rides a living entity.
     *
     * @return true when {@link #mountYaw} is set
     */
    public boolean riding() {
        return !Float.isNaN(mountYaw);
    }
}
