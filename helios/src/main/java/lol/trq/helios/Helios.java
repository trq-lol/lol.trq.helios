/* SPDX-License-Identifier: MIT */
package lol.trq.helios;

/** Entry point and version information for the Helios rendering library. */
public final class Helios {
    /** The GLSL version every built-in shader targets: GLSL 1.20, the OpenGL 2.1 baseline. */
    public static final int GLSL_VERSION = 120;

    private Helios() {}

    /**
     * Returns the library version from the jar manifest.
     *
     * @return the version, or {@code "dev"} when running from classes outside a jar
     */
    public static String version() {
        String v = Helios.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
