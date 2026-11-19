/* SPDX-License-Identifier: MIT */
package lol.trq.helios.harness.mod;

import lol.trq.helios.Helios;
import lol.trq.helios.gl.GlBackend;
import lol.trq.helios.math.Mat4;
import lol.trq.helios.math.Projector;
import lol.trq.helios.mesh.MeshFormat;
import lol.trq.helios.mesh.MeshRecorder;
import lol.trq.helios.mesh.MeshRenderer;
import lol.trq.helios.mesh.StaticMesh;
import lol.trq.helios.mesh.TexturedSink;
import lol.trq.helios.minecraft.MinecraftPlayerPose;
import lol.trq.helios.minecraft.MinecraftWorld;
import lol.trq.helios.model.Attachment;
import lol.trq.helios.model.PlayerModel;
import lol.trq.helios.model.PlayerPose;

/**
 * World mode ({@code -Dhelios.harness.world=true}): creates a singleplayer world and, after the world renders, checks
 * projection with the game's real camera matrices. A point straight ahead of the player's eyes must land in the
 * middle of the screen, and the player's interpolated position must coincide with the camera.
 */
final class WorldCheck {
    private WorldCheck() {}

    /** Creates and enters a flat-seeded default world. */
    static void launchWorld() throws ReflectiveOperationException {
        ClassLoader loader = WorldCheck.class.getClassLoader();
        Class<?> gameType = Class.forName("net.minecraft.world.WorldSettings$GameType", true, loader);
        Object creative = gameType.getField("CREATIVE").get(null);
        Class<?> worldType = Class.forName("net.minecraft.world.WorldType", true, loader);
        Object defaultType = worldType.getField("field_77137_b").get(null);
        Class<?> settingsClass = Class.forName("net.minecraft.world.WorldSettings", true, loader);
        Object settings = settingsClass
                .getConstructor(long.class, gameType, boolean.class, boolean.class, worldType)
                .newInstance(1891L, creative, false, false, defaultType);
        Object mc = HarnessMod.minecraft();
        mc.getClass()
                .getMethod("func_71371_a", String.class, String.class, settingsClass)
                .invoke(mc, "helios-world-" + System.nanoTime(), "helios-world", settings);
    }

    static Object player() throws ReflectiveOperationException {
        Object mc = HarnessMod.minecraft();
        return mc.getClass().getField("field_71439_g").get(mc);
    }

    /**
     * Runs the checks; call it from the world render hook, where the camera matrices are current.
     *
     * @return whether every check passed
     */
    static boolean run(Helios helios) throws ReflectiveOperationException {
        Object player = player();
        Class<?> entity = Class.forName("net.minecraft.entity.Entity", false, WorldCheck.class.getClassLoader());
        float yaw = entity.getField("field_70177_z").getFloat(player);
        float pitch = entity.getField("field_70125_A").getFloat(player);
        float eye = (Float) entity.getMethod("func_70047_e").invoke(player);

        int[] viewport = new int[4];
        helios.gl().getIntegers(lol.trq.helios.gl.GlBackend.VIEWPORT, viewport);
        Projector projector = new Projector().capture(helios.gl(), 1);

        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double lx = -Math.sin(yr) * Math.cos(pr);
        double ly = -Math.sin(pr);
        double lz = Math.cos(yr) * Math.cos(pr);
        float[] out = new float[3];
        boolean inFront = projector.project(lx * 8, eye + ly * 8, lz * 8, out);
        float cx = viewport[0] + viewport[2] / 2f;
        float cy = viewport[3] / 2f;
        float error = (float) Math.hypot(out[0] - cx, out[1] - cy);
        boolean centred = inFront && error < 1.5f;
        System.out.printf(
                "HELIOS-HARNESS world look yaw=%.1f pitch=%.1f projected=(%.2f, %.2f) centre=(%.1f, %.1f) depth=%.4f%n",
                yaw, pitch, out[0], out[1], cx, cy, out[2]);
        report("a point ahead of the eyes projects to the screen centre", centred, "off by " + error + " px");

        boolean behind = !projector.project(-lx * 8, eye - ly * 8, -lz * 8, out);
        report("a point behind the camera is rejected", behind, "projected " + out[0] + ", " + out[1]);

        MinecraftWorld world = new MinecraftWorld(WorldCheck.class.getClassLoader());
        float pt = world.partialTicks();
        double[] cam = world.camera(new double[3]);
        double[] pos = world.position(player, pt, cam, new double[3]);
        double offset = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        report("player position interpolates onto the camera", offset < 0.01, "offset " + offset);

        double[] box = world.box(player, pt, cam, new double[6]);
        report(
                "player box is 0.6 wide and 1.8 tall",
                Math.abs(box[3] - box[0] - 0.6) < 1e-3 && Math.abs(box[4] - box[1] - 1.8) < 1e-3,
                java.util.Arrays.toString(box));
        boolean meshes = checkMeshes(helios, world, player, pt, cam);
        return centred && behind && offset < 0.01 && meshes;
    }

