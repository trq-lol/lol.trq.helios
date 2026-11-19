/* SPDX-License-Identifier: MIT */
package lol.trq.helios.model;

/**
 * Points on the 1.8.9 player model that things attach to. Each anchor moves and turns with its model part. Offsets are
 * in model pixels (1/16 of a model unit), with x toward the player's left, y down and z toward the back.
 */
public enum Attachment {
    /** Top centre of the head, or of the helmet when one is worn; turns with the head. */
    HEAD,
    /** Top centre of the torso, at the neck; tilts forward with the body when sneaking. */
    BODY,
    /** Top centre of the back, or of the chestplate when one is worn; tilts with the body. */
    BACK
}
