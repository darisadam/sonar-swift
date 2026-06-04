package io.sonarswift.cli.dashboard;

import java.util.Collection;
import java.util.Map;

/**
 * Minimal JSON serializer for the dashboard's API responses.
 *
 * <p>The CLI deliberately depends only on {@code slf4j-api} (see
 * {@code sonar-swift-cli/pom.xml}). Pulling in Jackson would be ~2 MB for what
 * the dashboard needs (write Maps + Lists + primitives). This is a hand-rolled
 * writer good enough for the small dashboard payloads.</p>
 *
 * <p>NOT a full JSON implementation — does not handle Unicode escape sequences
 * beyond the common ones, doesn't support arbitrary precision numbers. If the
 * dashboard's payloads grow beyond Maps / Lists / primitives, replace this
 * with Jackson via {@code maven-shade-plugin}.</p>
 */
public final class JsonWriter {

    private JsonWriter() {}

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object o) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof Boolean b) { sb.append(b); return; }
        if (o instanceof Number n) { sb.append(n); return; }
        if (o instanceof String s) { writeString(sb, s); return; }
        if (o instanceof Map<?, ?> m) { writeMap(sb, (Map<String, Object>) m); return; }
        if (o instanceof Collection<?> c) { writeArray(sb, c); return; }
        // fallback: stringify with toString()
        writeString(sb, String.valueOf(o));
    }

    private static void writeMap(StringBuilder sb, Map<String, Object> m) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, e.getKey());
            sb.append(':');
            write(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Collection<?> c) {
        sb.append('[');
        boolean first = true;
        for (Object o : c) {
            if (!first) sb.append(',');
            first = false;
            write(sb, o);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
