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
 * Reads <a href="https://github.com/peripheryapp/periphery">Periphery</a>'s
 * JSON output and raises external issues for each unused-symbol finding.
 */
public class PeripherySensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(PeripherySensor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Periphery dead-code importer")
                .onlyOnLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_PERIPHERY_REPORT_PATH));
    }

    @Override
    public void execute(SensorContext context) {
        String reportPath = context.config()
                .get(SwiftPluginConstants.PROP_PERIPHERY_REPORT_PATH).orElse(null);
        if (reportPath == null) return;

        File f = resolveFile(context, reportPath);
        if (f == null || !f.exists()) {
            LOG.warn("Periphery report not found: {}", reportPath);
            return;
        }
        try {
            int n = importReport(context, f);
            LOG.info("Periphery sensor: imported {} dead-code finding(s)", n);
        } catch (IOException e) {
            LOG.warn("Failed to read Periphery report {}: {}", reportPath, e.getMessage());
        }
    }

    private int importReport(SensorContext context, File reportFile) throws IOException {
        JsonNode root = JSON.readTree(reportFile);
        if (!root.isArray()) {
            LOG.warn("Periphery report is not a JSON array: {}", reportFile);
            return 0;
        }
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();
        int n = 0;
        for (JsonNode v : root) {
            // Periphery emits one object per finding with: location, kind, name, modifiers
            JsonNode loc = v.path("location");
            String filePath = loc.path("file").asText("");
            int line = Math.max(1, loc.path("line").asInt(1));
            String kind = v.path("kind").asText("");
            String name = v.path("name").asText("");
            String hints = v.path("hints").isArray() ? v.path("hints").toString() : v.path("hints").asText("");

            InputFile inputFile = fs.inputFile(fp.hasPath(filePath));
            if (inputFile == null) continue;

            String ruleId = mapKindToRuleId(kind);

            NewExternalIssue ext = context.newExternalIssue()
                    .engineId("periphery")
                    .ruleId(ruleId)
                    .severity(Severity.MINOR)
                    .type(RuleType.CODE_SMELL)
                    .remediationEffortMinutes(10L);

            NewIssueLocation loc2 = ext.newLocation()
                    .on(inputFile)
                    .at(inputFile.selectLine(line))
                    .message("Unused " + kind + " `" + name + "` " + hints);

            ext.at(loc2).save();
            n++;
        }
        return n;
    }

    private String mapKindToRuleId(String kind) {
        return switch (kind) {
            case "class" -> "S3100";
            case "struct", "enum" -> "S3101";
            case "protocol" -> "S3102";
            case "function", "method" -> "S3103";
            case "initializer" -> "S3104";
            case "var", "property" -> "S3105";
            case "parameter" -> "S3106";
            case "typealias" -> "S3108";
            default -> "S3199";
        };
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }
}
