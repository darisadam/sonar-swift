package io.sonarswift.plugin.coverage;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

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

/** Code-coverage sensor for Slather XML output. */
public class SlatherCoverageSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(SlatherCoverageSensor.class);
    private static final XmlMapper XML = new XmlMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Slather coverage importer")
                .onlyOnLanguages(
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_SLATHER_REPORT_PATHS));
    }

    @Override
    public void execute(SensorContext context) {
        for (String path : context.config().getStringArray(SwiftPluginConstants.PROP_SLATHER_REPORT_PATHS)) {
            File f = resolveFile(context, path);
            if (f == null || !f.exists()) { LOG.warn("Slather report not found: {}", path); continue; }
            try {
                importXml(context, f);
            } catch (IOException e) {
                LOG.warn("Failed to read Slather report {}: {}", path, e.getMessage());
            }
        }
    }

    /**
     * Slather XML shape (abbreviated):
     * <pre>
     * &lt;coverage&gt;
     *   &lt;packages&gt;
     *     &lt;package&gt;
     *       &lt;classes&gt;
     *         &lt;class filename="MyApp/Login.swift"&gt;
     *           &lt;lines&gt;
     *             &lt;line number="12" hits="3"/&gt;
     *           &lt;/lines&gt;
     *         &lt;/class&gt;
     *       &lt;/classes&gt;
     *     &lt;/package&gt;
     *   &lt;/packages&gt;
     * &lt;/coverage&gt;
     * </pre>
     */
    private void importXml(SensorContext context, File reportFile) throws IOException {
        JsonNode root = XML.readTree(reportFile);
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();

        JsonNode packages = root.path("packages");
        JsonNode packageNode = packages.path("package");
        for (JsonNode pkg : iter(packageNode)) {
            JsonNode classes = pkg.path("classes").path("class");
            for (JsonNode cls : iter(classes)) {
                String filename = cls.path("filename").asText("");
                InputFile inputFile = fs.inputFile(fp.hasPath(filename));
                if (inputFile == null) continue;

                NewCoverage cov = context.newCoverage().onFile(inputFile);
                JsonNode lines = cls.path("lines").path("line");
                for (JsonNode line : iter(lines)) {
                    int lineNo = line.path("number").asInt(-1);
                    int hits = line.path("hits").asInt(0);
                    if (lineNo > 0 && lineNo <= inputFile.lines()) {
                        cov.lineHits(lineNo, hits);
                    }
                }
                cov.save();
            }
        }
    }

    private Iterable<JsonNode> iter(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return java.util.Collections.emptyList();
        if (node.isArray()) return node;
        return java.util.Collections.singletonList(node);
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }
}
