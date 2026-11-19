/* SPDX-License-Identifier: MIT */
package lol.trq.helios.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.trq.helios.math.Mat4;
import org.junit.jupiter.api.Test;

class PlayerModelTest {
    private static final float EPS = 1e-5f;
    private static final float PX = 0.9375f / 16;

    private final PlayerPose pose = new PlayerPose().reset();
    private final float[] m = new float[16];
    private final float[] p = new float[4];

    private float[] at(Attachment a, float x, float y, float z) {
        PlayerModel.anchor(pose, a, m);
        return Mat4.transform(m, x, y, z, 1, p);
    }

    @Test
    void headTopOfAStandingPlayerMatchesVanilla() {
        float[] top = at(Attachment.HEAD, 0, 0, 0);
        assertEquals(0, top[0], EPS);
        assertEquals(1.88232421875f, top[1], EPS);
        assertEquals(0, top[2], EPS);
    }

    @Test
    void helmetRaisesTheHeadAnchorOnePixel() {
        pose.helmet = true;
        assertEquals(1.88232421875f + PX, at(Attachment.HEAD, 0, 0, 0)[1], EPS);
    }

    @Test
    void sneakingLowersOtherPlayersFurtherThanTheClientPlayer() {
        pose.sneaking = true;
        pose.clientPlayer = true;
        float client = at(Attachment.HEAD, 0, 0, 0)[1];
        assertEquals(1.88232421875f - 0.2f * 0.9375f - PX, client, EPS);
        pose.clientPlayer = false;
        assertEquals(client - 0.125f, at(Attachment.HEAD, 0, 0, 0)[1], EPS);
    }

    @Test
    void positionTranslatesEveryAnchor() {
        pose.x = 10;
        pose.y = 64;
        pose.z = -3;
        float[] top = at(Attachment.HEAD, 0, 0, 0);
        assertEquals(10, top[0], EPS);
        assertEquals(65.88232421875f, top[1], 1e-4f);
        assertEquals(-3, top[2], EPS);
    }

    @Test
    void theFaceLooksAlongTheYaw() {
        float[] face = at(Attachment.HEAD, 0, 4, -4);
        assertTrue(face[2] > 0.2f, "yaw 0 faces +z");
        pose.bodyYaw = 90;
        pose.headYaw = 90;
        face = at(Attachment.HEAD, 0, 4, -4);
        assertTrue(face[0] < -0.2f && Math.abs(face[2]) < EPS, "yaw 90 faces -x");
        pose.bodyYaw = 0;
        face = at(Attachment.HEAD, 0, 4, -4);
        assertTrue(face[0] < -0.2f && Math.abs(face[2]) < EPS, "the head turns on its own");
    }

    @Test
    void pitchTipsTheFaceDown() {
        float level = at(Attachment.HEAD, 0, 4, -4)[1];
        pose.pitch = 45;
        assertTrue(at(Attachment.HEAD, 0, 4, -4)[1] < level);
    }

    @Test
    void backSitsBehindTheBodyAndOutOnAChestplate() {
        assertEquals(-2 * PX, at(Attachment.BACK, 0, 0, 0)[2], EPS);
        pose.chestplate = true;
        assertEquals(-3 * PX, at(Attachment.BACK, 0, 0, 0)[2], EPS);
        assertEquals(0, at(Attachment.BODY, 0, 0, 0)[2], EPS);
    }

    @Test
    void sneakingTiltsTheBodyForward() {
        float standing = at(Attachment.BODY, 0, 12, 0)[2];
        pose.sneaking = true;
        assertTrue(at(Attachment.BODY, 0, 12, 0)[2] < standing, "the hips move back, behind a player facing +z");
    }

    @Test
    void ridingLimitsTheBodyTurnFromTheMount() {
        pose.mountYaw = 0;
        pose.headYaw = 120;
        pose.bodyYaw = 120;
        assertEquals(120 - 85 + 85 * 0.2f, PlayerModel.bodyYaw(pose), EPS);
        assertEquals(120, PlayerModel.netHeadYaw(pose), EPS);
        pose.headYaw = 30;
        assertEquals(30 - 30, PlayerModel.bodyYaw(pose), EPS);
        pose.mountYaw = Float.NaN;
        assertEquals(120, PlayerModel.bodyYaw(pose), EPS);
    }

    @Test
    void anglesWrapAndInterpolateTheShortWay() {
        assertEquals(-170, PlayerModel.wrap(190), EPS);
        assertEquals(-180, PlayerModel.wrap(180), EPS);
        assertEquals(10, PlayerModel.wrap(-350), EPS);
        assertEquals(180, PlayerModel.interpolateAngle(170, -170, 0.5f), EPS);
        assertEquals(15, PlayerModel.interpolateAngle(10, 20, 0.5f), EPS);
    }
}
