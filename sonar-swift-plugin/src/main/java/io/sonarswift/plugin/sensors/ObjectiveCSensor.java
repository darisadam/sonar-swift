package io.sonarswift.plugin.sensors;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main native-analysis sensor for Objective-C.
 *
 * <p><b>Status</b>: skeleton. Once {@link io.sonarswift.plugin.parser.ClangClient}
 * is wired up to feed the clang AST JSON into ObjC checks, this fills out.
 * Today it just discovers files so the language registration is exercised
 * and metrics like file count / size are populated.</p>
 */
public class ObjectiveCSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectiveCSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Sonar Swift — Objective-C native analysis")
                .onlyOnLanguage(SwiftPluginConstants.OBJC_LANGUAGE_KEY);
    }

    @Override
    public void execute(SensorContext context) {
        FileSystem fs = context.fileSystem();
        FilePredicates p = fs.predicates();
        int n = 0;
        for (InputFile ignored : fs.inputFiles(p.and(
                p.hasLanguage(SwiftPluginConstants.OBJC_LANGUAGE_KEY),
                p.hasType(InputFile.Type.MAIN)))) {
            n++;
            // TODO(@objc-team): once ClangClient lands, parse + dispatch to ObjC checks.
        }
        LOG.info("Sonar Swift ObjC sensor: discovered {} Objective-C file(s) (analysis stubbed)", n);
    }
}
