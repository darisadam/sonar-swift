package io.sonarswift.plugin.leak;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process holder for the most recent memory snapshot of the Swift parser
 * sidecar. The {@code SwiftSyntaxClient} probes the sidecar periodically (or
 * at end-of-scan) by sending {@code {"op":"metrics"}}; the response is parked
 * here, and the {@link LeakDetectionSensor} reads it when writing the leak
 * report.
 *
 * <p>Memory cost: at most one Snapshot record at a time (~100 bytes). Wrapped
 * in {@link AtomicReference} so the sensor and the parser-client thread don't
 * have to share a lock.</p>
 */
public final class ParserMemoryProbe {

    private ParserMemoryProbe() {}

    public record Snapshot(
            long timestampMillis,
            long residentBytes,
            long virtualBytes,
            int liveThreads,
            long allocationsTotal,
            long allocationsPerFile,
            int filesParsed) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestampMillis", timestampMillis);
            m.put("residentBytes", residentBytes);
            m.put("virtualBytes", virtualBytes);
            m.put("liveThreads", liveThreads);
            m.put("allocationsTotal", allocationsTotal);
            m.put("allocationsPerFile", allocationsPerFile);
            m.put("filesParsed", filesParsed);
            return m;
        }
    }

    private static final AtomicReference<Snapshot> LATEST = new AtomicReference<>();

    public static void publish(Snapshot s) {
        LATEST.set(s);
    }

    public static Snapshot latest() {
        return LATEST.get();
    }

    public static void clear() {
        LATEST.set(null);
    }
}
