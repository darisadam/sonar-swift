package io.sonarswift.plugin.qualitygate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwiftQualityGateTest {

    @Test
    void recommended_set_includes_the_core_metric_keys() {
        List<SwiftQualityGate.Condition> conditions = SwiftQualityGate.recommended();
        List<String> metricKeys = conditions.stream().map(SwiftQualityGate.Condition::metric).toList();

        // The core quality-gate metrics the iOS team's PR-review workflow depends on.
        assertThat(metricKeys).contains(
                "new_blocker_violations",
                "new_critical_violations",
                "new_vulnerabilities",
                "new_security_hotspots_reviewed",
                "new_coverage",
                "new_duplicated_lines_density",
                "new_maintainability_rating",
                "blocker_violations");
    }

    @Test
    void every_condition_has_an_error_threshold() {
        for (SwiftQualityGate.Condition c : SwiftQualityGate.recommended()) {
            assertThat(c.error())
                    .as("rule '%s' must have a non-empty error threshold", c.metric())
                    .isNotBlank();
        }
    }

    @Test
    void new_coverage_threshold_is_eighty_percent() {
        SwiftQualityGate.Condition coverage = SwiftQualityGate.recommended().stream()
                .filter(c -> "new_coverage".equals(c.metric()))
                .findFirst().orElseThrow();
        assertThat(coverage.error()).isEqualTo("80.0");
        assertThat(coverage.op()).isEqualTo("LT");
    }

    @Test
    void referenced_metrics_matches_recommended_conditions() {
        assertThat(SwiftQualityGate.referencedMetrics())
                .hasSize(SwiftQualityGate.recommended().size());
    }

    @Test
    void quality_gate_name_is_stable() {
        // External tooling references this by name; don't accidentally rename it.
        assertThat(SwiftQualityGate.NAME).isEqualTo("Sonar Swift way");
    }
}
