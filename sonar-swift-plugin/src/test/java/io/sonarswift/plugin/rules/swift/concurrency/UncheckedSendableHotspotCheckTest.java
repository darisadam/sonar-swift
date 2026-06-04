package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UncheckedSendableHotspotCheckTest {

    @Test
    void flags_at_unchecked_sendable() {
        var issues = CheckTestHarness.run(new UncheckedSendableHotspotCheck(), """
                final class Cache: @unchecked Sendable {
                    var storage: [String: Data] = [:]
                }
                """);
        assertThat(issues).hasSize(1);
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("@unchecked Sendable");
    }

    @Test
    void does_not_fire_on_plain_sendable() {
        var issues = CheckTestHarness.run(new UncheckedSendableHotspotCheck(), """
                struct V: Sendable { let x: Int }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void fires_once_per_occurrence() {
        var issues = CheckTestHarness.run(new UncheckedSendableHotspotCheck(), """
                final class A: @unchecked Sendable {}
                final class B: @unchecked Sendable {}
                """);
        assertThat(issues).hasSize(2);
    }
}
