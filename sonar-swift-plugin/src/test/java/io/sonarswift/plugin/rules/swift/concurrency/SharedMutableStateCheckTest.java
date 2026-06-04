package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SharedMutableStateCheckTest {

    @Test
    void flags_static_var_at_top_level() {
        var issues = CheckTestHarness.run(new SharedMutableStateCheck(), """
                enum FeatureFlags {
                    static var debugMode = false
                }
                """);
        assertThat(issues).hasSize(1);
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("`static var` outside an `actor`");
    }

    @Test
    void does_not_fire_on_static_let() {
        var issues = CheckTestHarness.run(new SharedMutableStateCheck(), """
                enum FeatureFlags {
                    static let debugMode = false
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_on_static_var_inside_actor() {
        var issues = CheckTestHarness.run(new SharedMutableStateCheck(), """
                actor FeatureFlags {
                    static var debugMode = false
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_when_marked_nonisolated_unsafe() {
        var issues = CheckTestHarness.run(new SharedMutableStateCheck(), """
                enum FeatureFlags {
                    nonisolated(unsafe) static var debugMode = false
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void respects_suppression_comment() {
        var issues = CheckTestHarness.run(new SharedMutableStateCheck(), """
                enum FeatureFlags {
                    // sonar-swift:disable-next-line S1907
                    static var debugMode = false
                }
                """);
        assertThat(issues).isEmpty();
    }
}
