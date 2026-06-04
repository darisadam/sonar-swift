package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRetainCycleCheckTest {

    @Test
    void flags_task_capturing_self_strongly() {
        var issues = CheckTestHarness.run(new TaskRetainCycleCheck(), """
                func observe() {
                    Task {
                        await self.refresh()
                    }
                }
                """);
        assertThat(issues).isNotEmpty();
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("captures `self` strongly");
    }

    @Test
    void does_not_fire_when_weak_self_is_in_capture_list() {
        var issues = CheckTestHarness.run(new TaskRetainCycleCheck(), """
                func observe() {
                    Task { [weak self] in
                        guard let self else { return }
                        await self.refresh()
                    }
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_when_unowned_self_is_in_capture_list() {
        var issues = CheckTestHarness.run(new TaskRetainCycleCheck(), """
                func observe() {
                    Task { [unowned self] in
                        self.refresh()
                    }
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_when_task_body_does_not_reference_self() {
        var issues = CheckTestHarness.run(new TaskRetainCycleCheck(), """
                func loadOne(_ url: URL) {
                    Task {
                        let data = try await URLSession.shared.data(from: url)
                        print(data)
                    }
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_match_task_used_as_a_type() {
        var issues = CheckTestHarness.run(new TaskRetainCycleCheck(), """
                func makeTask() -> Task<Void, Never> {
                    Task<Void, Never> {
                        self.refresh()
                    }
                }
                """);
        // Treated as type — first Task<> is filtered; but second instantiates with explicit
        // generics + then `{...}` so the check should NOT pick that up either (the next token
        // after `Task` is `<`, so the rule treats Task as a type and skips).
        assertThat(issues).isEmpty();
    }
}
