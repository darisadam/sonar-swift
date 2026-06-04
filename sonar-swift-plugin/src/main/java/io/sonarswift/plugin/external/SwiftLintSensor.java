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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads SwiftLint JSON output and imports each violation as an
 * <b>external issue</b> under the {@code external_swiftlint} repository.
 *
 * <p>External issues integrate with SonarQube without us defining each
 * SwiftLint rule individually — useful given SwiftLint has 200+ rules
 * with frequent additions.</p>
 *
 * <p>Generate the input with:</p>
 * <pre>swiftlint lint --reporter json &gt; swiftlint.json</pre>
 *
 * <p>Then point the scanner at it via {@code sonar.swift.swiftlint.reportPaths}.</p>
 */
public class SwiftLintSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(SwiftLintSensor.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EXTERNAL_REPO = "external_swiftlint";

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("SwiftLint report importer")
                .onlyOnLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .onlyWhenConfiguration(c ->
                        c.hasKey(SwiftPluginConstants.PROP_SWIFTLINT_REPORT_PATHS)
                                || c.getBoolean(SwiftPluginConstants.PROP_SWIFTLINT_AUTORUN).orElse(false));
    }

    @Override
    public void execute(SensorContext context) {
        List<File> reports = collectReports(context);
        if (reports.isEmpty()) {
            LOG.debug("SwiftLint sensor: no reports to import (autorun produced none, no explicit paths)");
            return;
        }

        int total = 0;
        for (File reportFile : reports) {
            try {
                total += importReport(context, reportFile);
            } catch (IOException e) {
                LOG.warn("Failed to read SwiftLint report {}: {}", reportFile, e.getMessage());
            }
        }
        LOG.info("SwiftLint sensor: imported {} issue(s) from {} report(s)", total, reports.size());
    }

    private List<File> collectReports(SensorContext context) {
        List<File> out = new ArrayList<>();

        // 1) autorun — invokes SwiftLint and produces a report under workDir
        SwiftLintRunner runner = new SwiftLintRunner(context.config(), context.fileSystem());
        Optional<Path> autorunReport = runner.runIfRequested(context);
        autorunReport.ifPresent(p -> out.add(p.toFile()));

        // 2) explicit report paths
        String[] paths = context.config()
                .getStringArray(SwiftPluginConstants.PROP_SWIFTLINT_REPORT_PATHS);
        if (paths != null) {
            for (String p : paths) {
                File f = resolveFile(context, p);
                if (f == null || !f.exists()) {
                    LOG.warn("SwiftLint report not found: {}", p);
                    continue;
                }
                out.add(f);
            }
        }
        return out;
    }

    private int importReport(SensorContext context, File reportFile) throws IOException {
        JsonNode root = JSON.readTree(reportFile);
        if (!root.isArray()) {
            LOG.warn("SwiftLint report is not a JSON array: {}", reportFile);
            return 0;
        }
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();
        int n = 0;
        for (JsonNode v : root) {
            String filePath = v.path("file").asText("");
            int line = Math.max(1, v.path("line").asInt(1));
            String ruleId = v.path("rule_id").asText("unknown");
            String severityRaw = v.path("severity").asText("warning");
            String reason = v.path("reason").asText("");
            String type = v.path("type").asText(ruleId);

            InputFile inputFile = fs.inputFile(fp.hasPath(filePath));
            if (inputFile == null) {
                LOG.debug("SwiftLint reported on unknown file {} — skipped", filePath);
                continue;
            }

            NewExternalIssue ext = context.newExternalIssue()
                    .engineId("swiftlint")
                    .ruleId(ruleId)
                    .severity(mapSeverity(severityRaw))
                    .type(RuleType.CODE_SMELL)
                    .remediationEffortMinutes(5L);

            NewIssueLocation loc = ext.newLocation()
                    .on(inputFile)
                    .at(inputFile.selectLine(line))
                    .message(type + ": " + reason);

            ext.at(loc).save();
            n++;
        }
        return n;
    }

    private Severity mapSeverity(String s) {
        return switch (s.toLowerCase()) {
            case "error" -> Severity.MAJOR;
            case "warning" -> Severity.MINOR;
            default -> Severity.INFO;
        };
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }
}
