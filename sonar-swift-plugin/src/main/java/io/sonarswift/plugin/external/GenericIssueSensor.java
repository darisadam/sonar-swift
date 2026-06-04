package io.sonarswift.plugin.external;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewExternalIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rules.RuleType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Imports SonarQube's <a
 * href="https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/importing-external-issues/generic-issue-import-format/">
 * generic external issue format</a>. Lets teams import findings from any tool
 * that can write the format — Tailor, custom linters, security scanners,
 * project-specific checks.
 *
 * <p>Schema (abridged):</p>
 * <pre>
 * {
 *   "issues": [{
 *     "engineId": "my-tool",
 *     "ruleId": "rule-1",
 *     "type": "CODE_SMELL" | "BUG" | "VULNERABILITY",
 *     "severity": "BLOCKER" | "CRITICAL" | "MAJOR" | "MINOR" | "INFO",
 *     "primaryLocation": {
 *       "message": "…",
 *       "filePath": "Sources/Foo.swift",
 *       "textRange": {"startLine": 12, "endLine": 12, "startColumn": 4, "endColumn": 14}
 *     },
 *     "effortMinutes": 5,
 *     "secondaryLocations": [...]
 *   }]
 * }
 * </pre>
 *
 * <p>Activated by {@code sonar.swift.externalIssues.reportPaths=path1,path2}.</p>
 */
public class GenericIssueSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(GenericIssueSensor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Generic external issue importer (Swift / ObjC)")
                .onlyOnLanguages(
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_GENERIC_ISSUE_REPORT_PATHS));
    }

    @Override
    public void execute(SensorContext context) {
        int total = 0;
        for (String path : context.config().getStringArray(SwiftPluginConstants.PROP_GENERIC_ISSUE_REPORT_PATHS)) {
            File f = resolveFile(context, path);
            if (f == null || !f.exists()) {
                LOG.warn("Generic issue report not found: {}", path);
                continue;
            }
            try {
                total += importReport(context, f);
            } catch (IOException e) {
                LOG.warn("Failed to read generic issue report {}: {}", path, e.getMessage());
            }
        }
        LOG.info("Generic external issues sensor: imported {} issue(s)", total);
    }

    private int importReport(SensorContext context, File reportFile) throws IOException {
        JsonNode root = JSON.readTree(reportFile);
        JsonNode issues = root.path("issues");
        if (!issues.isArray()) {
            LOG.warn("Report missing 'issues' array: {}", reportFile);
            return 0;
        }
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();
        int n = 0;
        for (JsonNode i : issues) {
            String engineId = i.path("engineId").asText("unknown");
            String ruleId = i.path("ruleId").asText("rule");
            RuleType type = parseRuleType(i.path("type").asText("CODE_SMELL"));
            Severity severity = parseSeverity(i.path("severity").asText("MAJOR"));
            long effort = i.path("effortMinutes").asLong(0L);

            JsonNode loc = i.path("primaryLocation");
            String filePath = loc.path("filePath").asText("");
            String message = loc.path("message").asText("(no message)");
            JsonNode range = loc.path("textRange");
            int startLine = range.path("startLine").asInt(1);

            InputFile inputFile = fs.inputFile(fp.hasPath(filePath));
            if (inputFile == null) {
                LOG.debug("Generic issue refers to unknown file: {}", filePath);
                continue;
            }

            NewExternalIssue ext = context.newExternalIssue()
                    .engineId(engineId)
                    .ruleId(ruleId)
                    .severity(severity)
                    .type(type);
            if (effort > 0) {
                ext.remediationEffortMinutes(effort);
            }

            NewIssueLocation primary = ext.newLocation()
                    .on(inputFile)
                    .at(inputFile.selectLine(startLine))
                    .message(message);
            ext.at(primary).save();
            n++;
        }
        return n;
    }

    private RuleType parseRuleType(String s) {
        return switch (s.toUpperCase()) {
            case "BUG" -> RuleType.BUG;
            case "VULNERABILITY" -> RuleType.VULNERABILITY;
            case "SECURITY_HOTSPOT" -> RuleType.SECURITY_HOTSPOT;
            default -> RuleType.CODE_SMELL;
        };
    }

    private Severity parseSeverity(String s) {
        return switch (s.toUpperCase()) {
            case "BLOCKER" -> Severity.BLOCKER;
            case "CRITICAL" -> Severity.CRITICAL;
            case "MAJOR" -> Severity.MAJOR;
            case "MINOR" -> Severity.MINOR;
            case "INFO" -> Severity.INFO;
            default -> Severity.MAJOR;
        };
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }
}
