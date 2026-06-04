package io.sonarswift.plugin.test;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.measures.CoreMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Populates SonarQube's <b>test execution</b> widget from an Xcode test
 * <code>.xcresult</code> bundle.
 *
 * <p>Reads {@code sonar.swift.testExecution.xcresultPaths}, invokes
 * {@code xcrun xcresulttool} to extract the test plan summary, and writes the
 * standard {@code TESTS}, {@code TEST_FAILURES}, {@code TEST_ERRORS},
 * {@code SKIPPED_TESTS} and {@code TEST_EXECUTION_TIME} project-level
 * measures.</p>
 *
 * <p>Why project-level and not per-file: Apple's test result bundle records
 * which class/method failed, but doesn't carry a source-file path for each
 * test. Mapping test → file requires walking the symbol graph, which we don't
 * yet do. The widget at project level is the same one Apple developers see in
 * Xcode and is enough for quality-gate purposes.</p>
 */
public class XcresultTestExecutionSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(XcresultTestExecutionSensor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Xcode test execution importer")
                .onlyOnLanguages(
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .onlyWhenConfiguration(c -> c.hasKey(SwiftPluginConstants.PROP_XCTEST_REPORT_PATHS));
    }

    @Override
    public void execute(SensorContext context) {
        Totals totals = new Totals();
        for (String path : context.config().getStringArray(SwiftPluginConstants.PROP_XCTEST_REPORT_PATHS)) {
            File bundle = resolveFile(context, path);
            if (bundle == null || !bundle.exists()) {
                LOG.warn(".xcresult not found: {}", path);
                continue;
            }
            try {
                JsonNode summary = runXcresultTool(bundle);
                if (summary != null) {
                    accumulate(summary, totals);
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Failed to read {}: {}", bundle, e.getMessage());
            }
        }
        if (totals.tests == 0) {
            LOG.info("No test results extracted from .xcresult bundles");
            return;
        }
        save(context, totals);
    }

    private JsonNode runXcresultTool(File xcresult) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "xcrun", "xcresulttool",
                "get", "test-results", "summary",
                "--path", xcresult.getAbsolutePath(),
                "--format", "json")
                .redirectErrorStream(false);
        Process p = pb.start();
        JsonNode root = JSON.readTree(p.getInputStream());
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return null;
        }
        return root;
    }

    private void accumulate(JsonNode summary, Totals totals) {
        // xcresulttool summary shape (abbreviated):
        // { "summary": { "totalTestCount": int, "passedTests": int,
        //                "failedTests": int, "skippedTests": int,
        //                "expectedFailures": int, "totalDuration": float, ... } }
        JsonNode s = summary.path("summary");
        totals.tests       += s.path("totalTestCount").asInt(0);
        totals.failures    += s.path("failedTests").asInt(0);
        totals.skipped     += s.path("skippedTests").asInt(0);
        totals.duration    += (long) (s.path("totalDuration").asDouble(0.0) * 1000.0);
    }

    private void save(SensorContext context, Totals totals) {
        context.<Integer>newMeasure().forMetric(CoreMetrics.TESTS)
                .on(context.project()).withValue(totals.tests).save();
        context.<Integer>newMeasure().forMetric(CoreMetrics.TEST_FAILURES)
                .on(context.project()).withValue(totals.failures).save();
        context.<Integer>newMeasure().forMetric(CoreMetrics.TEST_ERRORS)
                .on(context.project()).withValue(0).save();
        context.<Integer>newMeasure().forMetric(CoreMetrics.SKIPPED_TESTS)
                .on(context.project()).withValue(totals.skipped).save();
        context.<Long>newMeasure().forMetric(CoreMetrics.TEST_EXECUTION_TIME)
                .on(context.project()).withValue(totals.duration).save();
        LOG.info("Test execution: {} tests, {} failures, {} skipped, {} ms",
                totals.tests, totals.failures, totals.skipped, totals.duration);
    }

    private File resolveFile(SensorContext context, String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) return p.toFile();
        return context.fileSystem().resolvePath(configured);
    }

    private static final class Totals {
        int tests;
        int failures;
        int skipped;
        long duration;
    }
}
