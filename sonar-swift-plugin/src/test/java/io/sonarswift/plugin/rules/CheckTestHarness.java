package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.SwiftTokenizer;
import io.sonarswift.plugin.parser.Token;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Shared test scaffolding for {@link SwiftCheck} unit tests.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Collection<Issue> issues = CheckTestHarness.run(new ForceUnwrapCheck(), """
 *     let x = optional!
 *     """);
 * assertThat(issues).hasSize(1);
 * }</pre>
 *
 * <p>The harness:</p>
 * <ul>
 *   <li>Creates an in-memory project rooted at a tmp dir</li>
 *   <li>Tokenizes the source via {@link SwiftTokenizer}</li>
 *   <li>Builds a {@link SuppressionRegistry} so comment-based suppressions work</li>
 *   <li>Invokes the check's {@code visitTokens}</li>
 *   <li>Returns the collected issues</li>
 * </ul>
 */
public final class CheckTestHarness {

    private CheckTestHarness() {}

    public static Collection<Issue> run(SwiftCheck check, String source) {
        return run(check, "Foo.swift", source);
    }

    public static Collection<Issue> run(SwiftCheck check, String filename, String source) {
        SensorContextTester context = SensorContextTester.create(Path.of(".").toAbsolutePath());

        InputFile inputFile = TestInputFileBuilder.create("project", filename)
                .setLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .setContents(source)
                .setCharset(StandardCharsets.UTF_8)
                .build();
        context.fileSystem().add((org.sonar.api.batch.fs.internal.DefaultInputFile) inputFile);

        List<Token> tokens = SwiftTokenizer.tokenize(source);
        check.setSuppressions(new SuppressionRegistry(inputFile, tokens));
        check.visitTokens(inputFile, tokens, context);

        return context.allIssues();
    }

    /** Convenience — return only issue messages for assertion brevity. */
    public static List<String> messages(Collection<Issue> issues) {
        return issues.stream()
                .map(i -> i.primaryLocation().message())
                .toList();
    }

    /** Convenience — return only the rule keys raised. */
    public static List<String> ruleKeys(Collection<Issue> issues) {
        return issues.stream()
                .map(i -> i.ruleKey().rule())
                .toList();
    }

    /** Convenience — return the 1-based start lines of the issues. */
    public static List<Integer> startLines(Collection<Issue> issues) {
        return issues.stream()
                .map(i -> {
                    var range = i.primaryLocation().textRange();
                    return range != null ? range.start().line() : -1;
                })
                .toList();
    }
}