    /** Reads the player's pose, checks the head anchor height, and draws through every mesh sink at the anchors. */
    private static boolean checkMeshes(Helios helios, MinecraftWorld world, Object player, float pt, double[] cam) {
        PlayerPose pose = new MinecraftPlayerPose(WorldCheck.class.getClassLoader(), world)
                .read(player, pt, cam, new PlayerPose());
        float[] m = new float[16];
        float[] p = new float[4];
        Mat4.transform(PlayerModel.anchor(pose, Attachment.HEAD, m), 0, 0, 0, 1, p);
        double above = p[1] - pose.y;
        boolean head = !pose.sneaking && !pose.helmet && Math.abs(above - 1.88232421875) < 1e-3;
        report("head anchor sits 1.882 blocks above the feet", head, "above " + above + " " + pose.sneaking);
        report("the client player is recognised", pose.clientPlayer, "clientPlayer false");

        GlBackend gl = helios.gl();
        int program = gl.getInteger(GlBackend.CURRENT_PROGRAM);
        int vao = gl.getInteger(GlBackend.VERTEX_ARRAY_BINDING);
        int buffer = gl.getInteger(GlBackend.ARRAY_BUFFER_BINDING);
        MeshRenderer meshes = helios.meshes();
        int before = meshes.drawCalls();
        int texture = gl.createRgbTexture(
                1,
                1,
                java.nio.ByteBuffer.allocateDirect(3)
                        .put(new byte[] {-1, -1, -1})
                        .flip());
        MeshRecorder rec = new MeshRecorder(MeshFormat.TEXTURED);
        TexturedSink t = rec.textured();
        t.vertex(-4, 0, -4, 0, 0, 0, -1, 0, 0xFFFFD700).vertex(4, 0, -4, 1, 0, 0, -1, 0, 0xFFFFD700);
        t.vertex(4, 0, 4, 1, 1, 0, -1, 0, 0xFFFFD700).vertex(-4, 0, 4, 0, 1, 0, -1, 0, 0xFFFFD700);
        StaticMesh hat = rec.build(meshes);
        try {
            PlayerModel.anchor(pose, Attachment.BACK, m);
            meshes.quads()
                    .transform(m)
                    .vertex(-5, 0, 0, 0x80FF0000)
                    .vertex(5, 0, 0, 0x80FF0000)
                    .vertex(5, 16, 0, 0x80FF0000)
                    .vertex(-5, 16, 0, 0x80FF0000)
                    .transform(null);
            meshes.lines().line(-1, 0, 1, 1, 2, 1, 0xFF00FFFF, 2).line(0, 0, -50, 0, 0, 50, 0xFFFFFFFF, 0.5f);
            meshes.texture(texture)
                    .textured()
                    .transform(m)
                    .vertex(-5, 0, 0, 0, 0, 0, 0, 1, -1)
                    .vertex(5, 0, 0, 1, 0, 0, 0, 1, -1)
                    .vertex(5, 16, 0, 1, 1, 0, 0, 1, -1)
                    .vertex(-5, 16, 0, 0, 1, 0, 0, 1, -1)
                    .transform(null);
            meshes.draw(hat, PlayerModel.anchor(pose, Attachment.HEAD, m));
            meshes.texture(0).flush();
        } finally {
            hat.close();
            gl.deleteTexture(texture);
        }
        boolean drew = meshes.drawCalls() - before == 4;
        report("every mesh sink and a static mesh draw", drew, (meshes.drawCalls() - before) + " draw calls");
        boolean restored = gl.getInteger(GlBackend.CURRENT_PROGRAM) == program
                && gl.getInteger(GlBackend.VERTEX_ARRAY_BINDING) == vao
                && gl.getInteger(GlBackend.ARRAY_BUFFER_BINDING) == buffer;
        report("mesh drawing restores program, vertex array and array buffer", restored, "bindings changed");
        return head && pose.clientPlayer && drew && restored;
    }

    private static boolean failed;

    private static void report(String what, boolean ok, String detail) {
        failed |= !ok;
        System.out.println("HELIOS-HARNESS check " + (ok ? "pass" : "FAIL") + ": world: " + what
                + (ok ? "" : " (" + detail + ")"));
    }

    static boolean anyFailed() {
        return failed;
    }
}
