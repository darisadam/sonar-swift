package io.sonarswift.plugin.qualitygate;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.measures.Metric;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggested quality gate thresholds for Swift / ObjC projects.
 *
 * <p>SonarQube models quality gates as server-side configuration — the
 * plugin API does not allow shipping a built-in gate the way it allows
 * built-in profiles. Instead, this class is the canonical reference: the
 * dashboard server reads it and offers a one-click "apply Sonar Swift way"
 * action via the SonarQube web API, and our docs explain the same values
 * for manual setup.</p>
 *
 * <p>The list deliberately mirrors what SonarSource ships for Java +
 * Kotlin (the closest cousin languages) so existing operators understand it
 * at a glance.</p>
 */
public final class SwiftQualityGate {

    private SwiftQualityGate() {}

    public static final String NAME = "Sonar Swift way";

    public record Condition(String metric, String op, String onLeak, String warning, String error) {}

    /**
     * Returns the recommended set of quality-gate conditions for Swift / ObjC.
     * Conditions on "new code" only (leak period) by default — matches the
     * Sonar best-practice for evolving projects.
     */
    public static List<Condition> recommended() {
        List<Condition> out = new ArrayList<>();

        // Reliability / Security on new code
        out.add(new Condition("new_blocker_violations", "GT", "yes", null, "0"));
        out.add(new Condition("new_critical_violations", "GT", "yes", null, "0"));
        out.add(new Condition("new_vulnerabilities", "GT", "yes", null, "0"));
        out.add(new Condition("new_security_hotspots_reviewed", "LT", "yes", null, "100"));

        // Coverage on new code — 80%
        out.add(new Condition("new_coverage", "LT", "yes", null, "80.0"));

        // Duplication on new code — under 3%
        out.add(new Condition("new_duplicated_lines_density", "GT", "yes", null, "3.0"));

        // Maintainability — A rating on new code
        out.add(new Condition("new_maintainability_rating", "GT", "yes", null, "1"));

        // Whole-project guardrails
        out.add(new Condition("blocker_violations", "GT", "no", null, "0"));

        return out;
    }

    /** Metric keys the gate references, in case downstream tooling needs them. */
    public static List<String> referencedMetrics() {
        return recommended().stream().map(Condition::metric).toList();
    }
}
