package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetachedTaskMisuseCheckTest {

    @Test
    void flags_task_detached() {
        var issues = CheckTestHarness.run(new DetachedTaskMisuseCheck(), """
                func go() {
                    Task.detached {
                        await heavyWork()
                    }
                }
                """);
        assertThat(issues).hasSize(1);
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("Task.detached");
    }

    @Test
    void does_not_fire_on_plain_task() {
        var issues = CheckTestHarness.run(new DetachedTaskMisuseCheck(), """
                func go() {
                    Task { await heavyWork() }
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void fires_once_per_occurrence() {
        var issues = CheckTestHarness.run(new DetachedTaskMisuseCheck(), """
                func a() { Task.detached {} }
                func b() { Task.detached {} }
                """);
        assertThat(issues).hasSize(2);
    }
}
