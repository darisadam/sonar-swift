package io.sonarswift.plugin.external;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.rules.RuleType;
import org.sonar.api.batch.rule.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GenericIssueSensorTest {

    @Test
    void imports_issues_from_well_formed_report(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot.resolve("Sources"));
        Path sourceFile = projectRoot.resolve("Sources/Foo.swift");
        Files.writeString(sourceFile, "func a() {}\nfunc b() {}\n");

        Path report = tmp.resolve("issues.json");
        Files.writeString(report, """
                {
                  "issues": [
                    {
                      "engineId": "tailor",
                      "ruleId": "redundant-parens",
                      "type": "CODE_SMELL",
                      "severity": "MINOR",
                      "effortMinutes": 5,
                      "primaryLocation": {
                        "message": "Redundant parens",
                        "filePath": "Sources/Foo.swift",
                        "textRange": {"startLine": 1, "endLine": 1}
                      }
                    },
                    {
                      "engineId": "tailor",
                      "ruleId": "magic-number",
                      "type": "BUG",
                      "severity": "CRITICAL",
                      "primaryLocation": {
                        "message": "Magic number",
                        "filePath": "Sources/Foo.swift",
                        "textRange": {"startLine": 2}
                      }
                    }
                  ]
                }
                """);

        SensorContextTester context = SensorContextTester.create(projectRoot);
        InputFile inputFile = TestInputFileBuilder.create("project", "Sources/Foo.swift")
                .setLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .setContents(Files.readString(sourceFile, StandardCharsets.UTF_8))
                .setCharset(StandardCharsets.UTF_8)
                .build();
        context.fileSystem().add((org.sonar.api.batch.fs.internal.DefaultInputFile) inputFile);
        context.setSettings(new org.sonar.api.config.internal.MapSettings()
                .setProperty(SwiftPluginConstants.PROP_GENERIC_ISSUE_REPORT_PATHS, report.toString()));

        new GenericIssueSensor().execute(context);

        assertThat(context.allExternalIssues()).hasSize(2);

        ExternalIssue first = context.allExternalIssues().stream()
                .filter(i -> "redundant-parens".equals(i.ruleId()))
                .findFirst().orElseThrow();
        assertThat(first.engineId()).isEqualTo("tailor");
        assertThat(first.type()).isEqualTo(RuleType.CODE_SMELL);
        assertThat(first.severity()).isEqualTo(Severity.MINOR);
        assertThat(first.remediationEffort()).isEqualTo(5L);

        ExternalIssue second = context.allExternalIssues().stream()
                .filter(i -> "magic-number".equals(i.ruleId()))
                .findFirst().orElseThrow();
        assertThat(second.type()).isEqualTo(RuleType.BUG);
        assertThat(second.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void skips_issues_pointing_at_files_outside_the_project(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);
        Path report = tmp.resolve("issues.json");
        Files.writeString(report, """
                {
                  "issues": [{
                    "engineId": "x",
                    "ruleId": "rule-1",
                    "type": "CODE_SMELL",
                    "severity": "MINOR",
                    "primaryLocation": {
                      "message": "m",
                      "filePath": "Nonexistent.swift",
                      "textRange": {"startLine": 1}
                    }
                  }]
                }
                """);

        SensorContextTester context = SensorContextTester.create(projectRoot);
        context.setSettings(new org.sonar.api.config.internal.MapSettings()
                .setProperty(SwiftPluginConstants.PROP_GENERIC_ISSUE_REPORT_PATHS, report.toString()));

        new GenericIssueSensor().execute(context);

        assertThat(context.allExternalIssues()).isEmpty();
    }

    @Test
    void handles_missing_report_gracefully(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);
        SensorContextTester context = SensorContextTester.create(projectRoot);
        context.setSettings(new org.sonar.api.config.internal.MapSettings()
                .setProperty(SwiftPluginConstants.PROP_GENERIC_ISSUE_REPORT_PATHS,
                        tmp.resolve("does-not-exist.json").toString()));

        // Should not throw
        new GenericIssueSensor().execute(context);
        assertThat(context.allExternalIssues()).isEmpty();
    }
}
