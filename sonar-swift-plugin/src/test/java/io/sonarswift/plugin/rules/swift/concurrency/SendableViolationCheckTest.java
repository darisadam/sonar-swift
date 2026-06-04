package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendableViolationCheckTest {

    @Test
    void flags_mutable_class_with_var_property_in_concurrent_file() {
        var issues = CheckTestHarness.run(new SendableViolationCheck(), """
                actor S {}

                class Counter {
                    var value = 0
                }
                """);
        assertThat(issues).hasSize(1);
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("not declared `Sendable`");
    }

    @Test
    void does_not_fire_when_file_has_no_concurrency_markers() {
        var issues = CheckTestHarness.run(new SendableViolationCheck(), """
                class Counter {
                    var value = 0
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_when_class_already_conforms_to_sendable() {
        var issues = CheckTestHarness.run(new SendableViolationCheck(), """
                actor S {}
                final class Counter: Sendable {
                    let value = 0
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_when_class_has_only_let_properties() {
        var issues = CheckTestHarness.run(new SendableViolationCheck(), """
                actor S {}
                class Counter {
                    let value = 0
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void respects_suppression_comment() {
        var issues = CheckTestHarness.run(new SendableViolationCheck(), """
                actor S {}
                // sonar-swift:disable-next-line S1900
                class Counter {
                    var value = 0
                }
                """);
        assertThat(issues).isEmpty();
    }
}
