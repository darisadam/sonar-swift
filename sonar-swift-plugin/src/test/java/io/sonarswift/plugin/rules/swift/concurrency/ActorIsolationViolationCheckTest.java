package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActorIsolationViolationCheckTest {

    @Test
    void flags_assignment_to_actor_property_without_await() {
        var issues = CheckTestHarness.run(new ActorIsolationViolationCheck(), """
                actor Counter {
                    var value = 0
                }
                func bump(_ c: Counter) {
                    c.value = 1
                }
                """);
        assertThat(issues).isNotEmpty();
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("actor-isolated property");
    }

    @Test
    void does_not_fire_when_await_is_present_on_same_line() {
        var issues = CheckTestHarness.run(new ActorIsolationViolationCheck(), """
                actor Counter {
                    var value = 0
                }
                func bump(_ c: Counter) async {
                    await c.value = 1
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_when_no_actor_is_declared_in_file() {
        var issues = CheckTestHarness.run(new ActorIsolationViolationCheck(), """
                class Bag {
                    var contents: [Int] = []
                }
                func add(_ b: Bag) {
                    b.contents = []
                }
                """);
        assertThat(issues).isEmpty();
    }
}
