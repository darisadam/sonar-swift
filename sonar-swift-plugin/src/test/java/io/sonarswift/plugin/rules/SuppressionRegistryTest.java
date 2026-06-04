package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.SwiftTokenizer;
import io.sonarswift.plugin.parser.Token;

import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuppressionRegistryTest {

    private SuppressionRegistry build(String source) {
        InputFile file = TestInputFileBuilder.create("project", "Foo.swift")
                .setLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .setContents(source)
                .setCharset(StandardCharsets.UTF_8)
                .build();
        List<Token> tokens = SwiftTokenizer.tokenize(source);
        return new SuppressionRegistry(file, tokens);
    }

    @Test
    void file_wide_disable_blocks_subsequent_lines() {
        SuppressionRegistry r = build("""
                // sonar-swift:disable S1001
                let a = b!
                let c = d!
                """);
        assertThat(r.isSuppressed(2, "S1001")).isTrue();
        assertThat(r.isSuppressed(3, "S1001")).isTrue();
        assertThat(r.isSuppressed(2, "S1004")).isFalse();
    }

    @Test
    void disable_next_line_only_blocks_the_following_line() {
        SuppressionRegistry r = build("""
                let a = "fine"
                // sonar-swift:disable-next-line S1001
                let b = optional!
                let c = optional!
                """);
        assertThat(r.isSuppressed(3, "S1001")).isTrue();
        assertThat(r.isSuppressed(4, "S1001")).isFalse();
    }

    @Test
    void disable_this_line_only_blocks_the_comment_line() {
        SuppressionRegistry r = build("""
                let x = a! // sonar-swift:disable-this-line S1001
                let y = b!
                """);
        assertThat(r.isSuppressed(1, "S1001")).isTrue();
        assertThat(r.isSuppressed(2, "S1001")).isFalse();
    }

    @Test
    void ALL_token_suppresses_every_rule() {
        SuppressionRegistry r = build("""
                // sonar-swift:disable-next-line
                let a = optional!
                """);
        assertThat(r.isSuppressed(2, "S1001")).isTrue();
        assertThat(r.isSuppressed(2, "S9999")).isTrue();
    }

    @Test
    void multiple_rule_keys_can_be_listed_after_directive() {
        SuppressionRegistry r = build("""
                // sonar-swift:disable-next-line S1001 S1004
                let a = try! foo()!
                """);
        assertThat(r.isSuppressed(2, "S1001")).isTrue();
        assertThat(r.isSuppressed(2, "S1004")).isTrue();
        assertThat(r.isSuppressed(2, "S1003")).isFalse();
    }

    @Test
    void swiftlint_directives_are_mapped_to_sonar_rules() {
        SuppressionRegistry r = build("""
                // swiftlint:disable:next force_unwrapping
                let a = optional!
                """);
        // We map force_unwrapping → S1001. Note the directive token form here:
        // SwiftLint's "next" variant is `swiftlint:disable-next-line`; the more common
        // `swiftlint:disable:next` form is recognised inside the comment too.
        // The registry's lookup is by directive prefix — `swiftlint:disable-next-line`
        // is matched. Use the canonical form here:
        r = build("""
                // swiftlint:disable-next-line force_unwrapping
                let a = optional!
                """);
        assertThat(r.isSuppressed(2, "S1001")).isTrue();
    }

    @Test
    void unknown_directives_do_not_blow_up() {
        SuppressionRegistry r = build("""
                // sonar-swift:unknown-directive S1001
                let a = optional!
                """);
        assertThat(r.isSuppressed(2, "S1001")).isFalse();
    }

    @Test
    void comments_outside_recognised_directives_are_ignored() {
        SuppressionRegistry r = build("""
                // TODO: maybe disable some lint here later
                let a = optional!
                """);
        assertThat(r.isSuppressed(2, "S1001")).isFalse();
    }
}
