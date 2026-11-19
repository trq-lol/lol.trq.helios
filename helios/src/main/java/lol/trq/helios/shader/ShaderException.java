/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

/** A shader failed to preprocess, compile or link. The message names files and lines where the driver gave them. */
public final class ShaderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what failed
     */
    public ShaderException(String message) {
        super(message);
    }
}
