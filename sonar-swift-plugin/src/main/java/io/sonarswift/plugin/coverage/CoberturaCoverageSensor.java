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

/**
 * Code-coverage sensor for Cobertura XML format. Supports both line and
 * branch coverage where present.
 */
public class CoberturaCoverageSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(CoberturaCoverageSensor.class);
    private static final XmlMapper XML = new XmlMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Cobertura coverage importer")
                .onlyOnLanguages(
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_COBERTURA_REPORT_PATHS));
    }

    @Override
    public void execute(SensorContext context) {
        for (String path : context.config().getStringArray(SwiftPluginConstants.PROP_COBERTURA_REPORT_PATHS)) {
            File f = resolveFile(context, path);
            if (f == null || !f.exists()) { LOG.warn("Cobertura report not found: {}", path); continue; }
            try {
                importXml(context, f);
            } catch (IOException e) {
                LOG.warn("Failed to read Cobertura report {}: {}", path, e.getMessage());
            }
        }
    }

    private void importXml(SensorContext context, File reportFile) throws IOException {
        JsonNode root = XML.readTree(reportFile);
        FileSystem fs = context.fileSystem();
        FilePredicates fp = fs.predicates();

        JsonNode packageNode = root.path("packages").path("package");
        for (JsonNode pkg : iter(packageNode)) {
            JsonNode classNode = pkg.path("classes").path("class");
            for (JsonNode cls : iter(classNode)) {
                String filename = cls.path("filename").asText("");
                InputFile inputFile = fs.inputFile(fp.hasPath(filename));
                if (inputFile == null) continue;

                NewCoverage cov = context.newCoverage().onFile(inputFile);
                JsonNode lines = cls.path("lines").path("line");
                for (JsonNode line : iter(lines)) {
                    int lineNo = line.path("number").asInt(-1);
                    int hits = line.path("hits").asInt(0);
                    if (lineNo <= 0 || lineNo > inputFile.lines()) continue;

                    cov.lineHits(lineNo, hits);

                    // Branch coverage (when present in this Cobertura variant)
                    boolean isBranch = line.path("branch").asBoolean(false);
                    if (isBranch) {
                        String cc = line.path("condition-coverage").asText("");
                        int[] cov2 = parseConditionCoverage(cc);
                        if (cov2 != null) {
                            cov.conditions(lineNo, cov2[1], cov2[0]);
                        }
                    }
                }
                cov.save();
            }
        }
    }

    /** Parses Cobertura's {@code condition-coverage="50% (1/2)"} → [1, 2]. */
    private int[] parseConditionCoverage(String s) {
        int open = s.indexOf('(');
        int close = s.indexOf(')');
        int slash = s.indexOf('/');
        if (open < 0 || close < 0 || slash < 0) return null;
        try {
            int covered = Integer.parseInt(s.substring(open + 1, slash).trim());
            int total = Integer.parseInt(s.substring(slash + 1, close).trim());
            return new int[]{ covered, total };
        } catch (NumberFormatException e) {
            return null;
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
