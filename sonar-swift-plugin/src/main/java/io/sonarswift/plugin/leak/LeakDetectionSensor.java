package io.sonarswift.plugin.leak;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs at the END of analysis (lowest sensor priority) and writes a JSON
 * report describing the scan's memory profile to {@code .sonar/leaks/latest.json}.
 *
 * <p>What's recorded:</p>
 * <ul>
 *   <li>JVM heap before / after GC (init / used / committed / max)</li>
 *   <li>Non-heap (metaspace, code cache)</li>
 *   <li>Thread count (live / peak / daemon)</li>
 *   <li>Class loading stats (loaded / unloaded)</li>
 *   <li>Native parser sidecar stats from the parser's own metrics
 *       (delivered via {@link ParserMemoryProbe#latest()})</li>
 *   <li>Wall-clock seconds for the sensor run</li>
 * </ul>
 *
 * <p>Activated by {@code sonar.swift.leak.enabled=true} (defaults to true in
 * dev, false on CI runners other than the dedicated leak job).</p>
 *
 * <p>The pre-commit hook {@code leak-budget-check.sh} compares the resulting
 * report against {@code .sonar/leaks/budget.json}: a regression beyond a
 * configurable slack fails the commit / the CI job.</p>
 */
public class LeakDetectionSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(LeakDetectionSensor.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("sonar-swift self-diagnostics (memory)")
                .global()
                .onlyWhenConfiguration(c -> c.getBoolean(SwiftPluginConstants.PROP_LEAK_DETECT_ENABLED).orElse(false));
    }

    @Override
    public void execute(SensorContext context) {
        long start = System.nanoTime();

        // Force a GC pass so used-heap reflects retained memory, not garbage.
        System.gc();
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("plugin", "sonar-swift");

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        report.put("heap", memMap(mem.getHeapMemoryUsage()));
        report.put("nonHeap", memMap(mem.getNonHeapMemoryUsage()));

        var threads = ManagementFactory.getThreadMXBean();
        report.put("threads", Map.of(
                "count", threads.getThreadCount(),
                "peak", threads.getPeakThreadCount(),
                "daemon", threads.getDaemonThreadCount()));

        var loading = ManagementFactory.getClassLoadingMXBean();
        report.put("classes", Map.of(
                "loaded", loading.getLoadedClassCount(),
                "totalLoaded", loading.getTotalLoadedClassCount(),
                "unloaded", loading.getUnloadedClassCount()));

        // GC summary
        var gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        Map<String, Object> gc = new LinkedHashMap<>();
        long totalCount = 0;
        long totalTime = 0;
        for (var bean : gcBeans) {
            gc.put(bean.getName(), Map.of(
                    "count", bean.getCollectionCount(),
                    "time", bean.getCollectionTime()));
            totalCount += bean.getCollectionCount();
            totalTime  += bean.getCollectionTime();
        }
        gc.put("totalCount", totalCount);
        gc.put("totalTimeMs", totalTime);
        report.put("gc", gc);

        // Parser sidecar metrics
        ParserMemoryProbe.Snapshot probe = ParserMemoryProbe.latest();
        if (probe != null) {
            report.put("parser", probe.toMap());
        }

        // Counts of files analyzed (best-effort — pull from project)
        report.put("scan", Map.of(
                "project", context.project().key(),
                "elapsedMs", (System.nanoTime() - start) / 1_000_000));

        Path target = leakReportPath(context);
        try {
            Files.createDirectories(target.getParent());
            JSON.writeValue(target.toFile(), report);
            LOG.info("Leak report: {}", target);

            // also drop a per-run timestamped copy for history
            Path hist = target.resolveSibling("leak-" + Instant.now().toEpochMilli() + ".json");
            Files.copy(target, hist);
        } catch (IOException e) {
            LOG.warn("Failed to write leak report: {}", e.getMessage());
        }

        // Compare against budget if present
        compareAgainstBudget(context, report, target);
    }

    private void compareAgainstBudget(SensorContext context, Map<String, Object> report, Path latest) {
        Path budget = budgetPath(context);
        if (!Files.exists(budget)) {
            LOG.info("No leak budget set yet — current report becomes the baseline next time it's accepted.");
            return;
        }
        try {
            Map<?, ?> baseline = JSON.readValue(budget.toFile(), Map.class);
            long currentUsed = ((Number) ((Map<?, ?>) report.get("heap")).get("used")).longValue();
            long budgetUsed = ((Number) ((Map<?, ?>) baseline.get("heap")).get("used")).longValue();
            double slack = 0.05; // 5%
            long allowed = (long) (budgetUsed * (1.0 + slack));
            if (currentUsed > allowed) {
                LOG.warn("Memory regression: used={} bytes, budget={} bytes (+{}%), slack={}%",
                        currentUsed, budgetUsed,
                        100.0 * (currentUsed - budgetUsed) / Math.max(1, budgetUsed), slack * 100);
            } else {
                LOG.info("Memory within budget: used={} (budget={}, allowed={})",
                        currentUsed, budgetUsed, allowed);
            }
        } catch (IOException e) {
            LOG.warn("Failed to compare against leak budget: {}", e.getMessage());
        }
    }

    private Path leakReportPath(SensorContext context) {
        return context.fileSystem().workDir().toPath().resolve("../leaks/latest.json").normalize();
    }

    private Path budgetPath(SensorContext context) {
        String explicit = context.config().get(SwiftPluginConstants.PROP_LEAK_BUDGET_PATH).orElse(null);
        if (explicit != null) {
            return context.fileSystem().resolvePath(explicit).toPath();
        }
        return context.fileSystem().baseDir().toPath().resolve(".sonar/leaks/budget.json");
    }

    private Map<String, Object> memMap(MemoryUsage u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("init", u.getInit());
        m.put("used", u.getUsed());
        m.put("committed", u.getCommitted());
        m.put("max", u.getMax());
        return m;
    }
}
