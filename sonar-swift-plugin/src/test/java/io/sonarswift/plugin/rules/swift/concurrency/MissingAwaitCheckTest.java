package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.rules.CheckTestHarness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingAwaitCheckTest {

    /**
     * S1906 is AST-mode-only — its token-mode {@code visitTokens} is intentionally
     * a no-op (token signal is too noisy without type info). This test exists to
     * pin that behaviour: until AST mode lights up, the rule must NOT raise issues
     * on token input.
     */
    @Test
    void token_mode_is_a_noop() {
        var issues = CheckTestHarness.run(new MissingAwaitCheck(), """
                func process(_ pipe: Pipeline) async throws {
                    let result = pipe.run()
                }
                """);
        assertThat(issues).isEmpty();
    }
}
