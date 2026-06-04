package io.sonarswift.cli.pipeline.builtin.steps;

import java.util.List;
import java.util.Map;

/** Small helpers for reading typed params out of a step's {@code with:} map. */
public final class Params {

    private Params() {}

    public static String string(Map<String, Object> with, String key, String dflt) {
        Object v = with.get(key);
        return v == null ? dflt : v.toString();
    }

    public static String stringRequired(Map<String, Object> with, String key, String stepId) {
        String v = string(with, key, null);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(
                    "Step `" + stepId + "` requires `with." + key + "`");
        }
        return v;
    }

    public static boolean bool(Map<String, Object> with, String key, boolean dflt) {
        Object v = with.get(key);
        if (v == null) return dflt;
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v.toString());
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(Map<String, Object> with, String key) {
        Object v = with.get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            return l.stream().map(Object::toString).toList();
        }
        // Allow comma-separated form as a fallback for ergonomics.
        return List.of(v.toString().split("\\s*,\\s*"));
    }
}
