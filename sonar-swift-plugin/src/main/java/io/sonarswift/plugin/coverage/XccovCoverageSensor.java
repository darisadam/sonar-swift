package io.sonarswift.plugin.coverage;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.coverage.NewCoverage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Code-coverage sensor for Apple's native {@code xccov} format.
 *
 * <p>Two input modes:</p>
 * <ol>
 *   <li>Pre-extracted JSON: {@code sonar.swift.coverage.xccov.reportPaths=coverage.json}.
 *       Generate with {@code xcrun xccov view --report --json *.xcresult}.</li>
 *   <li>Raw .xcresult bundles: {@code sonar.swift.coverage.xcresultPaths=build/MyApp.xcresult}.
 *       The sensor invokes {@code xcrun xccov} itself. Requires {@code xcrun} on PATH.</li>
 * </ol>
 */
public class XccovCoverageSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(XccovCoverageSensor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("xccov coverage importer")
                .onlyOnLanguages(
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .onlyWhenConfiguration(c ->
                        c.hasKey(SwiftPluginConstants.PROP_XCCOV_REPORT_PATHS)
                                || c.hasKey(SwiftPluginConstants.PROP_XCRESULT_PATHS));
    }

    @Override
    public void execute(SensorContext context) {
        // 1) Pre-extracted JSON paths
        for (String path : context.config().getStringArray(SwiftPluginConstants.PROP_XCCOV_REPORT_PATHS)) {
            File f = resolveFile(context, path);
            if (f == null || !f.exists()) { LOG.warn("xccov JSON not found: {}", path); continue; }
            try {
                importJson(context, JSON.readTree(f));
            } catch (IOException e) {
                LOG.warn("Failed to read xccov JSON {}: {}", path, e.getMessage());
            }
        }

        // 2) .xcresult bundles — call xccov ourselves
        for (String path : context.config().getStringArray(SwiftPluginConstants.PROP_XCRESULT_PATHS)) {
            File f = resolveFile(context, path);
            if (f == null || !f.exists()) { LOG.warn(".xcresult not found: {}", path); continue; }
            JsonNode root = runXccov(f);
            if (root != null) importJson(context, root);
        }
    }

    private JsonNode runXccov(File xcresult) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "xcrun", "xccov", "view",
                    "--report", "--json",
                    xcresult.getAbsolutePath())
                    .redirectErrorStream(false);
            Process p = pb.start();
            JsonNode root = JSON.readTree(p.getInputStream());
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                LOG.warn("xccov timed out for {}", xcresult);
                return null;
            }
            return root;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("Failed to invoke xcrun xccov on {}: {}", xcresult, e.getMessage());
            return null;
        }
    }

    /**
     * xccov JSON shape (abbreviated):
     * <pre>
     * {
     *   "targets": [{
     *     "name": "MyApp.app",
     *     "files": [{
     *       "path": "/path/to/MyApp/Login.swift",
     *       "functions": [{
     *         "lineCoverage": [
     *           {"line": 12, "executionCount": 3},
     *           {"line": 13, "executionCount": 0}
     *         ]
     *       }]
     *     }]
     *   }]
     * }
     * </pre>
     */
    private void importJson(SensorContext context, JsonNode root) {
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();

        for (JsonNode target : root.path("targets")) {
            for (JsonNode fileNode : target.path("files")) {
                String filePath = fileNode.path("path").asText("");
                InputFile inputFile = fs.inputFile(fp.hasPath(filePath));
                if (inputFile == null) {
                    LOG.debug("xccov: file not in project: {}", filePath);
                    continue;
                }
                NewCoverage cov = context.newCoverage().onFile(inputFile);
                for (JsonNode fn : fileNode.path("functions")) {
                    for (JsonNode line : fn.path("lineCoverage")) {
                        int lineNo = line.path("line").asInt(-1);
                        int count = line.path("executionCount").asInt(0);
                        if (lineNo > 0 && lineNo <= inputFile.lines()) {
                            cov.lineHits(lineNo, count);
                        }
                    }
                }
                cov.save();
            }
        }
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }
}
