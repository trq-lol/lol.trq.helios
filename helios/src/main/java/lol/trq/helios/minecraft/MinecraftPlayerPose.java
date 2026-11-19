/* SPDX-License-Identifier: MIT */
package lol.trq.helios.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import lol.trq.helios.model.PlayerModel;
import lol.trq.helios.model.PlayerPose;

/**
 * Fills a {@link PlayerPose} from a live player, interpolated to the frame the way the player renderer does it.
 * Reached by reflection with SRG names, MCP names as fallback; everything is resolved once, up front.
 */
public final class MinecraftPlayerPose {
    private static final int HELMET = 3;
    private static final int CHESTPLATE = 2;

    private final MinecraftWorld world;
    private final Class<?> living;
    private final Class<?> clientPlayer;
    private final Field renderYawOffset;
    private final Field prevRenderYawOffset;
    private final Field rotationYawHead;
    private final Field prevRotationYawHead;
    private final Field rotationPitch;
    private final Field prevRotationPitch;
    private final Field ridingEntity;
    private final Method isSneaking;
    private final Method currentArmor;
    private final double[] position = new double[3];

    /**
     * Resolves everything up front.
     *
     * @param loader the class loader Minecraft's classes load through
     * @param world the world helper that supplies positions
     * @throws IllegalStateException if a class or member cannot be found
     */
    public MinecraftPlayerPose(ClassLoader loader, MinecraftWorld world) {
        this.world = world;
        try {
            Class<?> entity = Class.forName("net.minecraft.entity.Entity", false, loader);
            living = Class.forName("net.minecraft.entity.EntityLivingBase", false, loader);
            clientPlayer = Class.forName("net.minecraft.client.entity.EntityPlayerSP", false, loader);
            renderYawOffset = field(living, "field_70761_aq", "renderYawOffset");
            prevRenderYawOffset = field(living, "field_70760_ar", "prevRenderYawOffset");
            rotationYawHead = field(living, "field_70759_as", "rotationYawHead");
            prevRotationYawHead = field(living, "field_70758_at", "prevRotationYawHead");
            rotationPitch = field(entity, "field_70125_A", "rotationPitch");
            prevRotationPitch = field(entity, "field_70127_C", "prevRotationPitch");
            ridingEntity = field(entity, "field_70154_o", "ridingEntity");
            isSneaking = method(entity, "func_70093_af", "isSneaking");
            currentArmor = method(living, "func_82169_q", "getCurrentArmor", int.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot resolve Minecraft player members", e);
        }
    }

    /**
     * Reads a player's pose for this frame.
     *
     * @param player a {@code net.minecraft.entity.player.EntityPlayer}
     * @param partialTicks the partial tick, from {@link MinecraftWorld#partialTicks()}
     * @param camera the camera position, from {@link MinecraftWorld#camera(double[])}
     * @param out receives the pose
     * @return {@code out}
     */
    public PlayerPose read(Object player, float partialTicks, double[] camera, PlayerPose out) {
        try {
            world.position(player, partialTicks, camera, position);
            out.x = position[0];
            out.y = position[1];
            out.z = position[2];
            out.bodyYaw = angle(player, prevRenderYawOffset, renderYawOffset, partialTicks);
            out.headYaw = angle(player, prevRotationYawHead, rotationYawHead, partialTicks);
            float p0 = prevRotationPitch.getFloat(player);
            out.pitch = p0 + (rotationPitch.getFloat(player) - p0) * partialTicks;
            out.sneaking = (Boolean) isSneaking.invoke(player);
            out.clientPlayer = clientPlayer.isInstance(player);
            Object mount = ridingEntity.get(player);
            out.mountYaw = living.isInstance(mount)
                    ? angle(mount, prevRenderYawOffset, renderYawOffset, partialTicks)
                    : Float.NaN;
            out.helmet = currentArmor.invoke(player, HELMET) != null;
            out.chestplate = currentArmor.invoke(player, CHESTPLATE) != null;
            return out;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float angle(Object o, Field previous, Field current, float partialTicks)
            throws IllegalAccessException {
        return PlayerModel.interpolateAngle(previous.getFloat(o), current.getFloat(o), partialTicks);
    }

    private static Method method(Class<?> owner, String srg, String mcp, Class<?>... params)
            throws NoSuchMethodException {
        Method m;
        try {
            m = owner.getMethod(srg, params);
        } catch (NoSuchMethodException e) {
            m = owner.getMethod(mcp, params);
        }
        return m;
    }

    private static Field field(Class<?> owner, String srg, String mcp) throws NoSuchFieldException {
        Field f;
        try {
            f = owner.getDeclaredField(srg);
        } catch (NoSuchFieldException e) {
            f = owner.getDeclaredField(mcp);
        }
        f.setAccessible(true);
        return f;
    }
}
