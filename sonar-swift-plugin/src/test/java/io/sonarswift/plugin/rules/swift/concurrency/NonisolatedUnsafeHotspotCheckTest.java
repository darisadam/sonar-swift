package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NonisolatedUnsafeHotspotCheckTest {

    @Test
    void flags_nonisolated_unsafe_declarations() {
        var issues = CheckTestHarness.run(new NonisolatedUnsafeHotspotCheck(), """
                enum FeatureFlags {
                    nonisolated(unsafe) static var debugMode = false
                }
                """);
        assertThat(issues).hasSize(1);
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("nonisolated(unsafe)");
    }

    @Test
    void does_not_fire_on_plain_nonisolated() {
        var issues = CheckTestHarness.run(new NonisolatedUnsafeHotspotCheck(), """
                actor S {
                    nonisolated var version: Int { 1 }
                }
                """);
        assertThat(issues).isEmpty();
    }
}
