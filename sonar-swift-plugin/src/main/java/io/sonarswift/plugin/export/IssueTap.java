package io.sonarswift.plugin.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-process collector for native-check findings, used by
 * {@link SarifExportSensor} to publish a SARIF file at end-of-scan.
 *
 * <p>The Java API's {@code NewIssue} doesn't give us a way to read back the
 * issues we've saved. To avoid a dependency on Sonar's web-API, we tee each
 * native check's report into this collector at the same time as we save to
 * Sonar.</p>
 *
 * <p>This adds a per-issue cost on the order of microseconds (HashMap put +
 * a Finding allocation). Memory budget: each Finding is ~200 bytes; a
 * 100k-LOC project with 5k findings holds ~1 MB. Negligible.</p>
 *
 * <p>Thread-safety: all writes synchronize on the underlying list. Reads are
 * snapshot copies, safe to iterate.</p>
 */
public final class IssueTap {

    private IssueTap() {}

    public record Finding(
            String ruleKey,
            String severity,
            String type,
            String message,
            String filePath,
            int startLine,
            int endLine) {}

    private static final List<Finding> FINDINGS = Collections.synchronizedList(new ArrayList<>());

    public static void record(Finding f) {
        FINDINGS.add(f);
    }

    public static int size() {
        return FINDINGS.size();
    }

    /** Returns a stable snapshot of the findings recorded so far. */
    public static List<Finding> snapshot() {
        synchronized (FINDINGS) {
            return List.copyOf(FINDINGS);
        }
    }

    /** Clear the tap. Called by the test fixtures between runs. */
    public static void clear() {
        FINDINGS.clear();
    }
}
