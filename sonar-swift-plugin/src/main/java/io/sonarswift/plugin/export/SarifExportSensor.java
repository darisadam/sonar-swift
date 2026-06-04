package io.sonarswift.plugin.export;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.ScannerSide;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Writes a SARIF 2.1.0 file at the end of analysis so non-Sonar consumers
 * (GitHub Code Scanning, GitLab CodeClimate via converter, IDE viewers) can
 * see sonar-swift's findings.
 *
 * <p>The sensor doesn't read issues back from Sonar's storage — that happens
 * on the server, not the scanner. Instead, when active, every native check
 * also publishes its issue into an in-process {@link IssueTap} buffer. At the
 * end of the scan we serialize the buffered findings to SARIF.</p>
 *
 * <p>Activate with {@code sonar.swift.export.sarifPath=build/sonar-swift.sarif}.</p>
 */
@ScannerSide
public class SarifExportSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(SarifExportSensor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("sonar-swift SARIF exporter")
                .global()
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_SARIF_EXPORT_PATH));
    }

    @Override
    public void execute(SensorContext context) {
        String out = context.config().get(SwiftPluginConstants.PROP_SARIF_EXPORT_PATH).orElse(null);
        if (out == null || out.isBlank()) return;

        Path target = Paths.get(out);
        if (!target.isAbsolute()) {
            target = context.fileSystem().baseDir().toPath().resolve(target);
        }

        try {
            Files.createDirectories(target.getParent());
            ObjectNode root = buildSarif(context);
            Files.writeString(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            LOG.info("Wrote SARIF report: {} ({} findings)", target, IssueTap.size());
        } catch (IOException e) {
            LOG.warn("Failed to write SARIF: {}", e.getMessage());
        }
    }

    private ObjectNode buildSarif(SensorContext context) {
        ObjectNode root = JSON.createObjectNode();
        root.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/main/sarif-2.1/schema/sarif-schema-2.1.0.json");
        root.put("version", "2.1.0");

        ArrayNode runs = root.putArray("runs");
        ObjectNode run = runs.addObject();

        ObjectNode tool = run.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", "sonar-swift");
        driver.put("informationUri", "https://github.com/sonar-swift/sonar-swift");
        driver.put("version", "0.2.0-SNAPSHOT");
        driver.put("organization", "sonar-swift");

        ArrayNode rules = driver.putArray("rules");
        Map<String, Integer> ruleIndex = new HashMap<>();
        FileSystem fs = context.fileSystem();

        ArrayNode results = run.putArray("results");
        for (IssueTap.Finding f : IssueTap.snapshot()) {
            int idx = ruleIndex.computeIfAbsent(f.ruleKey(), k -> {
                ObjectNode r = rules.addObject();
                r.put("id", k);
                r.put("name", k);
                ObjectNode config = r.putObject("defaultConfiguration");
                config.put("level", levelFromSeverity(f.severity()));
                ObjectNode shortDesc = r.putObject("shortDescription");
                shortDesc.put("text", k);
                return rules.size() - 1;
            });

            ObjectNode result = results.addObject();
            result.put("ruleId", f.ruleKey());
            result.put("ruleIndex", idx);
            result.put("level", levelFromSeverity(f.severity()));
            result.putObject("message").put("text", f.message());

            ArrayNode locs = result.putArray("locations");
            ObjectNode loc = locs.addObject();
            ObjectNode physical = loc.putObject("physicalLocation");
            ObjectNode artifact = physical.putObject("artifactLocation");
            artifact.put("uri", fs.baseDir().toPath().relativize(Paths.get(f.filePath())).toString());
            ObjectNode region = physical.putObject("region");
            region.put("startLine", f.startLine());
            region.put("endLine", f.endLine());
        }

        ObjectNode invocation = run.putArray("invocations").addObject();
        invocation.put("executionSuccessful", true);
        invocation.put("endTimeUtc", Instant.now().toString());

        return root;
    }

    private String levelFromSeverity(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "BLOCKER", "CRITICAL" -> "error";
            case "MAJOR" -> "warning";
            case "MINOR", "INFO" -> "note";
            default -> "warning";
        };
    }
}
