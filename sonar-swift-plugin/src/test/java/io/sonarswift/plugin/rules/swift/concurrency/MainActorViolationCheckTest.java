package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainActorViolationCheckTest {

    @Test
    void flags_uikit_touch_in_async_non_main_func() {
        var issues = CheckTestHarness.run(new MainActorViolationCheck(), """
                func loadProfile() async {
                    UIApplication.shared.open(url)
                }
                """);
        assertThat(issues).isNotEmpty();
        assertThat(CheckTestHarness.messages(issues).get(0))
                .contains("UIApplication");
    }

    @Test
    void does_not_fire_when_function_is_main_actor() {
        var issues = CheckTestHarness.run(new MainActorViolationCheck(), """
                @MainActor
                func loadProfile() async {
                    UIApplication.shared.open(url)
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void does_not_fire_in_sync_func() {
        var issues = CheckTestHarness.run(new MainActorViolationCheck(), """
                func loadProfile() {
                    UIApplication.shared.open(url)
                }
                """);
        assertThat(issues).isEmpty();
    }

    @Test
    void only_flags_known_ui_receivers() {
        var issues = CheckTestHarness.run(new MainActorViolationCheck(), """
                func helper() async {
                    Logger.shared.log("hello")
                }
                """);
        assertThat(issues).isEmpty();
    }
}
