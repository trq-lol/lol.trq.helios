/* SPDX-License-Identifier: MIT */
package lol.trq.helios.harness.mod;

import java.io.File;
import java.lang.reflect.Field;
import lol.trq.helios.Helios;
import lol.trq.helios.minecraft.MinecraftHelios;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Harness test mod. On the main menu it draws Helios shapes every frame. After {@code helios.harness.ticks} client
 * ticks (default 60) it runs {@link Checks} inside one frame, writes screenshots to {@code <gameDir>/helios/}, prints
 * {@code HELIOS-HARNESS} lines and exits with 0 when every check passed, 1 otherwise. With
 * {@code -Dhelios.harness.stay=true} it keeps the game open.
 *
 * <p>Compiled against Forge only; Minecraft is reached by reflection with SRG names.
 */
@Mod(modid = "helios_harness", name = "Helios Harness", version = "1", acceptableRemoteVersions = "*")
public class HarnessMod {
    private static final String MAIN_MENU = "net.minecraft.client.gui.GuiMainMenu";
    private static final int TICKS = Integer.getInteger("helios.harness.ticks", 60);
    private static final boolean STAY = Boolean.getBoolean("helios.harness.stay");

    private Helios helios;
    private int menuTicks;
    private boolean checkRequested;
    private Integer exitCode;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (exitCode != null) {
            if (!STAY) {
                FMLCommonHandler.instance().exitJava(exitCode, false);
            }
            return;
        }
        if (helios != null && ++menuTicks == TICKS) {
            checkRequested = true;
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        try {
            Object gui = event.getClass().getField("gui").get(event);
            if (gui == null || !gui.getClass().getName().equals(MAIN_MENU)) {
                return;
            }
            if (helios == null) {
                helios = MinecraftHelios.create();
                try {
                    Checks.stencilOnScreen = MinecraftHelios.enableStencil();
                } catch (IllegalStateException e) {
                    System.out.println("HELIOS-HARNESS stencil on screen unavailable: " + e.getCause());
                }
                System.out.println("HELIOS-HARNESS main framebuffer stencil=" + Checks.stencilOnScreen);
                System.out.println("HELIOS-HARNESS start helios=" + Helios.version() + " java="
                        + System.getProperty("java.version") + " optifine=" + optifine() + " gl=" + Checks.glInfo());
            }
            Frame frame = Frame.of(gui);
            if (checkRequested && exitCode == null) {
                checkRequested = false;
                File out = new File(gameDir(), "helios");
                exitCode = new Checks(helios, frame, out).run() ? 0 : 1;
            } else {
                Checks.drawScene(helios, frame);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("HELIOS-HARNESS FAIL exception " + e);
            e.printStackTrace(System.out);
            exitCode = 1;
        }
    }

    private static File gameDir() throws ReflectiveOperationException {
        Object mc = minecraft();
        return (File) mc.getClass().getField("field_71412_D").get(mc);
    }

    static Object minecraft() throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.minecraft.client.Minecraft", true, HarnessMod.class.getClassLoader());
        return type.getMethod("func_71410_x").invoke(null);
    }

    private static String optifine() {
        try {
            Class<?> config = Class.forName("Config", false, HarnessMod.class.getClassLoader());
            return String.valueOf(config.getMethod("getVersion").invoke(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            return "absent";
        }
    }

    /**
     * One frame's geometry.
     *
     * @param guiWidth the screen width in GUI units
     * @param guiHeight the screen height in GUI units
     * @param fbWidth the framebuffer width in pixels
     * @param fbHeight the framebuffer height in pixels
     */
    record Frame(int guiWidth, int guiHeight, int fbWidth, int fbHeight) {
        static Frame of(Object gui) throws ReflectiveOperationException {
            Object mc = minecraft();
            return new Frame(
                    intField(gui, "field_146294_l"),
                    intField(gui, "field_146295_m"),
                    intField(mc, "field_71443_c"),
                    intField(mc, "field_71440_d"));
        }

        /** Size of one framebuffer pixel in GUI units. */
        float pixel() {
            return guiWidth / (float) fbWidth;
        }

        private static int intField(Object owner, String name) throws ReflectiveOperationException {
            Field f = owner.getClass().getField(name);
            return f.getInt(owner);
        }
    }
}
