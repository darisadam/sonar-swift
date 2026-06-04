package io.sonarswift.plugin.external;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

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
 * Reads OCLint PMD-format XML reports and imports issues as external issues
 * under {@code external_oclint}.
 *
 * <p>Generate with:</p>
 * <pre>oclint-json-compilation-database -- -report-type pmd -o oclint.xml</pre>
 */
public class OCLintSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(OCLintSensor.class);
    private static final XmlMapper XML = new XmlMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("OCLint report importer")
                .onlyOnLanguage(SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_OCLINT_REPORT_PATHS));
    }

    @Override
    public void execute(SensorContext context) {
        String[] paths = context.config()
                .getStringArray(SwiftPluginConstants.PROP_OCLINT_REPORT_PATHS);
        if (paths == null || paths.length == 0) return;

        int total = 0;
        for (String p : paths) {
            File f = resolveFile(context, p);
            if (f == null || !f.exists()) {
                LOG.warn("OCLint report not found: {}", p);
                continue;
            }
            try {
                total += importReport(context, f);
            } catch (IOException e) {
                LOG.warn("Failed to read OCLint report {}: {}", p, e.getMessage());
            }
        }
        LOG.info("OCLint sensor: imported {} issue(s) from {} report(s)", total, paths.length);
    }

    private int importReport(SensorContext context, File reportFile) throws IOException {
        JsonNode root = XML.readTree(reportFile);
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();
        int n = 0;
        JsonNode files = root.path("file");
        for (JsonNode fileNode : files.isArray() ? files : iter(files)) {
            String filePath = fileNode.path("name").asText("");
            InputFile inputFile = fs.inputFile(fp.hasPath(filePath));
            if (inputFile == null) {
                LOG.debug("OCLint reported on unknown file {} — skipped", filePath);
                continue;
            }
            JsonNode violations = fileNode.path("violation");
            for (JsonNode v : violations.isArray() ? violations : iter(violations)) {
                int line = Math.max(1, v.path("beginline").asInt(1));
                String rule = v.path("rule").asText("unknown");
                int priority = v.path("priority").asInt(3);
                String message = v.asText();   // PMD XML text content of <violation>

                NewExternalIssue ext = context.newExternalIssue()
                        .engineId("oclint")
                        .ruleId(rule)
                        .severity(priorityToSeverity(priority))
                        .type(RuleType.CODE_SMELL)
                        .remediationEffortMinutes(5L);

                NewIssueLocation loc = ext.newLocation()
                        .on(inputFile)
                        .at(inputFile.selectLine(line))
                        .message(rule + ": " + message.strip());

                ext.at(loc).save();
                n++;
            }
        }
        return n;
    }

    private Severity priorityToSeverity(int p) {
        return switch (p) {
            case 1 -> Severity.MAJOR;
            case 2 -> Severity.MINOR;
            default -> Severity.INFO;
        };
    }

    private Iterable<JsonNode> iter(JsonNode single) {
        return single.isMissingNode() || single.isNull()
                ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(single);
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }
}
