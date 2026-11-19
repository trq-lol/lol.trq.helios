/* SPDX-License-Identifier: MIT */
package lol.trq.helios.shader;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps the locations in a GLSL info log back to file names.
 *
 * <p>Drivers print locations in different shapes: NVIDIA {@code 0(12) : error}, AMD, Intel and Apple
 * {@code ERROR: 0:12: ...}, Mesa {@code 0:12(5): error}. Each leading {@code <source>:<line>} or
 * {@code <source>(<line>)} becomes {@code <file>:<line>}.
 */
public final class ShaderLog {
    private static final Pattern PAREN = Pattern.compile("(?m)^(\\s*)(\\d+)\\((\\d+)\\)");
    private static final Pattern COLON =
            Pattern.compile("(?m)^((?:\\s*(?:ERROR|WARNING|error|warning):\\s*)?)(\\d+):(\\d+)");

    private ShaderLog() {}

    /**
     * Rewrites a log.
     *
     * @param log the driver's log
     * @param files file names by source-string number
     * @return the rewritten log; an unknown source number is left as it was
     */
    public static String describe(String log, List<String> files) {
        if (log == null || log.isEmpty()) {
            return "";
        }
        String out = replace(PAREN, log, files);
        return replace(COLON, out, files);
    }

    private static String replace(Pattern pattern, String log, List<String> files) {
        Matcher m = pattern.matcher(log);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int source = Integer.parseInt(m.group(2));
            String replacement = source < files.size() ? m.group(1) + files.get(source) + ":" + m.group(3) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
